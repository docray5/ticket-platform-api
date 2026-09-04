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
        return new Queue(bookingProperties.rabbitMq().queueName(), true);
    }

    @Bean
    public DirectExchange holdExpiryExchange() {
        return new DirectExchange(bookingProperties.rabbitMq().exchangeName());
    }

    @Bean
    public Binding holdExpiryBinding() {
        return BindingBuilder.bind(holdExpiryQueue()).to(holdExpiryExchange()).with(bookingProperties.rabbitMq().routingKey());
    }

    @Bean
    public Queue holdExpiryDelayQueue() {
        return QueueBuilder
                .durable(bookingProperties.rabbitMq().delayQueueName())
                .deadLetterExchange(bookingProperties.rabbitMq().exchangeName())
                .ttl((int) bookingProperties.hold().ttl().toMillis())
                .deadLetterRoutingKey(bookingProperties.rabbitMq().routingKey())
                .build();
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter("com.bilicki.ticketing.booking.service");
    }
}
