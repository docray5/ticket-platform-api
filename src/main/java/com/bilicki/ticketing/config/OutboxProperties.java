package com.bilicki.ticketing.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @NotNull String pollIntervalMs,
        @NotNull Integer maxRetries,
        @NotNull Integer batchSize,
        @NotNull Integer maxBackoffSeconds,
        @NotNull Duration confirmTimeout
) { }
