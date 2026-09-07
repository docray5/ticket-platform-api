package com.bilicki.ticketing.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {
    @Override
    public void sendBookingConfirmation(UUID holdId, UUID userId) {
        log.info("Send email confirmation for hold {} confirmed by user {}", holdId, userId);
    }
}
