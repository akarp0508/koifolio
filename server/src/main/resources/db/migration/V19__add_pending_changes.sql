-- What an agent wants to change, which applies only once the user approves it.
CREATE TABLE pending_changes (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    agent_name text NOT NULL,
    kind       text NOT NULL CHECK (kind IN ('COMPANY', 'RULE', 'RULE_DELETE')),
    ticker     text,
    rule_id    uuid,
    changes    jsonb NOT NULL,
    summary    text NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX pending_changes_user_id ON pending_changes (user_id, created_at);

-- Set once a logged change has been undone, so it can't be undone twice.
ALTER TABLE audit_log ADD COLUMN reverted_at timestamptz;
