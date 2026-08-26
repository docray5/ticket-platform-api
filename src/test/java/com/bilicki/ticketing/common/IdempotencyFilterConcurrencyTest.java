package com.bilicki.ticketing.common;

import com.bilicki.ticketing.booking.internal.Hold;
import com.bilicki.ticketing.booking.service.BookingService;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.user.internal.User;
import com.bilicki.ticketing.user.internal.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class IdempotencyFilterConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @MockitoBean
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        idempotencyKeyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void concurrentRequests_WithSameKey_ShouldReturn201And409() throws Exception {
        User user = userRepository.save(new User("email", "hash"));

        String sharedIdempotencyKey = UUID.randomUUID().toString();
        UUID showtimeId = UUID.randomUUID();
        String jsonPayload = """
                {
                    "showtimeSeatIds": ["%s"]
                }
                """.formatted(UUID.randomUUID());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        when(bookingService.createHold(any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(500);
            return new HoldResponse(
                    UUID.randomUUID(), showtimeId, Hold.HoldStatus.ACTIVE,
                    BigDecimal.TEN, Instant.now(), Instant.now().plusSeconds(300), new ArrayList<>()
            );
        });

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Integer> httpStatuses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    MvcResult result = mockMvc.perform(post("/api/v1/showtimes/" + showtimeId + "/holds")
                                    .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                                    .header("Idempotency-Key", sharedIdempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonPayload))
                            .andReturn();

                    httpStatuses.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        doneLatch.await();
        executor.shutdown();

        assertThat(httpStatuses).hasSize(2);

        assertThat(httpStatuses).containsExactlyInAnyOrder(201, 409);

        List<IdempotencyKey> savedKeys = idempotencyKeyRepository.findAll();
        assertThat(savedKeys).hasSize(1);
        assertThat(savedKeys.getFirst().getStatus()).isEqualTo(IdempotencyKey.IdempotencyStatus.COMPLETED);
    }
}
