-- limit_percent is a ceiling or a floor depending on the type. max_risk says what counts as low risk.
CREATE TABLE rules (
    id            uuid PRIMARY KEY,
    user_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id    uuid REFERENCES accounts (id) ON DELETE CASCADE,
    type          text NOT NULL CHECK (type IN ('MAX_POSITION_WEIGHT', 'MIN_LOW_RISK_WEIGHT')),
    limit_percent numeric(5, 2) NOT NULL CHECK (limit_percent > 0 AND limit_percent <= 100),
    max_risk      integer CHECK (max_risk BETWEEN 1 AND 5),
    created_at    timestamptz NOT NULL,
    CHECK ((type = 'MIN_LOW_RISK_WEIGHT') = (max_risk IS NOT NULL))
);

CREATE INDEX rules_user_id ON rules (user_id);
