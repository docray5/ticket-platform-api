package com.bilicki.ticketing.common.outbox;

import com.bilicki.ticketing.config.OutboxProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OutboxRelay.class, OutboxRelayConcurrencyTest.TestConfig.class})
class OutboxRelayConcurrencyTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public OutboxProperties outboxProperties() {
            return new OutboxProperties("PT0.2S", 3, 10, 60, Duration.ofSeconds(2), Duration.ofDays(7));
        }

        @Bean
        public Clock testClock() {
            return Clock.systemUTC();
        }
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        outboxMessageRepository.deleteAll();
    }

    @Test
    void twoRelaysRunningAtOnce_sendEveryMessageExactlyOnce() throws Exception {
        int totalMessages = 50;

        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < totalMessages; i++) {
                outboxMessageRepository.save(new OutboxMessage("test.outbox", "test.outbox.key", "some json"));
            }
        });

        Queue<String> sentMessageIds = new ConcurrentLinkedQueue<>();
        Set<String> sendingThreads = ConcurrentHashMap.newKeySet();

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            sentMessageIds.add(correlation.getId());
            sendingThreads.add(Thread.currentThread().getName());
            Thread.sleep(15);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    outboxRelay.relay();
                    return null;
                }));
            }
            startSignal.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        List<OutboxMessage> allRows = outboxMessageRepository.findAll();

        assertThat(sentMessageIds).hasSize(totalMessages).doesNotHaveDuplicates();
        assertThat(sentMessageIds).containsExactlyInAnyOrderElementsOf(
                allRows.stream().map(row -> row.getId().toString()).toList());
        assertThat(sendingThreads).as("both relays must have taken part, or the test proved nothing").hasSize(2);

        assertThat(allRows).hasSize(totalMessages);
        assertThat(allRows).allMatch(row -> row.getStatus() == OutboxMessage.OutboxStatus.PROCESSED);
        assertThat(allRows).allMatch(row -> row.getRetryCount() == 0);
    }
}