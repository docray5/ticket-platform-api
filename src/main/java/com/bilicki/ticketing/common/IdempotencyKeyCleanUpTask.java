package com.bilicki.ticketing.common;

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
public class IdempotencyKeyCleanUpTask {
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanUpExpiredKeys() {
        Instant now = Instant.now(clock);
        log.info("Starting cleanup of expired idempotency keys at {}", now);

        int deletedCount = idempotencyKeyRepository.deleteAllByExpiresAtBefore(now);

        log.info("Deleted {} expired idempotency keys", deletedCount);
    }
}
