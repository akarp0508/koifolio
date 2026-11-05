ALTER TABLE accounts ADD COLUMN balance_set_on date NOT NULL DEFAULT CURRENT_DATE;

CREATE TABLE transactions (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id      uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    type            text NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'BUY', 'SELL', 'DIVIDEND', 'FEE')),
    date            date NOT NULL,
    ticker          text,
    quantity        numeric(23, 8),
    price           numeric(19, 6),
    fee             numeric(19, 4),
    amount          numeric(19, 4),
    withheld_tax    numeric(19, 4),
    cash_effect     numeric(19, 4) NOT NULL,
    affects_balance boolean NOT NULL,
    created_at      timestamptz NOT NULL,
    CHECK (CASE
        WHEN type IN ('BUY', 'SELL') THEN ticker IS NOT NULL AND quantity IS NOT NULL AND price IS NOT NULL AND fee IS NOT NULL
        WHEN type = 'DIVIDEND' THEN ticker IS NOT NULL AND amount IS NOT NULL AND withheld_tax IS NOT NULL
        ELSE amount IS NOT NULL
    END)
);

CREATE INDEX transactions_user_id_idx ON transactions (user_id, date DESC);
CREATE INDEX transactions_account_id_idx ON transactions (account_id);
