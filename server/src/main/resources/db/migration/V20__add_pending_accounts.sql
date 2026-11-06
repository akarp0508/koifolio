ALTER TABLE pending_changes DROP CONSTRAINT pending_changes_kind_check;
ALTER TABLE pending_changes ADD CONSTRAINT pending_changes_kind_check
    CHECK (kind IN ('COMPANY', 'RULE', 'RULE_DELETE', 'ACCOUNT'));
