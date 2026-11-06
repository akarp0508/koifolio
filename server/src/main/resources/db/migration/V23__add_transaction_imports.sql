-- Groups the transactions an agent added in one call, so they can be reviewed together.
ALTER TABLE transactions ADD COLUMN import_id uuid;
CREATE INDEX transactions_import_id ON transactions (import_id) WHERE import_id IS NOT NULL;
