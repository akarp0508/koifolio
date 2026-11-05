-- Removing an instrument from the list must keep it for its transactions
ALTER TABLE instruments ADD COLUMN listed boolean NOT NULL DEFAULT true;

-- Tickers removed from a list before this migration still need a row
INSERT INTO instruments (id, user_id, ticker, created_at, listed)
SELECT gen_random_uuid(), user_id, ticker, now(), false
FROM (SELECT DISTINCT user_id, ticker FROM transactions WHERE ticker IS NOT NULL) AS used
ON CONFLICT (user_id, ticker) DO NOTHING;

ALTER TABLE transactions ADD COLUMN instrument_id uuid REFERENCES instruments (id);

UPDATE transactions AS t
SET instrument_id = i.id
FROM instruments AS i
WHERE i.user_id = t.user_id AND i.ticker = t.ticker;

ALTER TABLE transactions DROP CONSTRAINT transactions_check;
ALTER TABLE transactions DROP COLUMN ticker;
ALTER TABLE transactions ADD CONSTRAINT transactions_fields_check CHECK (CASE
    WHEN type IN ('BUY', 'SELL') THEN instrument_id IS NOT NULL AND quantity IS NOT NULL AND price IS NOT NULL AND fee IS NOT NULL
    WHEN type = 'DIVIDEND' THEN instrument_id IS NOT NULL AND amount IS NOT NULL AND withheld_tax IS NOT NULL
    ELSE amount IS NOT NULL
END);

CREATE INDEX transactions_instrument_id_idx ON transactions (instrument_id);
