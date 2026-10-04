package com.bilicki.ticketing.common.outbox;

import com.bilicki.ticketing.booking.service.HoldConfirmMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@Import({ObjectMapper.class, OutboxService.class})
public class OutboxServiceTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxMessageRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void recordEventSuccess() {
        UUID holdId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();

        HoldConfirmMessage event = new HoldConfirmMessage(holdId, userId, correlationId);

        outboxService.recordEvent("booking.exchange", "booking.confirmed", event);

        List<OutboxMessage> records = repository.findAll();
        assertThat(records).hasSize(1);

        OutboxMessage outboxRecord = records.getFirst();
        assertThat(outboxRecord.getExchange()).isEqualTo("booking.exchange");
        assertThat(outboxRecord.getRoutingKey()).isEqualTo("booking.confirmed");
        assertThat(outboxRecord.getPayloadType()).isEqualTo(HoldConfirmMessage.class.getName());
        assertThat(outboxRecord.getStatus()).isEqualTo(OutboxMessage.OutboxStatus.PENDING);

        HoldConfirmMessage deserialized = objectMapper.readValue(
                outboxRecord.getPayload(),
                HoldConfirmMessage.class
        );
        assertThat(deserialized).isEqualTo(event);
    }
}
