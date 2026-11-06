-- A spin-off is a transaction on the new company, naming the parent it came from and the share of the parent's cost it takes.
ALTER TABLE transactions
    ADD COLUMN parent_instrument_id uuid REFERENCES instruments (id),
    ADD COLUMN cost_share           numeric(7, 4);

ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'BUY', 'SELL', 'DIVIDEND', 'FEE', 'SPLIT', 'INTEREST', 'SPINOFF'));

ALTER TABLE transactions DROP CONSTRAINT transactions_fields_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_fields_check CHECK (CASE
    WHEN type IN ('BUY', 'SELL') THEN instrument_id IS NOT NULL AND quantity IS NOT NULL AND price IS NOT NULL AND fee IS NOT NULL
    WHEN type = 'DIVIDEND' THEN instrument_id IS NOT NULL AND amount IS NOT NULL AND withheld_tax IS NOT NULL
    WHEN type = 'SPLIT' THEN instrument_id IS NOT NULL AND split_from IS NOT NULL AND split_to IS NOT NULL
    WHEN type = 'INTEREST' THEN amount IS NOT NULL AND withheld_tax IS NOT NULL
    WHEN type = 'SPINOFF' THEN instrument_id IS NOT NULL AND parent_instrument_id IS NOT NULL AND quantity IS NOT NULL AND cost_share IS NOT NULL
    ELSE amount IS NOT NULL
END);
