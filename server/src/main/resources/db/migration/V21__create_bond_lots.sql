-- One purchase of Polish retail bonds, which accrue from the purchase date. rates holds one per year, comma separated.
CREATE TABLE bond_lots (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id      uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    series          text NOT NULL,
    quantity        integer NOT NULL CHECK (quantity > 0),
    purchase_date   date NOT NULL,
    rates           text NOT NULL,
    early_fee       numeric(6, 2) NOT NULL CHECK (early_fee >= 0),
    affects_balance boolean NOT NULL,
    created_at      timestamptz NOT NULL
);

CREATE INDEX bond_lots_user_id ON bond_lots (user_id);

ALTER TABLE pending_changes DROP CONSTRAINT pending_changes_kind_check;
ALTER TABLE pending_changes ADD CONSTRAINT pending_changes_kind_check
    CHECK (kind IN ('COMPANY', 'RULE', 'RULE_DELETE', 'ACCOUNT', 'BOND'));
