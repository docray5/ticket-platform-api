package com.bilicki.ticketing.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {
    private Integer BATCH_SIZE = 30;
    private Integer MAX_BACKOFF_SECONDS = 60;

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    private Long pollInterval = 1000L;
    private Integer maxRetries = 3;
    private Duration confirmTimeout = Duration.ofSeconds(5);

    public void relay() {

    }

    /** @return true if there are more messages to be processed still */
    @Scheduled(fixedDelay = 1000L)
    @Transactional
    public boolean publishOutboxMessages() {
        List<OutboxMessage> messages = outboxMessageRepository.findBatchByStatus(OutboxMessage.OutboxStatus.PENDING.name(), BATCH_SIZE);

        Instant now = Instant.now(clock);
        Map<OutboxMessage, CorrelationData> inFlight = new LinkedHashMap<>();
        for (OutboxMessage message : messages) {
            CorrelationData correlation = new CorrelationData(message.getId().toString());
            try {
                rabbitTemplate.convertAndSend(message.getExchange(), message.getRoutingKey(), toAmqpMessage(message), correlation);
                inFlight.put(message, correlation);
            } catch (AmqpException e) {
                fail(message, e.getMessage(), now);
            }

        }

        long deadline = System.nanoTime() + confirmTimeout.toNanos();
        inFlight.forEach((message, correlation) -> awaitConfirm(message, correlation, deadline, now));

        return messages.size() == BATCH_SIZE;
    }

    private void awaitConfirm(OutboxMessage message, CorrelationData correlation, long deadlineNanos, Instant now) {
        try {
            long remaining = Math.max(1, deadlineNanos - System.nanoTime());
            CorrelationData.Confirm confirm = correlation.getFuture().get(remaining, TimeUnit.NANOSECONDS);

            if (!confirm.ack()) {
                fail(message, "Broker nack: " + confirm.reason(), now);
            } else if (correlation.getReturned() != null) {
                fail(message, "Unroutable: " + correlation.getReturned().getReplyText(), now);
            } else {
                message.markProcessed();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(message, "Interrupted while waiting for confirm", now);
        } catch (Exception e) {
            fail(message, "No confirm: " + e.getClass().getSimpleName() + " " + e.getMessage(), now);
        }
    }

    private void fail(OutboxMessage message, String error, Instant now) {
        long seconds = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(message.getRetryCount(), 6));
        message.recordFailure(error, maxRetries, now, Duration.ofSeconds(seconds));

        if (message.getStatus() == OutboxMessage.OutboxStatus.FAILED) {
            log.error("Outbox message {} permanently FAILED after {} attempts: {}", message.getId(), message.getRetryCount(), error);
        } else {
            log.warn("Outbox message {} publish failed (attempt {}), retrying: {}", message.getId(), message.getRetryCount(), error);
        }
    }

    private Message toAmqpMessage(OutboxMessage message) {
        return MessageBuilder
                .withBody(message.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(message.getId().toString())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
