package com.bilicki.ticketing.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    @NativeQuery("""
        SELECT * FROM outbox_messages
        WHERE status = :status AND next_attempt_at <= :now
        ORDER BY created_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
    """)
    List<OutboxMessage> findNextBatchByStatus(@Param("status") String status, @Param("now") Instant now, int limit);


}
