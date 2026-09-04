package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.config.BookingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class HoldMessagePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final BookingProperties bookingProperties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void scheduleHoldExpiry(HoldExpiryMessage event) {
        rabbitTemplate.convertAndSend("", bookingProperties.rabbitMq().delayQueueName(), event);
    }
}
