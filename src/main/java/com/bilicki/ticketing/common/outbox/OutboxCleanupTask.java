package com.bilicki.ticketing.common.outbox;

import com.bilicki.ticketing.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupTask {
    private final OutboxMessageRepository repository;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldProcessedMessages() {
        Instant cutoff = Instant.now(clock).minus(outboxProperties.retention());
        int deleted = repository.deleteByStatusAndProcessedAtBefore(OutboxMessage.OutboxStatus.PROCESSED, cutoff);
        log.info("Deleted {} processed outbox messages older than {}", deleted, cutoff);
    }
}
