-- Now also holds the transaction or account a proposal is about.
ALTER TABLE pending_changes RENAME COLUMN rule_id TO target_id;

ALTER TABLE pending_changes DROP CONSTRAINT pending_changes_kind_check;
ALTER TABLE pending_changes ADD CONSTRAINT pending_changes_kind_check
    CHECK (kind IN ('COMPANY', 'RULE', 'RULE_DELETE', 'ACCOUNT', 'ACCOUNT_UPDATE', 'BOND', 'TRANSACTION_UPDATE', 'TRANSACTION_DELETE'));
