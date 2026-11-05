CREATE TABLE instruments (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ticker     text NOT NULL,
    name       text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL,
    UNIQUE (user_id, ticker)
);

INSERT INTO instruments (id, user_id, ticker, created_at)
SELECT gen_random_uuid(), user_id, ticker, now()
FROM (SELECT DISTINCT user_id, ticker FROM transactions WHERE ticker IS NOT NULL) AS used;
