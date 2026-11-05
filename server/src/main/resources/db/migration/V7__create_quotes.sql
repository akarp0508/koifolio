ALTER TABLE instruments
    ADD COLUMN quote_symbol text,
    ADD COLUMN quote_currency text,
    ADD COLUMN quote_checked_at timestamptz;

-- Shared between users: a price is the same for everyone
CREATE TABLE quotes (
    symbol     text PRIMARY KEY,
    price      numeric(19, 6) NOT NULL,
    quoted_at  timestamptz NOT NULL,
    fetched_at timestamptz NOT NULL
);
