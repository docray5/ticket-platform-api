package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.booking.internal.Hold;
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

    @RabbitListener(queues = "${booking.rabbitmq.queue-name}")
    @Transactional
    public void handleHoldExpiry(HoldExpiryMessage event) {
        MDC.put("correlationId", event.correlationId());

        try {
            bookingService.transitionHoldStatusFromActiveTo(event.holdId(), Hold.HoldStatus.EXPIRED);
        } catch (NoSuchElementException e) {
            log.warn("Hold {} not found during expiry check", event.holdId());
        } catch (Exception e) {
            log.error("Failed to process expiry for hold {}", event.holdId(), e);
            throw e;
        } finally {
            MDC.remove("correlationId");
        }
    }
}
