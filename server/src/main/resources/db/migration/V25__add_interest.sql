ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'BUY', 'SELL', 'DIVIDEND', 'FEE', 'SPLIT', 'INTEREST'));

ALTER TABLE transactions DROP CONSTRAINT transactions_fields_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_fields_check CHECK (CASE
    WHEN type IN ('BUY', 'SELL') THEN instrument_id IS NOT NULL AND quantity IS NOT NULL AND price IS NOT NULL AND fee IS NOT NULL
    WHEN type = 'DIVIDEND' THEN instrument_id IS NOT NULL AND amount IS NOT NULL AND withheld_tax IS NOT NULL
    WHEN type = 'SPLIT' THEN instrument_id IS NOT NULL AND split_from IS NOT NULL AND split_to IS NOT NULL
    WHEN type = 'INTEREST' THEN amount IS NOT NULL AND withheld_tax IS NOT NULL
    ELSE amount IS NOT NULL
END);
