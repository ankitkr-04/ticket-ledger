CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    request_hash VARCHAR(64),
    response_status INT,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_expires_at
ON idempotency_keys (expires_at);

CREATE INDEX idx_idempotency_user_id
ON idempotency_keys (user_id);
