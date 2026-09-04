package com.bilicki.ticketing.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
        @NotNull @Valid Hold hold,
        @NotNull @Valid RabbitMq rabbitMq
) {
    public record Hold(@NotNull Duration ttl) { }

    public record RabbitMq(
            @NotBlank String queueName,
            @NotBlank String delayQueueName,
            @NotBlank String exchangeName,
            @NotBlank String routingKey
    ) { }
}