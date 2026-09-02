package com.bilicki.ticketing.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class HoldMessagePublisher {
    private final RabbitTemplate rabbitTemplate;

    @Value("${booking.rabbitmq.delay-queue-name}")
    private String delayQueueName;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void scheduleHoldExpiry(HoldExpiryMessage event) {
        rabbitTemplate.convertAndSend("", delayQueueName, event);
    }
}
