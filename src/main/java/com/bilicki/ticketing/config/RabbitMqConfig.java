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
    public Queue queue() {
        return new Queue(bookingProperties.rabbitMq().queueName(), true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(bookingProperties.rabbitMq().exchangeName());
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).with(bookingProperties.rabbitMq().routingKey());
    }

    @Bean
    public Queue delayQueue() {
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
