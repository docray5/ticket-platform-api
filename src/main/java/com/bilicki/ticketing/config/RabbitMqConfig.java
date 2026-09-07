package com.bilicki.ticketing.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {
    private final BookingProperties bookingProperties;

    @Bean
    public Queue holdExpiryQueue() {
        return new Queue(bookingProperties.rabbitMq().expiry().queueName(), true);
    }

    @Bean
    public DirectExchange holdExpiryExchange() {
        return new DirectExchange(bookingProperties.rabbitMq().expiry().exchangeName());
    }

    @Bean
    public Binding holdExpiryBinding() {
        return BindingBuilder.bind(holdExpiryQueue()).to(holdExpiryExchange()).with(bookingProperties.rabbitMq().expiry().routingKey());
    }

    @Bean
    public Queue holdExpiryDelayQueue() {
        return QueueBuilder
                .durable(bookingProperties.rabbitMq().expiry().delayQueueName())
                .deadLetterExchange(bookingProperties.rabbitMq().expiry().exchangeName())
                .ttl((int) bookingProperties.hold().ttl().toMillis())
                .deadLetterRoutingKey(bookingProperties.rabbitMq().expiry().routingKey())
                .build();
    }

    @Bean
    public Queue holdConfirmQueue() {
        return new Queue(bookingProperties.rabbitMq().confirm().queueName(), true);
    }

    @Bean
    public DirectExchange holdConfirmExchange() {
        return new DirectExchange(bookingProperties.rabbitMq().confirm().exchangeName());
    }

    @Bean
    public Binding holdConfirmBinding() {
        return BindingBuilder.bind(holdConfirmQueue()).to(holdConfirmExchange()).with(bookingProperties.rabbitMq().confirm().routingKey());
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter("com.bilicki.ticketing.booking.service");
    }
}
