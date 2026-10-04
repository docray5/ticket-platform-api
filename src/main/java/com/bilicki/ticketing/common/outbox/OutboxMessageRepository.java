package com.bilicki.ticketing.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    List<OutboxMessage> findAllByStatusOrderByCreatedAtAsc(OutboxMessage.OutboxStatus status); // TODO should probably use some locking strat
}
