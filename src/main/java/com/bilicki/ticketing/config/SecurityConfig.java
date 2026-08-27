package com.bilicki.ticketing.config;

import com.bilicki.ticketing.common.IdempotencyFilter;
import com.bilicki.ticketing.common.IdempotencyKeyRepository;
import com.bilicki.ticketing.common.ProblemDetailResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock clock;
    private final ProblemDetailResponseWriter problemDetailResponseWriter;

    @Value("${app.idempotency.expiration-hours}")
    private Long idempotencyExpiryHours;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(restAccessDeniedHandler)
                        .authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/movies",
                                "/api/v1/movies/{movieId}",
                                "/api/v1/movies/{movieId}/showtimes",
                                "/api/v1/showtimes/{showtimeId}/seats"
                        ).permitAll().anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new IdempotencyFilter(idempotencyKeyRepository, clock, problemDetailResponseWriter, idempotencyExpiryHours), AuthorizationFilter.class)
                .build();
    }
}
