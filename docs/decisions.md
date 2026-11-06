# Decisions

## Settled

- **MVP first, one feature at a time:** login, then balance info, then transactions. See [roadmap.md](roadmap.md). Decisions below that go beyond the MVP are direction, not current scope.
- **Web UI:** clean and minimal, one koi-orange accent, light/dark following the system with a toggle. Plain CSS with theme variables, no UI framework or router yet; both can be added when there's a need. English only for now; PL/EN comes later.
- **The web token is kept in `localStorage`,** so a login survives closing the tab. That exposes it to any script on the page, which is why the app loads no third-party scripts.
- **Auth is JWT.** Stateless HS256 access tokens through Spring Security's resource server support, signed with `KOIFOLIO_JWT_SECRET`. Passwords use BCrypt.
- **Registration is open to anyone who can reach the app.** Each user's data is isolated, so a new user can't affect anyone else's. Rate limiting is needed before exposing the app publicly, since nothing stops mass sign-ups or password guessing.
- **Universal by design.** Nothing in the data model is tied to one broker, exchange, country or tax system. Polish things (bond series, tax presets, NBP) are presets and pluggable providers on top of a generic model.
- **Automatic prices are limited for now** to what bankier covers, to keep the fetching code simple. Anything else shows no price.
- **Prices are shared and fetched every 15 minutes,** with a Refresh button on a one-minute cooldown. See [price-sources.md](price-sources.md).
- **Daily closes are stored locally,** shared between users, from each symbol's first trade onward. Only finished days are stored, and a check in the 15-minute run fills anything missing. Position values over time are calculated from them on request.
- **No manual prices yet.** They'd matter for instruments the source doesn't cover, or if it goes down.
- **FX uses NBP table A,** latest rates plus stored daily history from the first trade, filled like closes. Other pairs are cross rates through PLN, and currencies outside the table have no rate. A price in another currency than its account is converted, and totals use a base currency per user. Cost stays in the account's currency, so purchase-date rates and the cost FX setting are still planned.
- **Period changes measure gain, not value:** value − cost + realized, so deposits, buys and sells aren't profit.
- **Each account has one currency and one balance** in the MVP, set directly by the user. A multi-currency broker account means several accounts. This replaces the earlier plan of cash per currency within an account. How transactions affect the balance is decided in the transactions step.
- **Stack:** Java 25, Spring Boot 4.1, Maven project in `server/`, Postgres 18, React + Vite + TypeScript + Recharts.
- **Deployment:** Docker Compose on a Raspberry Pi 5 (8 GB), reached over Tailscale. Built so it can later be hosted for others.
- **Storage:** Postgres is the source of truth. Plain text is export/import plus an optional one-way git backup.
- **Agents:** through MCP only.
- **Accounts own lots and cash.** An account has a name, a free-form description, a currency and a cost method. There are no fixed account types.
- **Cost basis:** `average` or `fifo`, set per account, average by default. Changing it recalculates positions.
- **Positions are calculated on each request,** in the `domain` package, not stored. One per account and ticker. A sale past the shares on record counts only the shares with a known cost, and the position is marked `incomplete`.
- **Bonds:** one generic model, with Polish retail series as presets.
- **Base currency:** configurable, default PLN.
- **Transactions are edited in place and deleted for real in the MVP.** Revisions and soft delete come with the audit log. This replaces "never destroyed" for now.
- **Prices:** free APIs and scraping only, no headless browser.
- **No Nix dev shell.** Local tools come from the system; Docker covers full builds.
- **No CLI.** MCP covers what it was for.
- **One backend module.** Calculations live in the `domain` package of `server`, not a separate module, because the server is their only user.
- **Fees are part of cost basis:** buy fees raise cost, sell fees reduce proceeds.
- **Invalid history never blocks:** a sale of more than was held is saved and marked `oversold`, because backfilled history can arrive in any order or start partway through. Negative cash is allowed too.
- **Balance:** the account balance is stored and each transaction with `affectsBalance` changes it; editing or deleting a transaction undoes its effect. Setting the balance by hand just sets the number and records `balanceSetOn`. The UI defaults `affectsBalance` to off for dates before that day, so backfilled history isn't counted twice.
- **Going below zero asks first:** the server refuses with `negative_balance` unless the request confirms it, and the UI offers "Save anyway". This covers anything lowering the balance, and it's a confirmation, not a ban.
- **Two layers of notes.** The instrument holds what's true of the company: sector, risk, thesis and Markdown analysis. A position plan holds what you intend in one account: plan text, target, stop and an optional risk override. Risk has three parts: company risk (typed), intent here (the override), and size (the calculated weight, later).
- **Sectors are your own,** with used ones suggested, so they work for any market and breakdowns stay tidy.
- **Two independent kinds of tags,** filtered separately: company tags on instruments, position tags on plans. Tags replace nothing: accounts still own lots and cash. Any tag within a kind matches, and the kinds must both match.
- **The dashboard is the home page.** Cash has no tags, so it's left out while tags filter the view.
- **Instruments are private per user,** with optional names, filled by hand or automatically from transactions. Transactions link to them with a foreign key, so removing one only hides it from the list. A shared catalog with prices can sit behind this later.
- **MVP transactions:** deposit, withdrawal, buy, sell, dividend, fee. The ticker is plain text, amounts are in the account's currency, and deleting an account deletes its transactions.
- **v1 tax estimate:** unrealized is gain × rate per position; realized is per calendar year and account, with losses offsetting gains in that year. No carry-forward between years.
- **Dividends:** stored as gross + withheld; the form also accepts net only (withheld = 0).
- **Personal data is isolated, market facts are shared (later).** Each user's sector, risk, scenarios, thesis, plans and notes are private. When prices arrive, ticker, market and prices are stored once.
- **FX date for costs (planned) is a setting:** previous publication by default (the Polish tax rule), or the transaction date.
- **Agent transactions need review.** Agents can add transactions through MCP, but they arrive marked unvalidated and appear in an "Unvalidated changes" tab. They count in all numbers straight away. Approving clears the mark; rejecting soft-deletes the transaction. Agent edits to analysis apply immediately and go to the audit log. A settings screen for what MCP may do comes later.
- **Database layer: JPA / Hibernate** with Spring Data JPA, chosen because the database will grow well beyond the current tables. Flyway owns the schema (`ddl-auto: validate`), and entities stay separate from the `domain` records. Native SQL is fine where JPA doesn't fit, e.g. `ON CONFLICT` upserts.
- **OpenAPI is generated from code** with `springdoc-openapi`, added with the first controllers. Swagger UI is switched on with `KOIFOLIO_SWAGGER` and is off by default. It's meant for local use and for testing each batch; a browser page can't send a JWT, so it can't sit behind login.
- **Workflow:** every finished batch is committed, documented in Swagger and tested by hand by the user before the next batch starts.

## Open

Nothing right now.
