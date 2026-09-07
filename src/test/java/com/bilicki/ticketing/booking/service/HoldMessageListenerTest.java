package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HoldMessageListenerTest {
    @Mock
    private BookingService bookingService;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private HoldMessageListener listener;

    @Test
    void shouldTransitionHoldToExpired_WhenMessageIsProcessedSuccessfully() {
        UUID holdId = UUID.randomUUID();
        HoldExpiryMessage event = new HoldExpiryMessage(holdId, "corr-123");

        listener.handleHoldExpiry(event);

        verify(bookingService).expireHold(holdId);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldPutCorrelationIdInMdc_BeforeDelegatingToBookingService() {
        UUID holdId = UUID.randomUUID();
        HoldExpiryMessage event = new HoldExpiryMessage(holdId, "corr-456");

        doAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo("corr-456");
            return null;
        }).when(bookingService).expireHold(any());

        listener.handleHoldExpiry(event);

        verify(bookingService).expireHold(holdId);
    }

    @Test
    void shouldSwallowNoSuchElementException_WhenHoldNoLongerExists() {
        UUID holdId = UUID.randomUUID();
        HoldExpiryMessage event = new HoldExpiryMessage(holdId, "corr-789");

        doThrow(new NoSuchElementException()).when(bookingService)
                .expireHold(any());

        assertThatCode(() -> listener.handleHoldExpiry(event)).doesNotThrowAnyException();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldRethrowAndStillClearMdc_WhenUnexpectedExceptionOccurs() {
        UUID holdId = UUID.randomUUID();
        HoldExpiryMessage event = new HoldExpiryMessage(holdId, "corr-999");

        doThrow(new RuntimeException("db is down")).when(bookingService)
                .expireHold(any());

        assertThrows(RuntimeException.class, () -> listener.handleHoldExpiry(event));
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldClearMdc_AfterHandlingHoldConfirm() {
        UUID holdId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        HoldConfirmMessage event = new HoldConfirmMessage(holdId, userId, "corr-confirm");

        assertThatCode(() -> listener.handleHoldConfirm(event)).doesNotThrowAnyException();

        assertThat(MDC.get("correlationId")).isNull();
    }
}
