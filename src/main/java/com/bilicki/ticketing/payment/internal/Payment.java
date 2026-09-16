package com.bilicki.ticketing.payment.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Table(name = "payments")
@Getter
@Entity
public class Payment {
    public enum PaymentStatus { SUCCEEDED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hold_id", nullable = false)
    private UUID holdId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Setter
    private PaymentStatus status;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "provider_reference", nullable = false)
    private String providerReference;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public Payment(UUID holdId, PaymentStatus status, BigDecimal amount, String providerReference) {
        this.holdId = holdId;
        this.status = status;
        this.amount = amount;
        this.providerReference = providerReference;
    }
}