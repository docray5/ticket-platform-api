package com.bilicki.ticketing.notification;

import java.util.UUID;

public interface NotificationFacade {
    void sendBookingConfirmation(UUID holdId, UUID userId);
}
