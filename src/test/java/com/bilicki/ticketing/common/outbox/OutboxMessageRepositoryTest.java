package com.bilicki.ticketing.common.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class OutboxMessageRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final String exchangeName = "test.outbox";
    private final String routingKey = "test.outbox.key";

    @BeforeEach
    @AfterEach
    void cleanUp() {
        outboxMessageRepository.deleteAll();
    }

    @Test
    public void findNextBatchByStatusSuccess() throws InterruptedException {
        int batchSize = 5;

        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < 10; i++) {
                outboxMessageRepository.save(new OutboxMessage(exchangeName, routingKey, "some payload " + i));
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch thread1HasLockedRows = new CountDownLatch(1);
        CountDownLatch thread2HasFinished = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(2);

        List<UUID> thread1Ids = new CopyOnWriteArrayList<>();
        List<UUID> thread2Ids = new CopyOnWriteArrayList<>();

        executor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    List<OutboxMessage> batch1 = outboxMessageRepository.findNextBatchByStatus(OutboxMessage.OutboxStatus.PENDING.name(), Instant.now(), batchSize);
                    batch1.forEach(msg -> thread1Ids.add(msg.getId()));

                    thread1HasLockedRows.countDown();

                    try {
                        thread2HasFinished.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception ignored) {
            } finally {
                allDone.countDown();
            }
        });

        executor.submit(() -> {
            try {
                thread1HasLockedRows.await(5, TimeUnit.SECONDS);

                transactionTemplate.executeWithoutResult(status -> {
                    List<OutboxMessage> batch2 = outboxMessageRepository.findNextBatchByStatus(OutboxMessage.OutboxStatus.PENDING.name(), Instant.now(), batchSize);
                    batch2.forEach(msg -> thread2Ids.add(msg.getId()));
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                thread2HasFinished.countDown();
                allDone.countDown();
            }
        });

        boolean completedInTime = allDone.await(10, TimeUnit.SECONDS);
        assertThat(completedInTime).isTrue();

        assertThat(thread1Ids.size()).isEqualTo(batchSize);
        assertThat(thread2Ids.size()).isEqualTo(batchSize);

        assertThat(thread1Ids).doesNotContainAnyElementsOf(thread2Ids);
    }
}
