# Roadmap

1. Scaffolding: Maven server project, web app, Dockerfile + compose, CI.
2. `domain` package: domain model, positions/cost basis/P&L, bonds, rules + tests.
3. `server`: persistence + migrations, audit log, login, REST API, price providers + cache + manual override, scheduler (snapshots).
4. MCP server (read tools first, then write tools).
5. `web`: layout, dashboard, positions, transactions modal, accounts, company notes, rules, i18n, themes. → **v1**
6. Backfill, export/import, git backup, passkeys. → **v1.1**
7. Risk & scenarios view, rule checks with explanations.

Step 1 is done. Next: step 2, once the open items in [decisions.md](decisions.md) are settled.
