CREATE TABLE accounts (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        text NOT NULL,
    description text NOT NULL DEFAULT '',
    currency    text NOT NULL,
    balance     numeric(19, 4) NOT NULL,
    created_at  timestamptz NOT NULL
);

CREATE INDEX accounts_user_id_idx ON accounts (user_id);
