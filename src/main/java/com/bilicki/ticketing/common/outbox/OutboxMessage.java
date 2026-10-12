package com.bilicki.ticketing.common.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
@Getter
@NoArgsConstructor
public class OutboxMessage {
    private static final int MAX_ERROR_LENGTH = 1000;

    public enum OutboxStatus { PENDING, PROCESSED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String exchange;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    public OutboxMessage(String exchange, String routingKey, String payload) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payload = payload;
    }

    public void markProcessed(Instant now) {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = now;
        this.lastError = null;
    }

    public void recordFailure(String error, int maxRetries, Instant now, Duration backoff) {
        this.retryCount++;
        this.lastError = truncate(error);
        if (this.retryCount >= maxRetries) {
            this.status = OutboxStatus.FAILED;
            this.processedAt = now;
        } else {
            this.nextAttemptAt = now.plus(backoff);
        }
    }

    private static String truncate(String s) {
        if (s == null) return "unknown error";
        return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
    }
}
