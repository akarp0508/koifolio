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

Implemented.

| Request | Purpose |
|---|---|
| `GET /settings` | `{"baseCurrency": "PLN", "costFxRule": "previous_publication", "currencies": ["AUD", "…"]}` |
| `PATCH /settings` | Change `baseCurrency` or `costFxRule`; a currency with no rate gives `400 unknown_currency` |

`costFxRule` picks the rate that converts a trade: `previous_publication` (the last before it, the Polish tax rule) or `transaction_date`.

## Accounts

Implemented. Each account belongs to one user; another user's account returns `404`.

| Request | Purpose |
|---|---|
| `GET /accounts` | List yours, oldest first |
| `POST /accounts` | Create |
| `GET /accounts/{id}` | |
| `PATCH /accounts/{id}` | Change `name`, `description`, `balance` or `costMethod`; send only what changes |
| `DELETE /accounts/{id}` | Delete, along with its transactions |

```json
{
  "id": "…",
  "name": "Brokerage",
  "description": "Main account",
  "currency": "PLN",
  "balance": "5000.00",
  "balanceSetOn": "2026-11-05",
  "costMethod": "average"
}
```

- `balance` defaults to 0 and may be negative. It's returned as a string, with the currency's decimal places. More decimals than the currency has gives `400 invalid_amount`.
- `currency` is a real ISO code in capitals, otherwise `400`. It can't be changed after creation.
- `taxRate` is in percent, and left out if not set, which isn't the same as 0. Send `clearTaxRate: true` to unset it.
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
| `split` | `ticker`, `splitFrom`, `splitTo`: `splitTo` new shares for every `splitFrom` old ones | none; never touches the balance |

- Every type also needs `date` and `affectsBalance`. Responses add `id`, `currency`, `cashEffect`, `oversold` and `unvalidated`, plus `createdBy` for an agent's, and leave out fields the type doesn't use.
- `GET /transactions?unvalidated=true` lists what agents added and you haven't reviewed. `POST /transactions/{id}/approve` clears the mark. `POST /transactions/{id}/reject` removes one and undoes its effect on the balance. Either returns `409` if it isn't waiting.
- `DELETE /transactions/{id}` removes it from every number and undoes its effect on the balance, but keeps it in the database.
- Cash effects are rounded to the currency's decimals.
- `oversold` marks a sale of more than was held at that point, per account and ticker. It's a warning, and it clears once the history adds up.
- A change that takes the balance below zero returns `409 negative_balance`, with the resulting balance in `detail`, and saves nothing. Sending `allowNegativeBalance: true` goes ahead. Changes that don't lower the balance, and transactions with `affectsBalance` off, never ask.
- A transaction or balance past 15 digits returns `400` (`amount_out_of_range`, `balance_out_of_range`).
- Pagination and `version` come later.

## Instruments

Implemented. Each user has their own private list. An instrument holds what's true of the company, whichever account holds it; see plans under Positions.

| Request | Purpose |
|---|---|
| `GET /instruments` | List yours, by ticker |
| `POST /instruments` | `{"ticker", "name", "sector", "risk", "thesis", "analysis"}`; only `ticker` is required, a repeat gives `409 instrument_exists` |
| `GET /instruments/{id}` | One, including ones removed from the list that you still hold |
| `PUT /instruments/{id}` | Replace everything except the ticker; what's left out is cleared |
| `DELETE /instruments/{id}` | Remove from the list; transactions are kept |

- `risk` is 1 (very low) to 5 (very high), and left out if not set.
- `analysis` is Markdown. The UI doesn't render raw HTML in it.
- `tags` are cleaned to lower case without repeats, up to 20 of 30 characters each.

A transaction using a new ticker adds it to the list. Removing one hides it from the list and keeps its transactions; using it again, or adding it again, brings it back.

Planned: `GET /instruments/search`, `GET /bond-presets`.

## Positions

Implemented. Worked out from buys and sells on every request; nothing is stored.

| Request | Purpose |
|---|---|
| `GET /positions` | By ticker, then account. Filters: `account`; `closed=true` includes fully sold positions |
| `GET /positions/{accountId}/{ticker}/history` | One point per trading day since the first trade |

```json
{
  "accountId": "…",
  "ticker": "PAS",
  "currency": "PLN",
  "quantity": "15",
  "averageCost": "110.00",
  "cost": "1650.00",
  "realizedGain": "200.00",
  "closed": false,
  "incomplete": false,
  "price": "151",
  "priceCurrency": "PLN",
  "quotedAt": "2026-11-05T16:00:00Z",
  "value": "2265.00",
  "unrealizedGain": "615.00",
  "unrealizedPercent": "37.3",
  "currencyMismatch": false
}
```

- One position per account and ticker, using the account's `costMethod`.
- `cost` and `realizedGain` include fees. Dividends aren't part of realized gain: `dividends` is what was received net of tax, and `dividendsWithheld` the tax taken.
- A dividend on a ticker with no trades makes a closed position.
- `incomplete` means a sale exceeded the shares on record. Realized gain then counts only shares with a known cost, and the position always shows, even if nothing is held.
- `averageCost` is left out once nothing is held.
- `weight` and `portfolioWeight` are percentages of the account and of all accounts, with cash in the total, at the latest rates. They don't change with filters, and are left out without a value. Unpriced positions aren't in the total, so weights rise until they get a price.
- `inBase` repeats cost, realized, value, gain and dividends in the base currency, with `currencyGain`, and is left out when the account already uses it.
- Price fields are left out when there's no price, or nothing is held. A price in another currency is converted to the account's at the latest rate; without a rate, `currencyMismatch` is set and `value` and the gains are left out.

| `GET /positions/{accountId}/{ticker}/details` | The company and your plan together |
| `PUT /positions/{accountId}/{ticker}/plan` | Replace your plan |

A plan belongs to an account and instrument, and stays if you sell out and buy back:

```json
{"plan": "Trade around earnings", "risk": 4, "targetPrice": "180", "stopPrice": "120", "tags": ["swing"]}
```

- `risk` overrides the company's risk for this position. `GET /positions` shows the plan's risk if set, otherwise the company's.
- `targetPrice` and `stopPrice` are in the price's currency.
- `signal` on a position is `NEAR_TARGET`, `TARGET_REACHED`, `NEAR_STOP` or `STOP_HIT`. Near means within 5%, and the stop wins if both apply. `details` also gives `targetDistance` and `stopDistance` in percent.

History response:

```json
{
  "accountId": "…",
  "ticker": "PAS",
  "currency": "PLN",
  "covered": true,
  "currencyMismatch": false,
  "points": [
    {"date": "2026-03-02", "quantity": "10", "close": "120", "value": "1200.00", "cost": "1000.00", "traded": true}
  ]
}
```

- Today's point comes from the current price while the day is unfinished.
- `covered` is false when the price source doesn't have the ticker. Covered with no points means history is still being fetched.
- Closes in another currency convert at each day's rate. With `currencyMismatch` there's no rate, so points have `close` but no `value`.
- `traded` marks a buy or sell since the previous point, and a trade on a non-trading day shows on the next one.

## Dashboard

Implemented.

| Request | Purpose |
|---|---|
| `GET /dashboard` | Totals, breakdowns, attention list and holdings. Filters: `account`, `instrument`, and repeatable `instrumentTag` and `positionTag` |
| `GET /dashboard/history` | Value over time and period changes, with the same filters |

```json
{
  "currency": "PLN",
  "value": "3400.00",
  "positionsValue": "2000.00",
  "unrealizedGain": "600.00",
  "currencyGain": "100.00",
  "unrealizedPercent": "42.9",
  "realizedGain": "80.00",
  "dividends": "81.00",
  "dividendsWithheld": "19.00",
  "cash": "1400.00",
  "bySector": [{"label": "IT", "value": "1200.00", "percent": "60.0"}, {"value": "800.00", "percent": "40.0"}],
  "byRisk": [{"label": "4-5", "value": "1800.00", "percent": "90.0"}],
  "attention": [{"accountId": "…", "accountName": "IKE", "instrumentId": "…", "ticker": "PAS", "signal": "NEAR_TARGET", "distance": "2.9"}],
  "unpriced": 0,
  "holdings": [{
    "instrumentId": "…", "ticker": "PAS", "held": true, "value": "1200.00", "unrealizedGain": "300.00",
    "unrealizedPercent": "33.3", "realizedGain": "80.00", "weight": "35.3",
    "accounts": [{"accountId": "…", "accountName": "IKE", "closed": false, "incomplete": false, "value": "1200.00",
                  "unrealizedGain": "300.00", "unrealizedPercent": "33.3", "realizedGain": "80.00", "weight": "35.3"}]
  }]
}
```

- `holdings` groups the matching positions by company, in the base currency, biggest first, with unpriced ones last. `held` is false once everything is sold, unless the records are `incomplete`.
- A holding's `weight` is of the chosen account, or of the whole portfolio without one, with cash in the total. Tags narrow what's listed but don't change the weight.
- Value and cash are at the latest rates. Cost and realized gains use each trade's own rate, per `costFxRule`.
- `currencyGain` is the part of `unrealizedGain` from rates moving: cost at today's rate minus cost at purchase rates. It covers accounts in another currency than the base. A foreign stock in a base-currency account has its currency effect inside the stock's gain, because its cost was entered in that currency.
- A position matches if it has any of the wanted instrument tags, and any of the wanted position tags. The two kinds, the account and the instrument must all match.
- `cash` is left out while `instrument` or tags filter the view, because cash has neither.
- Slices with no `label` have no sector or risk set. Risk groups are `1-2`, `3` and `4-5`, using the plan's risk if set.
- `unpriced` counts open positions left out of the numbers, lacking a price or a rate.

History response:

```json
{
  "currency": "PLN",
  "points": [{"date": "2026-11-05", "value": "2000.00", "cost": "1400.00", "gain": "600.00"}],
  "changes": [{"period": "DAY", "change": "60.00", "percent": "5.0"}]
}
```

- Positions only, without cash. Value uses each day's rate, and cost and realized gains each trade's own.
- `gain` is value − cost + realized, and a change is how much `gain` moved. Deposits, buys and sells therefore don't count as profit or loss.
- `period` is `DAY`, `WEEK`, `MONTH`, `YEAR_TO_DATE` or `YEAR`, compared with the last point before the period starts. Periods reaching further back than the history are left out, and `percent` is of the value at the start.
- Positions without prices or any rate are left out.

Dividends are net of tax withheld, each at the rate of the day `costFxRule` picks, and the history's `gain` and period changes include them from the day they're paid. Holdings have `dividends` too.

Planned: realized gains and tax per year, income per year.

## Tax

Implemented. An estimate of tax on sales and dividends, in the base currency. Not tax advice.

| Request | Purpose |
|---|---|
| `GET /tax` | This year, or `?year=` |

- Accounts with the same `taxRate` add up per calendar year, so losses offset gains between them. Losses aren't carried between years.
- `totalTax` is tax on that year's sales. `totalIfSoldToday` is the extra tax if everything open were sold now, after this year's realized gains, and it can be negative. It's left out for past years.
- `dividendGroups` has that year's dividends per rate and account, with `gross`, `withheld` and `taxLeft`. `totalDividendTax` adds up what's left to pay. Each dividend owes its gross at the account's rate less what was withheld, never below zero, and sale losses don't offset it.
- `unsetAccounts` names accounts with something to tax but no rate, which aren't counted.
- Cost, proceeds and dividends use each one's own rate, per `costFxRule`.

## Bonds

Implemented, for Polish TOS, ROS, EDO and ROD bonds in PLN accounts.

| Request | Purpose |
|---|---|
| `GET /bonds` | Purchases valued today, with `value`, `interest` and `ifRedeemedToday` |
| `POST /bonds` | Add one: `series`, `accountId`, `quantity`, `purchaseDate`, `rates`, `earlyFee`, `affectsBalance` |
| `PUT /bonds/{id}/rates` | Replace the yearly rates |
| `DELETE /bonds/{id}` | Remove one, putting the cost back if it affected the balance |

- `series` must match the purchase date, so a TOS bought in August 2025 is `TOS0828`.
- `earlyFee` is per bond, and only a TOS can leave it out: 1.00 from September 2024, 0.70 before. It never exceeds the interest earned, and doesn't apply from maturity.
- `rateAssumed` means a year has no rate yet, so the last one stands in.
- Tax isn't taken off `ifRedeemedToday`. The tax page's `totalIfSoldToday` includes bond interest at the account's rate.

## Agent tokens

Implemented. Tokens for the [MCP server](mcp.md), which work there and nowhere else.

| Request | Purpose |
|---|---|
| `GET /agent-tokens` | List, without secrets |
| `POST /agent-tokens` | Create from `name`, optional `expiresOn`, the last day it works, and optional `scopes`. The `secret` is only returned here |
| `DELETE /agent-tokens/{id}` | Revoke at once |

## Review

Implemented. Agent company, rule and account changes wait here, and apply only when approved.

| Request | Purpose |
|---|---|
| `GET /review/count` | How much is waiting, transactions and changes together |
| `GET /review/changes` | What's waiting, with `current` and `proposed` values |
| `POST /review/changes/{id}/approve` | Apply one |
| `POST /review/changes/{id}/reject` | Drop one |
| `POST /review/approve-all` | Approve transactions and changes, for `?agent=` or everyone. If any can't apply, none are |
| `POST /review/reject-all` | Reject transactions and changes, for `?agent=` or everyone |
| `POST /review/log/{id}/revert` | Undo an agent's company, rule or account change from the change log. `409 changed_since` if it has changed since, unless `force=true`. `409 account_in_use` if an account has transactions |

## Change log

Implemented. `GET /audit-log` gives recent changes to transactions, companies, rules, accounts and plans, newest first, with `limit` up to 500. Each has `actorType`, `actorName` for an agent, `entityType`, `entityId`, `action`, `summary`, `before`, `after`, `revertible` and `changedSince`. For a plan, `entityId` is the company, and `before` and `after` name the account. Deleting an account keeps what went with it in `before`.

## Split suggestions

Implemented. When past closes change by one ratio, the app suggests a split. Nothing changes until you add it.

| Request | Purpose |
|---|---|
| `GET /split-suggestions` | Splits you haven't entered or dismissed: `{"id", "ticker", "date", "splitFrom", "splitTo", "accounts"}` |
| `POST /split-suggestions/{id}/apply` | Add the split to every affected account, on the suggested date |
| `POST /split-suggestions/{id}/dismiss` | Stop suggesting it, for you only |

`date` is the first day seen after the split, so it can be late. A split already entered within 14 days counts as the same one, and accounts that didn't hold the stock are left out.

## Rules

Implemented.

| Request | Purpose |
|---|---|
| `GET /rules` | List |
| `POST /rules` | Create |
| `PUT /rules/{id}` | Edit |
| `DELETE /rules/{id}` | Delete |
| `GET /rules/check` | Each rule with `passed`, `actual`, `breaches` and `unpriced` |

```json
{
  "rule": {"id": "…", "type": "max_position_weight", "limitPercent": "20"},
  "passed": false,
  "actual": "23.1",
  "breaches": [{"ticker": "PAS", "weight": "23.1"}],
  "unpriced": 0
}
```

- Types are `max_position_weight`, and `min_low_risk_weight`, which needs `maxRisk`, the highest risk that counts as low.
- Leaving out `accountId` covers everything. A company in two accounts then counts as one, in the base currency. A rule on one account uses that account's currency.
- Weights are of the value plus cash. Cash counts as low risk, a position's risk is its plan's if set, and positions with no risk don't count as low.
- `actual` is left out when there's no positive total to check, and the rule passes.
- `unpriced` counts open positions left out, lacking a price or a rate.

## Prices

Implemented. Prices update every 15 minutes on their own.

| Request | Purpose |
|---|---|
| `POST /prices/refresh` | Fetch now; repeating within a minute gives `429 refresh_too_soon` |

Planned: `GET /prices/status`, manual price overrides.

## Audit

| Request | Purpose |
|---|---|
| `GET /audit` | Filters: `actor`, `entity`, `from`, `to` |

## v1.1

Implemented.

| Request | Purpose |
|---|---|
| `GET /export` | Zip in the [export format](export-format.md) |
| `POST /import?mode=replace` | Upload an export as multipart `file`, replacing all your data. Up to 20 MB |

- There's no merge: telling a repeated trade from a real second one is a guess, and a wrong guess doubles a position.
- Restoring runs in one transaction, so `400 invalid_import` means nothing changed. Its `detail` names the file and line.
| `POST /portfolio/backfill` | Fill history gaps |
