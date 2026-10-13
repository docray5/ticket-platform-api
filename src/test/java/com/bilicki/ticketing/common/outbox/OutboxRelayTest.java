package com.bilicki.ticketing.common.outbox;

import com.bilicki.ticketing.config.OutboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.bilicki.ticketing.common.outbox.OutboxMessage.OutboxStatus.PENDING;
import static com.bilicki.ticketing.common.outbox.OutboxMessage.OutboxStatus.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxRelayTest {

    @Mock
    private OutboxMessageRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final Instant now = Instant.parse("2026-10-08T12:00:00Z");

    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private OutboxProperties properties;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties("PT0.2S", 3, 10, 60, Duration.ofMillis(200), Duration.ofDays(7));

        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());

        outboxRelay = new OutboxRelay(properties, repository, rabbitTemplate, clock, transactionTemplate);
    }

    private OutboxMessage createPendingMessage() {
        OutboxMessage message = new OutboxMessage("booking.exchange", "booking.confirm", "some payload 1");
        ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
        return message;
    }

    // ---------- broker outage ----------

    @Test
    void brokerDownOnFirstSend_leavesEverythingPending_withoutBurningRetries() {
        OutboxMessage m1 = pendingMessage();
        OutboxMessage m2 = pendingMessage();
        OutboxMessage m3 = pendingMessage();
        claimReturns(m1, m2, m3);
        Instant m1Due = m1.getNextAttemptAt();

        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.relay();

        for (OutboxMessage message : List.of(m1, m2, m3)) {
            assertThat(message.getStatus()).isEqualTo(PENDING);
            assertThat(message.getRetryCount()).isZero();
            assertThat(message.getLastError()).isNull();
        }
        assertThat(m1.getNextAttemptAt()).isEqualTo(m1Due);

        // halted after the first failure instead of hammering a dead broker...
        verify(rabbitTemplate, times(1)).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));
        // ...and the drain loop did not spin
        verify(repository, times(1)).findNextBatchByStatus(any(), any(), anyInt());
    }

    @Test
    void brokerDownMidBatch_stillSettlesMessagesAlreadySent() {
        OutboxMessage sentBeforeOutage = pendingMessage();
        OutboxMessage failedSend = pendingMessage();
        OutboxMessage neverAttempted = pendingMessage();
        claimReturns(sentBeforeOutage, failedSend, neverAttempted);

        doAnswer(ack())
                .doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        boolean moreToProcess = outboxRelay.publishOutboxMessages();

        assertThat(sentBeforeOutage.getStatus()).isEqualTo(PROCESSED);
        assertThat(failedSend.getStatus()).isEqualTo(PENDING);
        assertThat(failedSend.getRetryCount()).isZero();
        assertThat(neverAttempted.getStatus()).isEqualTo(PENDING);
        assertThat(neverAttempted.getRetryCount()).isZero();
        assertThat(moreToProcess).as("must not ask the drain loop to continue").isFalse();
        verify(rabbitTemplate, times(2)).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void ioExceptionDuringSend_isTreatedAsBrokerOutage_notAMessageFailure() {
        OutboxMessage message = pendingMessage();
        claimReturns(message);

        doThrow(new AmqpIOException(new IOException("Connection reset")))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(message.getStatus()).isEqualTo(PENDING);
        assertThat(message.getRetryCount()).isZero();
        assertThat(message.getLastError()).isNull();
    }

    // ---------- backoff ----------

    @Test
    void firstFailure_schedulesRetryExactlyOneSecondLater() {
        OutboxMessage message = pendingMessage();
        claimReturns(message);
        doAnswer(nack("rejected")).when(rabbitTemplate)
                .convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    void backoffIsCappedAtMaxBackoffSeconds() {
        OutboxProperties highRetryProperties = new OutboxProperties("PT0.2S", 100, 10, 60, Duration.ofMillis(200), Duration.ofDays(7));
        OutboxRelay customRelay = new OutboxRelay(highRetryProperties, repository, rabbitTemplate, clock, transactionTemplate);

        OutboxMessage message = createPendingMessage();
        ReflectionTestUtils.setField(message, "retryCount", 10);

        when(repository.findNextBatchByStatus(eq(OutboxMessage.OutboxStatus.PENDING.name()), eq(now), eq(highRetryProperties.batchSize())))
                .thenReturn(List.of(message));

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        customRelay.publishOutboxMessages();

        assertThat(message.getRetryCount()).isEqualTo(11);
        assertThat(message.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));
    }

    // ---------- batching ----------

    @Test
    void emptyBatch_isANoOp() {
        claimReturns();

        outboxRelay.relay();

        verifyNoInteractions(rabbitTemplate);
        verify(repository, times(1)).findNextBatchByStatus(any(), any(), anyInt());
    }

    @Test
    void fullBatch_triggersAnotherClaim_untilTheTableIsDrained() {
        List<OutboxMessage> fullBatch = IntStream.range(0, properties.batchSize())
                .mapToObj(i -> pendingMessage()).toList();
        when(repository.findNextBatchByStatus(PENDING.name(), now, properties.batchSize()))
                .thenReturn(fullBatch, List.of());
        doAnswer(ack()).when(rabbitTemplate)
                .convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.relay();

        verify(repository, times(2)).findNextBatchByStatus(any(), any(), anyInt());
        assertThat(fullBatch).allMatch(m -> m.getStatus() == PROCESSED);
    }

    @Test
    void mixedBatch_eachMessageGetsItsOwnOutcome() {
        OutboxMessage acked = pendingMessage();
        OutboxMessage nacked = pendingMessage();
        OutboxMessage timedOut = pendingMessage();
        claimReturns(acked, nacked, timedOut);

        doAnswer(ack())
                .doAnswer(nack("rejected"))
                .doAnswer(invocation -> null) // never completes the future -> confirm timeout
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(acked.getStatus()).isEqualTo(PROCESSED);

        assertThat(nacked.getStatus()).isEqualTo(PENDING);
        assertThat(nacked.getRetryCount()).isEqualTo(1);
        assertThat(nacked.getLastError()).contains("Broker nack: rejected");

        assertThat(timedOut.getStatus()).isEqualTo(PENDING);
        assertThat(timedOut.getRetryCount()).isEqualTo(1);
        assertThat(timedOut.getLastError()).contains("TimeoutException");
    }

    @Test
    void shouldMarkMessageAsProcessedWhenBrokerAcks() {
        OutboxMessage message = createPendingMessage();
        when(repository.findNextBatchByStatus(PENDING.name(), Instant.now(clock), properties.batchSize()))
                .thenReturn(List.of(message));

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(message.getExchange()), eq(message.getRoutingKey()), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(message.getStatus()).isEqualTo(PROCESSED);
        assertThat(message.getProcessedAt()).isEqualTo(now);
        assertThat(message.getLastError()).isNull();
    }

    @Test
    void shouldRecordFailureWhenBrokerNacks() {
        OutboxMessage message = createPendingMessage();
        when(repository.findNextBatchByStatus(PENDING.name(), Instant.now(clock), properties.batchSize()))
                .thenReturn(List.of(message));

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "Queue quota exceeded"));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(message.getStatus()).isEqualTo(PENDING);
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isAfter(now);
        assertThat(message.getLastError()).contains("Broker nack: Queue quota exceeded");
    }

    @Test
    void shouldRecordFailureWhenConfirmTimesOut() {
        OutboxMessage message = createPendingMessage();
        when(repository.findNextBatchByStatus(PENDING.name(), Instant.now(clock), properties.batchSize()))
                .thenReturn(List.of(message));

        doAnswer(invocation -> null).when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(message.getStatus()).isEqualTo(PENDING);
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isAfter(now);
        assertThat(message.getLastError()).contains("TimeoutException");
    }

    @Test
    void shouldRecordFailureWhenMessageIsReturned() {
        OutboxMessage message = createPendingMessage();
        when(repository.findNextBatchByStatus(PENDING.name(), Instant.now(clock), properties.batchSize()))
                .thenReturn(List.of(message));

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            ReturnedMessage returnedMessage = new ReturnedMessage(
                    new Message(new byte[0]), 312, "NO_ROUTE", message.getExchange(), message.getRoutingKey()
            );
            correlation.setReturned(returnedMessage);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(message.getStatus()).isEqualTo(PENDING);
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isAfter(now);
        assertThat(message.getLastError()).contains("Unroutable: NO_ROUTE");
    }

    @Test
    void shouldRecordFailureWhenSendThrowsAmqpException() {
        OutboxMessage message = createPendingMessage();
        when(repository.findNextBatchByStatus(PENDING.name(), Instant.now(clock), properties.batchSize()))
                .thenReturn(List.of(message));

        doThrow(new AmqpException("Broker connection refused"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(message.getStatus()).isEqualTo(PENDING);
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isAfter(now);
        assertThat(message.getLastError()).contains("Broker connection refused");
    }

    @Test
    void shouldTransitionToFailedWhenMaxRetriesExceeded() {
        OutboxMessage message = createPendingMessage();
        message.recordFailure("First failure", properties.maxRetries(), now.minusSeconds(10), Duration.ofSeconds(1));
        message.recordFailure("Second failure", properties.maxRetries(), now.minusSeconds(5), Duration.ofSeconds(2));
        assertThat(message.getRetryCount()).isEqualTo(2);

        when(repository.findNextBatchByStatus(PENDING.name(), Instant.now(clock), properties.batchSize()))
                .thenReturn(List.of(message));

        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "Persistent error"));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        outboxRelay.publishOutboxMessages();

        assertThat(message.getStatus()).isEqualTo(OutboxMessage.OutboxStatus.FAILED);
        assertThat(message.getRetryCount()).isEqualTo(3);
        assertThat(message.getLastError()).contains("Persistent error");
    }

    @Test
    void shouldHandleEmptyBatchAsNoOp() {
        when(repository.findNextBatchByStatus(PENDING.name(), now, properties.batchSize()))
                .thenReturn(Collections.emptyList());

        boolean more = outboxRelay.publishOutboxMessages();

        assertThat(more).isFalse();
        verifyNoInteractions(rabbitTemplate);
    }


    private OutboxMessage pendingMessage() {
        OutboxMessage message = new OutboxMessage("booking.exchange", "booking.key", "{\"some\":\"json\"}");
        ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
        return message;
    }

    private void claimReturns(OutboxMessage... messages) {
        when(repository.findNextBatchByStatus(PENDING.name(), now, properties.batchSize()))
                .thenReturn(List.of(messages));
    }

    private static Answer<Object> ack() {
        return invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        };
    }

    private static Answer<Object> nack(String reason) {
        return invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, reason));
            return null;
        };
    }
}
