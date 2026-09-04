package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.config.BookingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class HoldMessagePublisherTest {

    private static final String DELAY_QUEUE_NAME = "hold.expiry.delay.queue";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private HoldMessagePublisher holdMessagePublisher;

    @BeforeEach
    void setUp() {
        BookingProperties properties = new BookingProperties(
                new BookingProperties.Hold(Duration.ofMinutes(5)),
                new BookingProperties.RabbitMq("queue", DELAY_QUEUE_NAME, "exchange-test", "key-test")
        );
        holdMessagePublisher = new HoldMessagePublisher(rabbitTemplate, properties);
    }

    @Test
    void shouldPublishEventToDelayQueue_ViaDefaultExchange() {
        HoldExpiryMessage event = new HoldExpiryMessage(UUID.randomUUID(), "corr-123");

        holdMessagePublisher.scheduleHoldExpiry(event);

        verify(rabbitTemplate).convertAndSend("", DELAY_QUEUE_NAME, event);
        verifyNoMoreInteractions(rabbitTemplate);
    }
}