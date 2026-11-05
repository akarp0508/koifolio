# REST API

Base path `/api/v1`. JSON. Clients authenticate with a JWT: `Authorization: Bearer <token>`. The OpenAPI spec is generated from the controllers by `springdoc-openapi`, and `web` will generate its types from it. For anything implemented, the generated spec is the reference and this file is the plan. With `KOIFOLIO_SWAGGER=true`, Swagger UI is at `/swagger-ui.html` and the spec at `/v3/api-docs`.

## Conventions

- **Decimals are strings,** so JavaScript never turns them into floats: `{"amount": "120.00", "currency": "PLN"}`.
- **Dates** are ISO: `2026-03-12`.
- **Errors** are `application/problem+json` (RFC 9457) with a stable `code`:
  ```json
  {"status": 422, "code": "oversold", "detail": "Selling 15 PAS but only 10 held on 2026-03-12"}
  ```
  Invalid fields return `400 validation_failed` with the problem in `detail` and an `errors` list:
  ```json
  {"status": 400, "code": "validation_failed", "detail": "password must be at least 8 characters",
   "errors": [{"field": "password", "message": "must be at least 8 characters"}]}
  ```
  A body that can't be read returns `400 invalid_body`.
- **Concurrency:** editable resources carry a `version`. A stale `PUT`/`PATCH` returns `409`.
- **Pagination:** list endpoints that can grow take `cursor` and `limit`, and return `{"items": [...], "nextCursor": "..."}`.

## Auth

Implemented.

| Request | Purpose |
|---|---|
| `POST /auth/register` | `{"username", "password"}` → `201 {"id", "username"}`, or `409 username_taken` |
| `POST /auth/login` | `{"username", "password"}` → `{"accessToken", "expiresIn"}`, or `401 invalid_credentials` |
| `GET /auth/me` | `{"id", "username"}`; needs a token |

- Passwords are 8 characters to 72 bytes (a BCrypt limit). Usernames ignore case and surrounding spaces.
- There's no logout endpoint: the client discards the token. Tokens can't be revoked and last 12 hours, with no refresh tokens yet.

## Settings

| Request | Purpose |
|---|---|
| `GET /settings` | `{"baseCurrency": "PLN", "costFxRule": "previous_publication"}` |
| `PATCH /settings` | Change settings; a base currency change recomputes snapshots |

## Accounts

Implemented. Each account belongs to one user; another user's account returns `404`.

| Request | Purpose |
|---|---|
| `GET /accounts` | List yours, oldest first |
| `POST /accounts` | Create |
| `GET /accounts/{id}` | |
| `PATCH /accounts/{id}` | Change `name`, `description` or `balance`; send only what changes |
| `DELETE /accounts/{id}` | Delete, along with its transactions |

```json
{
  "id": "…",
  "name": "Brokerage",
  "description": "Main account",
  "currency": "PLN",
  "balance": "5000.00",
  "balanceSetOn": "2026-11-05"
}
```

- `balance` defaults to 0 and may be negative. It's returned as a string, with the currency's decimal places. More decimals than the currency has gives `400 invalid_amount`.
- `currency` is a real ISO code in capitals, otherwise `400`. It can't be changed after creation.
- `balanceSetOn` is the last day the balance was changed by hand. Transactions don't move it.
- Optimistic locking (`version`) and archiving come later.

## Transactions

Implemented. Each belongs to one user and one account. Amounts are in the account's currency.

| Request | Purpose |
|---|---|
| `GET /transactions` | Newest first; filter with `?account=` |
| `POST /transactions` | Add; needs `accountId` |
| `GET /transactions/{id}` | |
| `PUT /transactions/{id}` | Replace, except the account |
| `DELETE /transactions/{id}` | Delete and undo its balance effect |

```json
{
  "accountId": "…",
  "type": "buy",
  "date": "2026-03-12",
  "ticker": "PAS",
  "quantity": "10",
  "price": "120.00",
  "fee": "0",
  "affectsBalance": true
}
```

| Type | Fields | Cash effect |
|---|---|---|
| `deposit` | `amount` | +amount |
| `withdrawal`, `fee` | `amount` | −amount |
| `buy` | `ticker`, `quantity`, `price`, `fee` (optional) | −(quantity × price + fee) |
| `sell` | `ticker`, `quantity`, `price`, `fee` (optional) | quantity × price − fee |
| `dividend` | `ticker`, `amount` (gross), `withheldTax` (optional) | amount − withheld tax |

- Every type also needs `date` and `affectsBalance`. Responses add `id`, `currency`, `cashEffect` and `oversold`, and leave out fields the type doesn't use.
- Cash effects are rounded to the currency's decimals.
- `oversold` marks a sale of more than was held at that point, per account and ticker. It's a warning, and it clears once the history adds up.
- A change that takes the balance below zero returns `409 negative_balance`, with the resulting balance in `detail`, and saves nothing. Sending `allowNegativeBalance: true` goes ahead. Changes that don't lower the balance, and transactions with `affectsBalance` off, never ask.
- A transaction or balance past 15 digits returns `400` (`amount_out_of_range`, `balance_out_of_range`).
- Pagination, revisions and `version` come later.

## Instruments

Implemented. Each user has their own private list of tickers.

| Request | Purpose |
|---|---|
| `GET /instruments` | List yours, by ticker |
| `POST /instruments` | `{"ticker", "name"}`; `name` is optional, a repeat gives `409 instrument_exists` |
| `PATCH /instruments/{id}` | `{"name"}`; the ticker can't change |
| `DELETE /instruments/{id}` | Remove from the list; transactions keep their ticker |

A transaction using a new ticker adds it to the list.

Planned: `GET /instruments/search`, `PUT /instruments/{id}/profile`, `GET /bond-presets`.

## Portfolio

All take `account` (omit for all accounts) and `asOf` (default today). Calculations happen only on the server.

| Request | Purpose |
|---|---|
| `GET /portfolio/summary` | Total value, P/L, cash, rule status, warnings |
| `GET /portfolio/positions` | Positions |
| `GET /portfolio/bonds` | Bond holdings |
| `GET /portfolio/allocation?by=company\|sector` | Weights |
| `GET /portfolio/history?from=&to=` | Value over time, from snapshots |
| `GET /portfolio/realized?year=` | Realized gains and estimated tax |
| `GET /portfolio/income?year=` | Dividends and interest |

```json
{
  "value": {"amount": "50000.00", "currency": "PLN"},
  "pnl": {"amount": "4200.00", "currency": "PLN"},
  "cash": {"amount": "1000.00", "currency": "PLN"},
  "rules": {"passed": 2, "total": 2},
  "unvalidatedTransactions": 1,
  "warnings": [{"code": "missing_price", "instrument": "…"}]
}
```

## Rules

| Request | Purpose |
|---|---|
| `GET /rules` | List |
| `POST /rules` | Create |
| `PUT /rules/{id}` | Edit |
| `DELETE /rules/{id}` | Delete |
| `GET /rules/check` | Results: passed, actual, limit |

## Prices

| Request | Purpose |
|---|---|
| `GET /prices/status` | Last fetch per provider, stale instruments |
| `POST /prices/refresh` | Fetch now |
| `PUT /prices/{instrumentId}/override` | Set a manual price |
| `DELETE /prices/{instrumentId}/override` | Clear it |

## Audit

| Request | Purpose |
|---|---|
| `GET /audit` | Filters: `actor`, `entity`, `from`, `to` |

## v1.1

| Request | Purpose |
|---|---|
| `GET /export` | Zip in the [export format](export-format.md) |
| `POST /import` | Upload, `?mode=replace\|merge` |
| `POST /portfolio/backfill` | Fill history gaps |
