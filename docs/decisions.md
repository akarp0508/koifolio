# Decisions

## Settled

- **Stack:** Java 25, Spring Boot 4.1, Maven multi-module (`core`, `server`), Postgres 18, React + Vite + TypeScript + Recharts.
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

## Open

| # | Question | Recommended |
|---|---|---|
| 1 | Does the XTB account hold USD/EUR cash, or PLN only? | Track cash per currency in an account either way, with a `CurrencyExchange` transaction |
| 2 | Are fees part of cost basis? | Yes: buy fees raise cost, sell fees reduce proceeds (as Polish tax does) |
| 3 | What happens on invalid history? | Selling more than held is an error; negative cash is a warning |
| 4 | How detailed is the v1 tax estimate? | Unrealized: gain × rate per position. Realized: per calendar year and account, losses offset gains within the year. No carry-forward between years |
| 5 | How are dividends entered? | Store gross + withheld; the form also accepts net only (withheld = 0) |
| 6 | Can agents write transactions? | Off by default: agents get read + analysis, transactions need an explicit grant |
| 7 | Are instruments shared between users? | Yes: a shared catalog (ticker, market, currency, prices), with each user's sector, risk, scenarios and notes stored per user |
| 8 | Spring Data JDBC or JPA? | Spring Data JDBC: explicit SQL, no lazy loading, fits the revision model |
| 9 | How do we fill the history gap for 2026? | Find the `investor-chart` parameters from the browser's network tab, or evaluate EODHD's free tier |
