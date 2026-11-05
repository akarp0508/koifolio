CREATE TABLE users (
    id            uuid PRIMARY KEY,
    username      text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    created_at    timestamptz NOT NULL
);
