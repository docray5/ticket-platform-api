package com.bilicki.ticketing.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Table(name = "idempotency_keys")
@Getter
public class IdempotencyKey {
    public enum IdempotencyStatus { PENDING, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String key;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private IdempotencyStatus status = IdempotencyStatus.PENDING;

    @Column(name = "response_body")
    @Setter
    private String responseBody;

    @Column(name = "response_status")
    @Setter
    private Integer responseStatus;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public IdempotencyKey(String key, UUID userId, String endpoint, String requestHash, Instant expiresAt) {
        this.key = key;
        this.userId = userId;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.expiresAt = expiresAt;
    }
}
