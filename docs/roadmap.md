# Roadmap

Build one small feature at a time, each finished and tested before the next starts.

## MVP

1. **Login** (done): register, login, JWT access tokens.
2. **Balance info:** store each user's accounts and cash balances.
3. **Transactions:** store each user's transactions.

Scope for each step is agreed when we reach it.

## After the MVP

The rest of the plans in this folder describe where the app may go, not a commitment. Rough order:

- Domain calculations: positions, cost basis, P/L, FX, tax. A first version is on the `domain-replay-wip` branch.
- Web UI, once its look is agreed
- Prices and daily snapshots
- MCP server and audit log
- Bonds, rules
- Backfill, export/import, git backup, passkeys
- Risk & scenarios view, rule checks with explanations
