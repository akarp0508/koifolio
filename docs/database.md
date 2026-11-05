# Database

Postgres 18, accessed through JPA / Hibernate. Flyway owns the schema, and Hibernate only validates it. Entities are separate from the `domain` records, with mappers between them. Money and quantities are `numeric`. Ids are UUIDs.

User data has a `user_id`. Market data (instruments, prices, FX, CPI, rates) is shared.

## User data

```
users               id, username, password_hash, base_currency, cost_fx_rule, created_at

accounts            id, user_id, name, description, currency, balance, balance_set_on, cost_method, created_at
                    planned: tax_rate, archived_at

instrument_profiles user_id, instrument_id, sector, risk, scenarios (jsonb), thesis, note, updated_at
                    PK (user_id, instrument_id)

transactions        id, user_id, account_id, instrument_id, type, date, quantity, price, fee, amount,
                    withheld_tax, cash_effect, affects_balance, created_at
                    planned: revisions, soft delete, sequence, created_by, validated_at

instruments         id, user_id, ticker, name, listed, created_at
                    UNIQUE (user_id, ticker)

rules               id, user_id, type, account_id (null = all), params (jsonb), created_at

portfolio_snapshots user_id, account_id, date, value, cost, cash, currency
                    PK (user_id, account_id, date)

price_overrides     user_id, instrument_id, price, currency, set_at
                    PK (user_id, instrument_id)

audit_log           id, user_id, actor_type (user | agent), actor_name, entity_type, entity_id,
                    action, before (jsonb), after (jsonb), created_at
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
prices              instrument_id, date, open, high, low, close, volume, currency, source, fetched_at
                    PK (instrument_id, date)
latest_quotes       instrument_id, price, currency, quoted_at, source, fetched_at
fx_rates            currency, date, rate, source           PK (currency, date)
cpi_values          month, value, source                   PK (month)
reference_rates     date, rate, source                     PK (date)
```

- `owner_user_id` lets a user define a private bond or instrument; presets have none.
- `provider_symbols` maps our instrument to e.g. bankier's `PKOBP`.

OAuth tables for MCP clients come from Spring Authorization Server's standard schema.
