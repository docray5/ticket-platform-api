package com.bilicki.ticketing.common.outbox;

import com.bilicki.ticketing.config.OutboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupTaskTest {

    @Mock
    private OutboxMessageRepository repository;

    @Mock
    private OutboxProperties outboxProperties;

    private final Instant now = Instant.parse("2026-10-12T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private OutboxCleanupTask cleanupTask;

    @BeforeEach
    void setUp() {
        cleanupTask = new OutboxCleanupTask(repository, outboxProperties, clock);
    }

    @Test
    void shouldDeleteProcessedMessagesOlderThanRetentionCutoff() {
        Duration retention = Duration.ofDays(7);
        when(outboxProperties.retention()).thenReturn(retention);

        cleanupTask.deleteOldProcessedMessages();

        Instant expectedCutoff = now.minus(retention);
        verify(repository).deleteByStatusAndProcessedAtBefore(OutboxMessage.OutboxStatus.PROCESSED, expectedCutoff);
    }
}