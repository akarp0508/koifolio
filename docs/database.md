# Database

Postgres 18, accessed through JPA / Hibernate. Flyway owns the schema, and Hibernate only validates it. Entities are separate from the `domain` records, with mappers between them. Money and quantities are `numeric`. Ids are UUIDs.

User data has a `user_id`. Market data (instruments, prices, FX, CPI, rates) is shared.

## User data

```
users               id, username, password_hash, base_currency, cost_fx_rule, created_at

accounts            id, user_id, name, description, currency, balance, balance_set_on, cost_method, tax_rate, created_at
                    planned: archived_at

transactions        id, user_id, account_id, instrument_id, type, date, quantity, price, fee, amount,
                    withheld_tax, cash_effect, affects_balance, created_at, created_by, validated_at, deleted_at

instruments         id, user_id, ticker, name, sector, risk, thesis, analysis, listed, quote_symbol, quote_isin, quote_currency, quote_checked_at, created_at
                    UNIQUE (user_id, ticker)

position_plans      account_id, instrument_id, user_id, plan, risk, target_price, stop_price
                    PK (account_id, instrument_id)

instrument_tags     instrument_id, tag
position_tags       account_id, instrument_id, tag

rules               id, user_id, account_id (null = all), type, limit_percent, max_risk, created_at

portfolio_snapshots user_id, account_id, date, value, cost, cash, currency
                    PK (user_id, account_id, date)

price_overrides     user_id, instrument_id, price, currency, set_at
                    PK (user_id, instrument_id)

audit_log           id, user_id, actor_type (user | agent), actor_name, entity_type, entity_id,
                    action, summary, before (jsonb), after (jsonb), created_at, reverted_at

bond_lots           id, user_id, account_id, series, quantity, purchase_date, rates, early_fee, affects_balance, created_at

pending_changes     id, user_id, agent_name, kind (company, rule, rule delete, account, bond), ticker, rule_id, changes (jsonb), summary, created_at

agent_tokens        id, user_id, name, token_hash, scopes, expires_on, last_used_at, created_at
                    UNIQUE (user_id, name)
```

- Each type uses a subset of the columns, enforced by a `CHECK`.
- Removing an instrument clears `listed`, since transactions still point at it.
- `cash_effect` is stored, so undoing a transaction subtracts exactly what was applied. Writes lock the account row.
- Deleting an account deletes its transactions.
- Accounts with transactions are archived, not deleted.
- Snapshots are per account; the "all accounts" chart sums them. Changing the base currency recomputes them.

## Shared market data

```
instruments         id, kind (security | bond), ticker, market, currency, isin, name,
                    bond (jsonb), owner_user_id (null = shared)
provider_symbols    instrument_id, provider, symbol        PK (instrument_id, provider)
quotes              symbol, price, quoted_at, fetched_at          (PK symbol)
daily_closes        symbol, date, close                           (PK symbol, date)
price_history       symbol, covered_from, checked_on              (PK symbol)
fx_rates            currency, rate, effective_date, fetched_at    (PK currency; latest only)
fx_history          currency, date, rate                          (PK currency, date)
cpi_values          month, value, source                   PK (month)
reference_rates     date, rate, source                     PK (date)
```

- `owner_user_id` lets a user define a private bond or instrument; presets have none.
- `provider_symbols` maps our instrument to e.g. bankier's `PKOBP`.

OAuth tables for MCP clients come from Spring Authorization Server's standard schema.
