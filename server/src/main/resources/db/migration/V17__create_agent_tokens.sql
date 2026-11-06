-- Only a hash is kept, so a leaked database gives no way in. expires_on is the last day a token works.
CREATE TABLE agent_tokens (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         text NOT NULL,
    token_hash   text NOT NULL UNIQUE,
    expires_on   date,
    last_used_at timestamptz,
    created_at   timestamptz NOT NULL,
    UNIQUE (user_id, name)
);
