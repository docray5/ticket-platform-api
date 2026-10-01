package com.bilicki.ticketing.common.outbox;

import jakarta.persistence.*;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
public class Outbox {
    public enum OutboxStatus { PENDING, PROCESSED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String exchange;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(name = "payload_type", nullable = false)
    private String payloadType;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Setter
    private int retryCount = 0;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;
}
