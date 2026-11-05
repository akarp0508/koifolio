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
| `DELETE /accounts/{id}` | Delete |

```json
{
  "id": "…",
  "name": "Brokerage",
  "description": "Main account",
  "currency": "PLN",
  "balance": "5000.00"
}
```

- `balance` defaults to 0 and may be negative. It's returned as a string, with the currency's decimal places. More decimals than the currency has gives `400 invalid_amount`.
- `currency` is a real ISO code in capitals, otherwise `400`. It can't be changed after creation.
- Optimistic locking (`version`) and archiving come with transactions.

## Transactions

| Request | Purpose |
|---|---|
| `GET /transactions` | Filters: `account`, `instrument`, `type`, `from`, `to`, `validated` |
| `POST /transactions` | Create |
| `GET /transactions/{id}` | |
| `PUT /transactions/{id}` | Edit: creates a revision |
| `DELETE /transactions/{id}` | Soft delete |
| `GET /transactions/{id}/revisions` | History |
| `POST /transactions/{id}/validate` | Approve an agent transaction |
| `POST /transactions/{id}/reject` | Reject it (soft delete) |

`type` selects the fields, mirroring the records in [domain-model.md](domain-model.md):

```json
{
  "type": "buy",
  "account": "…",
  "date": "2026-03-12",
  "instrument": "…",
  "qty": "10",
  "price": {"amount": "120.00", "currency": "PLN"},
  "fee": {"amount": "0", "currency": "PLN"}
}
```

Every write re-validates the account's whole history, so an edit that would oversell a later sale is rejected with `422`. `sequence` is optional and defaults to the end of that day.

## Instruments

| Request | Purpose |
|---|---|
| `GET /instruments` | Instruments the user holds or has a profile for |
| `GET /instruments/search?q=` | Search the catalog and price providers |
| `POST /instruments` | Add a custom instrument or bond |
| `GET /instruments/{id}` | Instrument + profile |
| `PUT /instruments/{id}/profile` | Sector, risk, scenarios, thesis, Markdown note |
| `GET /bond-presets` | Polish series templates |

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
