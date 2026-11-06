ALTER TABLE users ADD COLUMN base_currency text NOT NULL DEFAULT 'PLN';

-- The latest rate per currency: how many PLN one unit buys. Shared between users.
CREATE TABLE fx_rates (
    currency       text PRIMARY KEY,
    rate           numeric(19, 8) NOT NULL,
    effective_date date NOT NULL,
    fetched_at     timestamptz NOT NULL
);

CREATE TABLE instrument_tags (
    instrument_id uuid NOT NULL REFERENCES instruments (id) ON DELETE CASCADE,
    tag           text NOT NULL,
    PRIMARY KEY (instrument_id, tag)
);

CREATE TABLE position_tags (
    account_id    uuid NOT NULL,
    instrument_id uuid NOT NULL,
    tag           text NOT NULL,
    PRIMARY KEY (account_id, instrument_id, tag),
    FOREIGN KEY (account_id, instrument_id) REFERENCES position_plans (account_id, instrument_id) ON DELETE CASCADE
);
