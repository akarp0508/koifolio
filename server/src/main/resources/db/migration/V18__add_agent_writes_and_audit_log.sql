-- created_by names the agent, and is empty for the user. A transaction with no validated_at waits for review.
ALTER TABLE transactions
    ADD COLUMN created_by   text,
    ADD COLUMN validated_at timestamptz,
    ADD COLUMN deleted_at   timestamptz;

UPDATE transactions SET validated_at = created_at;

-- Existing tokens stay read-only.
ALTER TABLE agent_tokens ADD COLUMN scopes text NOT NULL DEFAULT '';

CREATE TABLE audit_log (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_type  text NOT NULL CHECK (actor_type IN ('USER', 'AGENT')),
    actor_name  text,
    entity_type text NOT NULL,
    entity_id   uuid NOT NULL,
    action      text NOT NULL,
    summary     text NOT NULL,
    before      jsonb,
    after       jsonb,
    created_at  timestamptz NOT NULL
);

CREATE INDEX audit_log_user_id ON audit_log (user_id, created_at DESC);
