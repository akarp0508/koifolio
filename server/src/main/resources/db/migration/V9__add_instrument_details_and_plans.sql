ALTER TABLE instruments
    ADD COLUMN sector   text NOT NULL DEFAULT '',
    ADD COLUMN risk     smallint CHECK (risk BETWEEN 1 AND 5),
    ADD COLUMN thesis   text NOT NULL DEFAULT '',
    ADD COLUMN analysis text NOT NULL DEFAULT '';

-- Positions are calculated, so a plan is tied to the account and instrument, and outlives selling out
CREATE TABLE position_plans (
    account_id    uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    instrument_id uuid NOT NULL REFERENCES instruments (id),
    user_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan          text NOT NULL DEFAULT '',
    risk          smallint CHECK (risk BETWEEN 1 AND 5),
    target_price  numeric(19, 6),
    stop_price    numeric(19, 6),
    PRIMARY KEY (account_id, instrument_id)
);

CREATE INDEX position_plans_user_id_idx ON position_plans (user_id);
