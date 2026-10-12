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
import java.time.Instant;
import java.util.List;
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
public class OutboxRelayConcurrencyTest {
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
            return new OutboxProperties("PT200MS", 3, 10, 60, Duration.ofSeconds(2));
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

    private final String exchangeName = "test.outbox";
    private final String routingKey = "test.outbox.key";

    @Test
    public void relayConcurrencyTest() throws InterruptedException {
        int totalMessages = 50;

        transactionTemplate.executeWithoutResult(s -> {
            for (int i = 0; i < totalMessages; i++) {
                outboxMessageRepository.save(new OutboxMessage(exchangeName, routingKey, "some json"));
            }
        });

        Set<String> sentMessageIds = ConcurrentHashMap.newKeySet();

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            sentMessageIds.add(correlation.getId());
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    outboxRelay.relay();
                } catch (Exception ignored) {
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown();

        boolean finishedInTime = doneSignal.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finishedInTime).as("Relay threads should complete all batches").isTrue();

        assertThat(sentMessageIds).hasSize(totalMessages);

        List<OutboxMessage> remainingPending = outboxMessageRepository.findNextBatchByStatus(
                OutboxMessage.OutboxStatus.PENDING.name(), Instant.now(), totalMessages
        );
        assertThat(remainingPending).isEmpty();

        List<OutboxMessage> allRows = outboxMessageRepository.findAll();
        assertThat(allRows).hasSize(totalMessages);
        assertThat(allRows).allMatch(msg -> msg.getStatus() == OutboxMessage.OutboxStatus.PROCESSED);
    }
}
