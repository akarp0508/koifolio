# Decisions

## Settled

- **Website first.** Deployment is deferred on purpose. MCP was too, and it's now being built so an agent can enter real data. See [roadmap.md](roadmap.md) and [expectations.md](expectations.md).
- **Three places, not one page per feature.** Portfolio holds totals and holdings, each company gets one page, and Activity holds transactions. Accounts and Tax stay as smaller links. A company's All tab is in the base currency, and an account's tab in that account's. Weight is one number: of the chosen account, or of the portfolio with none chosen.

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
- **FX uses NBP table A,** latest rates plus stored daily history from the first trade, filled like closes. Other pairs are cross rates through PLN, and currencies outside the table have no rate. A price in another currency than its account is converted, and totals use a base currency per user. 
- **Base-currency cost uses each trade's own rate:** trades are converted first, then run through the same calculator, so lots, FIFO, fees and splits all follow. The rate date is a setting: the last rate before the trade by default (the Polish tax rule), or the trade date. Currency gain is cost at today's rate minus cost at purchase rates.
- **Period changes measure gain, not value:** value − cost + realized, so deposits, buys and sells aren't profit.
- **Each account has one currency and one balance** in the MVP, set directly by the user. A multi-currency broker account means several accounts. This replaces the earlier plan of cash per currency within an account. How transactions affect the balance is decided in the transactions step.
- **Stack:** Java 25, Spring Boot 4.1, Maven project in `server/`, Postgres 18, React + Vite + TypeScript + Recharts.
- **Deployment, when it comes:** Docker Compose on a Raspberry Pi 5 (8 GB), reached over Tailscale. Built so it can later be hosted for others.
- **Storage:** Postgres is the source of truth. Plain text is export and restore plus an optional one-way git backup. Restore replaces everything rather than merging, because a merge has to guess which trades are repeats, and a wrong guess silently doubles a position. Balances are restored as they stood, not rebuilt from transactions, because backfilled history doesn't affect them.
- **License: GPL-3.0-or-later.** It works with the Apache 2.0 libraries the app ships, such as Spring, which GPL 2 alone can't be combined with. It allows commercial use, as long as anyone distributing it shares the source. Hosting it as a service doesn't count as distributing. It replaces MIT, and the koi logo in the repo is covered by it too.
- **Branding comes from the koibranding repo:** logo and palette (ink, lime, orange, paper). Dark mode uses them directly. Lime and orange are unreadable on paper, so light mode fills accents with ink and darkens gains, losses and warnings. Every text colour is at least 4.5:1 on both backgrounds.
- **Agents:** through MCP only.
- **Agents can propose new accounts,** under their own scope, which is on by default. An account's balance is a claim about real money, so it waits for approval like company and rule changes. Agents can't edit or delete accounts.
- **Agent company and rule changes wait for approval.** They're stored as proposals and apply only once approved, field by field over the current values. Approved ones can be reverted from the change log, which restores everything from before and warns if it has changed since. Transactions differ: they count at once, as already decided, because reviewing a batch is easier with the numbers in place. This replaces agent edits applying immediately.
- **Agent login:** personal tokens first, each with a name, shown once and stored as a hash. They work only at `/mcp`. OAuth comes later for cloud clients, as a second way in that the tools don't see. This replaces OAuth-only.
- **Accounts own lots and cash.** An account has a name, a free-form description, a currency and a cost method. There are no fixed account types.
- **Cost basis:** `average` or `fifo`, set per account, average by default. Changing it recalculates positions.
- **Positions are calculated on each request,** in the `domain` package, not stored. One per account and ticker. A sale past the shares on record counts only the shares with a known cost, and the position is marked `incomplete`.
- **Bonds:** Polish yearly-capitalising series only for now, valued from their terms and held apart from transactions. They count in totals, weights, the chart and the tax estimate, and as low risk in rules. Rates are entered by hand. Redemption isn't modelled: a matured bond keeps its final value until it's removed. The aim is still one generic model with Polish series as presets.
- **Base currency:** configurable, default PLN.
- **Transactions are edited in place and soft-deleted.** Each edit keeps its old values in the change log, and deleted ones stay in the database, out of every number and out of exports. A restore erases them for good, along with everything else it replaces.
- **The change log covers transactions, companies, rules, accounts and plans,** from either you or an agent. Settings, tokens and restores aren't logged. It isn't part of an export, and a restore leaves it alone.
- **Deleting an account erases its transactions, plans and rules,** because of the database cascade. Its change log entry keeps a copy of them under `before`, so they can be read and entered again by hand.
- **Prices:** free APIs and scraping only, no headless browser.
- **No Nix dev shell.** Local tools come from the system; Docker covers full builds.
- **No CLI.** MCP covers what it was for.
- **One backend module.** Calculations live in the `domain` package of `server`, not a separate module, because the server is their only user.
- **Fees are part of cost basis:** buy fees raise cost, sell fees reduce proceeds.
- **Splits are entered as transactions,** as new shares for old ones, so 1 for 3 stays exact. Quantity changes, total cost doesn't, and the balance is never touched. The source's prices are already adjusted, so past values are scaled back up by any later splits. Splits are suggested, never applied: when the source rewrites past closes by one ratio, the app refetches the symbol and offers the split for you to add. Splits before the closes were stored can't be detected.
- **Invalid history never blocks:** a sale of more than was held is saved and marked `oversold`, because backfilled history can arrive in any order or start partway through. Negative cash is allowed too.
- **Balance:** the account balance is stored and each transaction with `affectsBalance` changes it; editing or deleting a transaction undoes its effect. Setting the balance by hand just sets the number and records `balanceSetOn`. The UI defaults `affectsBalance` to off for dates before that day, so backfilled history isn't counted twice.
- **Going below zero asks first:** the server refuses with `negative_balance` unless the request confirms it, and the UI offers "Save anyway". This covers anything lowering the balance, and it's a confirmation, not a ban.
- **Two layers of notes.** The instrument holds what's true of the company: sector, risk, thesis and Markdown analysis. A position plan holds what you intend in one account: plan text, target, stop and an optional risk override. Risk has three parts: company risk (typed), intent here (the override), and size (the calculated weight).
- **Sectors are your own,** with used ones suggested, so they work for any market and breakdowns stay tidy.
- **Two independent kinds of tags,** filtered separately: company tags on instruments, position tags on plans. Tags replace nothing: accounts still own lots and cash. Any tag within a kind matches, and the kinds must both match.
- **The dashboard is the home page.** Cash has no tags, so it's left out while tags filter the view.
- **Instruments are private per user,** with optional names, filled by hand or automatically from transactions. Transactions link to them with a foreign key, so removing one only hides it from the list. A shared catalog with prices can sit behind this later.
- **MVP transactions:** deposit, withdrawal, buy, sell, dividend, fee. The ticker is plain text, amounts are in the account's currency, and deleting an account deletes its transactions.
- **Tax estimate:** on sales only, per calendar year. Accounts with the same rate add up, as on a Polish PIT-38, so losses offset gains between them. No carry-forward between years. A rate can be unset, which leaves the account out and says so. "If sold today" is the extra tax after this year's realized gains. This replaces the earlier per-account plan.
- **Dividends:** stored as gross + withheld; the form also accepts net only (withheld = 0). They count in returns net of withheld tax, apart from realized gain, at the rate of the day `costFxRule` picks. They also count in the chart's gain and the period changes, because prices fall by what's paid out. On the Tax page each dividend owes its gross at the account's rate less what was withheld, never below zero, and sale losses don't offset it. A dividend entered as net only therefore shows too much left to pay. This replaces leaving dividends out of the estimate.
- **Dividends with no trades:** a dividend on a ticker with no buys on record makes a closed position, so it's still counted and can be seen.
- **Personal data is isolated, market facts are shared (later).** Each user's sector, risk, scenarios, thesis, plans and notes are private. When prices arrive, ticker, market and prices are stored once.
- **Agent transactions need review.** Agents can add transactions through MCP, but they arrive marked unvalidated and appear on a Review page. They count in all numbers straight away. Approving clears the mark; rejecting soft-deletes the transaction. What a token may do is set when it's created. Agents can't approve their own work.
- **Database layer: JPA / Hibernate** with Spring Data JPA, chosen because the database will grow well beyond the current tables. Flyway owns the schema (`ddl-auto: validate`), and entities stay separate from the `domain` records. Native SQL is fine where JPA doesn't fit, e.g. `ON CONFLICT` upserts.
- **OpenAPI is generated from code** with `springdoc-openapi`, added with the first controllers. Swagger UI is switched on with `KOIFOLIO_SWAGGER` and is off by default. It's meant for local use and for testing each batch; a browser page can't send a JWT, so it can't sit behind login.
- **Workflow:** every finished batch is committed, documented in Swagger and tested by hand by the user before the next batch starts.

## Open

Nothing right now.
