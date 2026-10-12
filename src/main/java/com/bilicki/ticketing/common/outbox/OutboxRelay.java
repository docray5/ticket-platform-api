package com.bilicki.ticketing.common.outbox;

import com.bilicki.ticketing.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final OutboxProperties properties;

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:PT1S}")
    public void relay() {
        boolean moreLeftToProcess = true;
        while (moreLeftToProcess) {
            moreLeftToProcess = transactionTemplate.execute(status -> publishOutboxMessages());
            if (moreLeftToProcess) log.info("Processing batch...");
        }
    }

    /** @return true if there are more messages to be processed still */
    public boolean publishOutboxMessages() {
        Instant now = Instant.now(clock);

        List<OutboxMessage> messages = outboxMessageRepository.findNextBatchByStatus(OutboxMessage.OutboxStatus.PENDING.name(), now, properties.batchSize());

        if (messages.isEmpty()) {
            return false;
        }

        Map<OutboxMessage, CorrelationData> inFlight = new LinkedHashMap<>();
        boolean brokerUnavailable = false;

        for (OutboxMessage message : messages) {
            CorrelationData correlation = new CorrelationData(message.getId().toString());
            try {
                rabbitTemplate.convertAndSend(message.getExchange(), message.getRoutingKey(), toAmqpMessage(message), correlation);
                inFlight.put(message, correlation);
            } catch (AmqpConnectException | AmqpIOException e) {
                log.warn("Broker connection refused while publishing message {}. Halting batch.", message.getId());
                brokerUnavailable = true;
                break;
            } catch (AmqpException e) {
                fail(message, e.getMessage(), now);
            }
        }

        long deadline = System.nanoTime() + properties.confirmTimeout().toNanos();
        inFlight.forEach((message, correlation) -> awaitConfirm(message, correlation, deadline, now));

        return !brokerUnavailable && messages.size() == properties.batchSize();
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
                message.markProcessed(now);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(message, "Interrupted while waiting for confirm", now);
        } catch (Exception e) {
            fail(message, "No confirm: " + e.getClass().getSimpleName() + " " + e.getMessage(), now);
        }
    }

    private void fail(OutboxMessage message, String error, Instant now) {
        long seconds = Math.min(properties.maxBackoffSeconds(), 1L << Math.min(message.getRetryCount(), 6));
        message.recordFailure(error, properties.maxRetries(), now, Duration.ofSeconds(seconds));

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
