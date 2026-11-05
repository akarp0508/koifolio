# REST API

Base path `/api/v1`. JSON. The web app authenticates with a session cookie. The server publishes an OpenAPI spec, and `web` generates its types from it.

## Conventions

- **Decimals are strings,** so JavaScript never turns them into floats: `{"amount": "120.00", "currency": "PLN"}`.
- **Dates** are ISO: `2026-03-12`.
- **Errors** are `application/problem+json` (RFC 9457) with a stable `code`:
  ```json
  {"status": 422, "code": "oversold", "detail": "Selling 15 PAS but only 10 held on 2026-03-12"}
  ```
- **Concurrency:** editable resources carry a `version`. A stale `PUT`/`PATCH` returns `409`.
- **Pagination:** list endpoints that can grow take `cursor` and `limit`, and return `{"items": [...], "nextCursor": "..."}`.

## Auth

| Request | Purpose |
|---|---|
| `GET /auth/status` | `{"setupRequired": false, "authenticated": true, "username": "..."}` |
| `POST /auth/setup` | Create the first user; refused once one exists |
| `POST /auth/login` | `{"username", "password"}` |
| `POST /auth/logout` | |

## Settings

| Request | Purpose |
|---|---|
| `GET /settings` | `{"baseCurrency": "PLN", "costFxRule": "previous_publication"}` |
| `PATCH /settings` | Change settings; a base currency change recomputes snapshots |

## Accounts

| Request | Purpose |
|---|---|
| `GET /accounts` | List; `?archived=true` includes archived |
| `POST /accounts` | Create |
| `GET /accounts/{id}` | |
| `PATCH /accounts/{id}` | Edit |
| `DELETE /accounts/{id}` | Delete if empty, otherwise archive |

```json
{
  "id": "…", "version": 1,
  "name": "Retirement account",
  "description": "Tax-free, held until retirement",
  "tax": {"method": "average", "rate": "0"}
}
```

## Transactions

| Request | Purpose |
|---|---|
| `GET /transactions` | Filters: `account`, `instrument`, `type`, `from`, `to` |
| `POST /transactions` | Create |
| `GET /transactions/{id}` | |
| `PUT /transactions/{id}` | Edit: creates a revision |
| `DELETE /transactions/{id}` | Soft delete |
| `GET /transactions/{id}/revisions` | History |

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
