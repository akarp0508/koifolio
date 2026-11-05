# Decisions

## Settled

- **Universal by design.** Nothing in the data model is tied to one broker, exchange, country or tax system. Polish things (bond series, tax presets, NBP) are presets and pluggable providers on top of a generic model.
- **Automatic price fetching is limited for now** to GPW, NewConnect, NYSE, NASDAQ and Deutsche Börse, to keep the fetching code simple. Any other instrument still works, with a manual price.
- **Cash is tracked per currency within an account,** with a `CurrencyExchange` transaction for conversions. This suits any broker.
- **Stack:** Java 25, Spring Boot 4.1, Maven project in `server/`, Postgres 18, React + Vite + TypeScript + Recharts.
- **Deployment:** Docker Compose on a Raspberry Pi 5 (8 GB), reached over Tailscale. Built so it can later be hosted for others.
- **Storage:** Postgres is the source of truth. Plain text is export/import plus an optional one-way git backup.
- **Agents:** through MCP only.
- **Accounts, not tags.** An account has a name, a free-form description and its own tax settings. There are no fixed account types; the UI offers presets.
- **Cost basis:** `average` or `fifo`, set per account.
- **Bonds:** one generic model, with Polish retail series as presets.
- **Base currency:** configurable, default PLN.
- **Transactions are never destroyed:** edits create revisions, deletes are soft.
- **Prices:** free APIs and scraping only, no headless browser.
- **No Nix dev shell.** Local tools come from the system; Docker covers full builds.
- **No CLI.** MCP covers what it was for.
- **One backend module.** Calculations live in the `domain` package of `server`, not a separate module, because the server is their only user.
- **Fees are part of cost basis:** buy fees raise cost, sell fees reduce proceeds.
- **Invalid history:** selling more than held is an error; negative cash is a warning.
- **v1 tax estimate:** unrealized is gain × rate per position; realized is per calendar year and account, with losses offsetting gains in that year. No carry-forward between years.
- **Dividends:** stored as gross + withheld; the form also accepts net only (withheld = 0).
- **Personal data is isolated, market facts are shared.** Each user's sector, risk, scenarios, thesis, plans and notes are private. Ticker, market and prices are stored once.
- **FX date for costs is a setting:** previous publication by default (the Polish tax rule), or the transaction date.
- **Agent transactions need review.** Agents can add transactions through MCP, but they arrive marked unvalidated and appear in an "Unvalidated changes" tab. They count in all numbers straight away. Approving clears the mark; rejecting soft-deletes the transaction. Agent edits to analysis apply immediately and go to the audit log. A settings screen for what MCP may do comes later.
- **Database layer: JPA / Hibernate** with Spring Data JPA, chosen because the database will grow well beyond the current tables. Flyway owns the schema (`ddl-auto: validate`), and entities stay separate from the `domain` records. Native SQL is fine where JPA doesn't fit, e.g. `ON CONFLICT` upserts.

## Open

| # | Question | Notes |
|---|---|---|
| 1 | How do we fill the history gap for 2026? | Find the `investor-chart` parameters from the browser's network tab, or evaluate EODHD's free tier |
