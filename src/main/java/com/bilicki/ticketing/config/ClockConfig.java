package com.bilicki.ticketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

@Configuration
public class ClockConfig {
    @Bean
    @Primary
    public Clock clock() {
        return Clock.systemUTC();
    }
}
