# Roadmap

Build one small feature at a time. Each batch is committed, shown in Swagger and tested by hand before the next one starts. From transactions on, a batch includes both the API and its screen.

## MVP

1. **Login** (done): register, login, JWT access tokens.
2. **Balance info** (done): accounts, each with one currency and one balance.
3. **Frontend catch-up** (done): login page and accounts page.
4. **Transactions:** API and screen together.

Scope for each step is agreed when we reach it.

## After the MVP

The rest of the plans in this folder describe where the app may go, not a commitment. Rough order:

- Domain calculations: positions, cost basis, P/L, FX, tax. A first version is on the `domain-replay-wip` branch.
- More web UI, with each screen's look agreed first
- Prices and daily snapshots
- MCP server and audit log
- Bonds, rules
- Backfill, export/import, git backup, passkeys
- Risk & scenarios view, rule checks with explanations
