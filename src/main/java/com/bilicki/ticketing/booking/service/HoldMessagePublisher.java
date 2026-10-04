package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.common.outbox.OutboxService;
import com.bilicki.ticketing.config.BookingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class HoldMessagePublisher {
    private final BookingProperties bookingProperties;
    private final OutboxService outboxService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void scheduleHoldExpiry(HoldExpiryMessage event) {
        // rabbitTemplate.convertAndSend("", bookingProperties.rabbitMq().expiry().delayQueueName(), event);
        outboxService.recordEvent("", bookingProperties.rabbitMq().expiry().delayQueueName(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void publishHoldConfirmation(HoldConfirmMessage event) {

        outboxService.recordEvent(bookingProperties.rabbitMq().confirm().exchangeName(), bookingProperties.rabbitMq().confirm().routingKey(), event);
        // rabbitTemplate.convertAndSend(bookingProperties.rabbitMq().confirm().exchangeName(), bookingProperties.rabbitMq().confirm().routingKey(), event);
    }
}
