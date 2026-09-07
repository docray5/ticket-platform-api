package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldMessageListener {
    private final BookingService bookingService;
    private final NotificationService notificationService;

    @RabbitListener(queues = "${booking.rabbit-mq.expiry.queue-name}")
    @Transactional
    public void handleHoldExpiry(HoldExpiryMessage event) {
        MDC.put("correlationId", event.correlationId());

        try {
            bookingService.expireHold(event.holdId());
        } catch (NoSuchElementException e) {
            log.warn("Hold {} not found during expiry check", event.holdId());
        } catch (Exception e) {
            log.error("Failed to process expiry for hold {}", event.holdId(), e);
            throw e;
        } finally {
            MDC.remove("correlationId");
        }
    }

    @RabbitListener(queues = "${booking.rabbit-mq.confirm.queue-name}")
    @Transactional
    public void handleHoldConfirm(HoldConfirmMessage event) {
        MDC.put("correlationId", event.correlationId());
        try {
            notificationService.sendBookingConfirmation(event.holdId(), event.userId());
        } finally {
            MDC.remove("correlationId");
        }
    }
}
