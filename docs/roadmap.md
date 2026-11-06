# Roadmap

Build one small feature at a time. Each batch is committed, shown in Swagger and tested by hand before the next one starts. From transactions on, a batch includes both the API and its screen.

## MVP

1. **Login** (done): register, login, JWT access tokens.
2. **Balance info** (done): accounts, each with one currency and one balance.
3. **Frontend catch-up** (done): login page and accounts page.
4. **Transactions** (done): API and screen, with backfill support.

The MVP is complete once step 4 is tested. Scope for each step is agreed when we reach it.

## Added after testing

5. **Instruments and balance warning** (done): a private ticker list that fills itself, and a confirmation before going below zero.
6. **Positions** (done): holdings, average cost and realized gains per account and ticker, with transactions linked to instruments.
7. **Prices** (done): current quotes every 15 minutes, with value and unrealized P/L on positions.
8. **Position history** (done): stored daily closes that repair themselves, and a chart per position.
9. **Company details and plans** (done): sector, risk, thesis and analysis per company, and a plan with target, stop and risk per account.
10. **Dashboard** (done): home page with totals in a base currency, sector and risk breakdowns, an attention list, and filters by account and two kinds of tags. NBP rates.

## After the MVP

The rest of the plans in this folder describe where the app may go, not a commitment. Rough order:

- **Portfolio value over time:** needs FX history, recalculated per filter. Comes with cost at purchase-date rates, currency gains, the cost FX setting, and charts for stocks in another currency. Tax estimates are on the `domain-replay-wip` branch.
- Manual prices, for what the source doesn't cover
- **Split transactions:** price history is split-adjusted, so a position held through a split shows wrong past values until splits can be entered
- More web UI, with each screen's look agreed first
- Daily snapshots
- MCP server and audit log: agents writing company analysis builds on the instrument fields
- Bonds, rules
- Backfill, export/import, git backup, passkeys
- Risk & scenarios view, rule checks with explanations
- Rate limiting on login and registration: required before the app is exposed publicly
