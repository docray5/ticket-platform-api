CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exchange VARCHAR(255) NOT NULL DEFAULT '',
    routing_key VARCHAR(255) NOT NULL,
    payload_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);
