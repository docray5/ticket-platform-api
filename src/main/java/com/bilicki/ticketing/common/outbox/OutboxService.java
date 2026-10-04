package com.bilicki.ticketing.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(String exchangeName, String routingKey, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            String payloadType = payload.getClass().getName();

            OutboxMessage outboxMessage = new OutboxMessage(exchangeName, routingKey, payloadType, jsonPayload);

            outboxMessageRepository.save(outboxMessage);

            // TODO should we already try to send a messege i.e trigger the relay?
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize outbox payload", e);
        }
    }
}
