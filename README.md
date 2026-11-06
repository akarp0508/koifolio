# koifolio

A self-hosted, AI-agent-friendly portfolio tracker for individual investors. It works with any broker, exchange or tax system. Automatic prices currently cover GPW, NewConnect, NYSE, NASDAQ and Deutsche Börse; anything else works with manual prices.

> **Status:** early development, building an MVP. Login, accounts with balances, transactions, instruments with company notes, positions with per-account plans, current prices, position history and a dashboard work, in the API and web UI. See the [roadmap](docs/roadmap.md).

## Why

Brokerage apps show *what* you hold. They don't help you keep track of *why* you hold it, how risky it is, or whether your portfolio still follows your own rules.

koifolio keeps your transactions, accounts and company analyses in one place. You use it through a web UI, and AI agents (Claude Code and others) work with the same data through an MCP server: they write company analyses, refresh risk scores and check rules. Everything is exportable to plain text (Markdown, YAML, JSONL) at any time, optionally backed up to your own git repo. The app is open source and self-hosted.

## Core ideas

- **Analysis lives next to the numbers.** Each company has a Markdown note plus structured fields (sector, risk score, scenarios). Agents update the analysis and the numbers in one place.
- **Agents are first-class users.** A built-in MCP server exposes the same calculations the UI shows. Every agent change is checked by the same code as the UI and recorded in the audit log, and agent transactions wait for your review.
- **Your data is never locked in.** Plain-text export/import in documented formats, plus an optional scheduled git backup.
- **Multiple accounts.** Any kind of account, each with its own tax settings (e.g. a regular brokerage account, or a tax-free one like Polish IKE) — viewed separately or together.
- **Your rules, checked automatically.** E.g. "max 20% in one company", "~50% in low-risk assets".
- **Self-hosted, always on.** One Docker Compose stack (app + Postgres), light enough for a Raspberry Pi 5. Built multi-user from day one, even if you're the only user.

## Features

### v1
- **Dashboard** — totals in your base currency, 1D to 1Y changes, value over time, sector and risk breakdowns, and what needs attention, filtered by account, company tags and position tags.
- **Transactions & positions** — add, edit and delete buys/sells/dividends/deposits in a modal form; average price, P/L, weights computed automatically.
- **Positions** — holdings, average cost, value and P/L per account and ticker, with average or FIFO cost, and a plan per account with target, stop and risk.
- **Position history** — daily closes stored locally, with gaps filled automatically, and a value chart per position.
- **Current prices** — fetched every 15 minutes for GPW, NewConnect, NYSE, NASDAQ and Deutsche Börse.
- **Instruments** — your own list of companies with sector, risk, thesis and Markdown analysis; new tickers join it automatically.
- **Multiple accounts** — filter by account or see everything combined, incl. bonds valued by the generic bond model (Polish series as presets).
- **Cost basis & tax** — average/FIFO and tax rate per account, with presets.
- **Dashboard** — total value, P/L, cash, rule status (pass/fail), value over time (from daily snapshots), allocation by company / sector.
- **FX** — NBP rates, with stocks converted to their account's currency and totals to your base currency.
- **Company notes** — edit structured fields and the Markdown analysis in the UI.
- **Rules** — define rules, see pass/fail.
- **MCP server** for agents, with audit log.
- **Unvalidated changes** — transactions added by agents count straight away, marked unvalidated, and wait in a tab for you to approve or reject.
- **Login** — local user accounts with open registration, password, JWT access tokens.
- **PL / EN** interface, **light / dark** theme (follows system by default).

### v1.1
- **Backfill** — fill history gaps (downtime, the period before koifolio existed) from transactions + historical closing prices where the provider has them.
- **Plain-text export/import** in a [documented format](docs/export-format.md).
- **Git backup** — scheduled, one-way, pushes the export to a repo you configure.
- **Passkeys.**

### Later
- Risk & scenarios view: risk/return chart, expected portfolio return, scenario ranges.
- Rule checks with explanations ("ABC is 18.8% of IKE — limit 20%").
- Broker import (e.g. XTB xlsx export).
- Agent skills shipped with the repo (e.g. "update company note after quarterly report").
- Event calendar (earnings dates) read from notes.
- MCP settings: choose what agents may do.
- Hosting for others: signup/invites, OIDC, admin.

## UI

Sidebar + dashboard layout:

```
┌───────────┬─────────────────────────────────────────┐
│ koifolio  │ Account: [All ▾]                PL|EN ◐ │
│           ├─────────────────────────────────────────┤
│ ▸ Dashboard │ ┌Value────┐┌P/L─────┐┌Cash────┐┌Rules┐ │
│   Positions │ │ 50 000  ││ +4 200 ││ 1 000  ││ 2/2 │ │
│   Transact. │ └─────────┘└────────┘└────────┘└─────┘ │
│   Accounts  │ ┌Value over time────┐┌Allocation─────┐ │
│           │ │     ╱╲   ╱──      ││  ◔ companies  │ │
│           │ │ ───╱  ╲╱          ││  ○ sectors    │ │
│           │ └───────────────────┘└───────────────┘ │
│           │ Positions                [+ Transaction]│
│           │ Ticker Acct Qty  Avg  Price  P/L   Wt  │
│           │ ABC    IKE   20  100   130   +30%  12% │
└───────────┴─────────────────────────────────────────┘
```

## Architecture

Java backend, React frontend, one Docker Compose stack.

```
koifolio/
├─ server/            # Spring Boot (Maven): domain logic, REST API, MCP server, auth, persistence, price providers, scheduler
└─ web/               # React + Vite + TypeScript, Recharts, i18n (PL/EN)
```

- **server:** Spring Boot, Java 25.
  - The `domain` package holds all calculations as plain Java (no Spring, no I/O), so the UI, MCP tools and export produce the same numbers.
  - Spring Security (JWT access tokens, password then passkeys, OAuth2 authorization server for MCP clients)
  - Spring Data JPA + Flyway on Postgres
  - Spring AI MCP server
  - jsoup for scraping, JGit for the git backup
- **web** only displays and edits data through the REST API; no calculations. TypeScript types are generated from the server's OpenAPI spec.
- **Deployment:** a multi-arch Docker image (`linux/arm64` for Raspberry Pi 5, plus `amd64`). A multi-stage build compiles `web` and serves it from Spring Boot. Compose runs app + Postgres. Reached over Tailscale for now; public exposure later is a config change, since auth already exists. No headless browser.
- **Dev environment:** JDK 25, Maven, Node 24 (for `web`). In dev, Vite proxies API calls to Spring Boot.

## Plans

The detailed plans live in [`docs/`](docs/README.md): domain model, database, REST API, MCP tools, export format, price sources and roadmap.

## Disclaimer

koifolio is a personal tool, not investment advice. Risk scores and scenarios are opinions you store — the app only displays and aggregates them.

## License

[GPL-3.0-or-later](LICENSE)
