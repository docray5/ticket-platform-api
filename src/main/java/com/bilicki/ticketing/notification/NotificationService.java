package com.bilicki.ticketing.notification;

import java.util.UUID;

public interface NotificationService {
    void sendBookingConfirmation(UUID holdId, UUID userId);
}
