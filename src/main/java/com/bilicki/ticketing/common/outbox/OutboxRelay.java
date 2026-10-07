package com.bilicki.ticketing.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {
    private Integer BATCH_SIZE = 30;

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    private Long pollInterval = 1000L;
    private Integer maxRetries = 3;

    public void relay() {

    }

    @Scheduled(fixedDelay = 1000L)
    @Transactional
    public void publishOutboxMessages() {
        List<OutboxMessage> messages = outboxMessageRepository.findBatchByStatus(OutboxMessage.OutboxStatus.PENDING.name(), BATCH_SIZE);



        messages.forEach(message -> {
            try {
                rabbitTemplate.convertAndSend(message.getExchange(), message.getRoutingKey(), toAmqpMessage(message));

                message.markProcessed();
            } catch (Exception e) {
                log.warn("Outbox message publish {} failed, attempt: {}, {}", message.getId(), message.getRetryCount(), e.toString());
                message.incrementRetry(maxRetries);
            }
        });
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
