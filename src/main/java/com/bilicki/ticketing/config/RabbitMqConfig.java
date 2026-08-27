package com.bilicki.ticketing.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    @Value("${rabbitmq.queue-name}")
    private String queueName;
    @Value("${rabbitmq.exchange-name}")
    private String directExchangeName;
    @Value("${rabbitmq.key-name}")
    private String routingKeyName;

    @Value("${rabbitmq.delay-queue-name}")
    private String delayQueueName;

    @Value("${booking.hold.ttl-minutes}")
    private Integer ttlMinutes;

    @Bean
    public Queue queue() {
        return new Queue(queueName, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(directExchangeName);
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).with(routingKeyName);
    }

    @Bean
    public Queue delayQueue() {
        return QueueBuilder
                .durable(delayQueueName)
                .deadLetterExchange(directExchangeName)
                .ttl(ttlMinutes * 60 * 1000)
                .deadLetterRoutingKey(routingKeyName)
                .build();
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
