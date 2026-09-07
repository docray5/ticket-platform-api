package com.bilicki.ticketing.payment;

import com.bilicki.ticketing.payment.internal.Payment;
import com.bilicki.ticketing.payment.internal.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PaymentFacadeImpl.class)
class PaymentFacadeImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID holdId;

    @BeforeEach
    void setUpData() {
        holdId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID movieId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();

        String unique = UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, 'hash')", userId, "test-" + unique + "@test.com");
        jdbcTemplate.update("INSERT INTO venues (id, name, address) VALUES (?, ?, ?)", venueId, "Venue " + unique, "Address " + unique);
        jdbcTemplate.update("INSERT INTO halls (id, venue_id, name) VALUES (?, ?, 'Hall')", hallId, venueId);
        jdbcTemplate.update("INSERT INTO movies (id, title, description, duration_minutes) VALUES (?, ?, 'desc', 120)", movieId, "Movie " + unique);
        jdbcTemplate.update("INSERT INTO showtimes (id, movie_id, hall_id, start_time, end_time, base_price) VALUES (?, ?, ?, now(), now() + interval '2 hours', 10)", showtimeId, movieId, hallId);
        jdbcTemplate.update("INSERT INTO holds (id, showtime_id, user_id, status, total_price, expires_at) VALUES (?, ?, ?, 'ACTIVE', 25.00, now() + interval '5 minutes')", holdId, showtimeId, userId);
    }

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM holds");
        jdbcTemplate.update("DELETE FROM showtimes");
        jdbcTemplate.update("DELETE FROM movies");
        jdbcTemplate.update("DELETE FROM halls");
        jdbcTemplate.update("DELETE FROM venues");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldPersistDeclinedPayment_WhenPaymentDeclines() {
        ReflectionTestUtils.setField(paymentFacade, "declineRate", 1.0f);

        BigDecimal amount = new BigDecimal("25.00");

        assertThatThrownBy(() -> paymentFacade.pay(holdId, amount))
                .isInstanceOf(PaymentDeclinedException.class);

        Payment savedPayment = paymentRepository.findAll().stream()
                .filter(p -> p.getHoldId().equals(holdId) && p.getStatus() == Payment.PaymentStatus.DECLINED)
                .findFirst()
                .orElseThrow();

        assertThat(savedPayment.getAmount()).isEqualByComparingTo(amount);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldPersistSucceededPayment_WhenPaymentSucceeds() {
        ReflectionTestUtils.setField(paymentFacade, "declineRate", 0.0f);

        BigDecimal amount = new BigDecimal("25.00");

        paymentFacade.pay(holdId, amount);

        Payment savedPayment = paymentRepository.findAll().stream()
                .filter(p -> p.getHoldId().equals(holdId) && p.getStatus() == Payment.PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow();

        assertThat(savedPayment.getAmount()).isEqualByComparingTo(amount);
    }
}