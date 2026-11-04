# agentfolio

A local-first, AI-agent-friendly portfolio tracker for individual investors — built around the Warsaw Stock Exchange (GPW), but not limited to it.

> **Status:** planning. Nothing is implemented yet — this README is the plan.

## Why

Brokerage apps show *what* you hold. They don't help you keep track of *why* you hold it, how risky it is, or whether your portfolio still follows your own rules.

agentfolio keeps everything in plain files (Markdown, YAML, JSONL) that live in **your own private git repo**. You edit them through a web UI — and AI agents (Claude Code and others) can read and update the very same files: write company analyses, refresh risk scores, check rules. The app is open source; your data never leaves your machine.

## Core ideas

- **Files are the database.** Small, focused, human- and agent-readable files instead of a DB. Every change is a git diff.
- **Analysis lives next to the numbers.** Each company is a Markdown note with YAML frontmatter (sector, risk score, scenarios). Agents update the analysis and the numbers in one place.
- **Multiple accounts.** Tax-advantaged and regular accounts (e.g. Polish IKE, OKI, IKZE), plus bonds — viewed separately or together.
- **Your rules, checked automatically.** E.g. "max 20% in one company", "~50% in low-risk assets".
- **Local-first.** Runs on your computer, reads a directory you point it to.

## Data layout (lives in a separate, private repo)

```
my-portfolio/
├─ accounts.yaml            # accounts: IKE (XTB), OKI, bonds...
├─ rules.yaml               # portfolio rules (max position %, min low-risk %)
├─ transactions/
│  ├─ ike-xtb.jsonl         # one line = one transaction
│  └─ bonds-tos-2025-08.jsonl
├─ companies/               # one note per instrument (name configurable)
│  └─ PAS.md                # YAML frontmatter + free-form analysis
└─ .cache/                  # prices, FX — git-ignored
```

### `accounts.yaml`
```yaml
- id: ike-xtb
  name: IKE (XTB)
  type: brokerage          # brokerage | bonds
  wrapper: IKE             # IKE | IKZE | OKI | regular
  currency: PLN
- id: bonds-tos-2025-08
  name: TOS bonds 08.2025
  type: bonds
  wrapper: regular
  currency: PLN
```

### `transactions/*.jsonl`
```json
{"date":"2026-03-12","type":"buy","ticker":"PAS","qty":10,"price":120.00,"currency":"PLN","fee":0}
{"date":"2026-07-02","type":"dividend","ticker":"PAS","amount":25.00,"currency":"PLN"}
{"date":"2026-01-05","type":"deposit","amount":5000,"currency":"PLN"}
{"date":"2025-08-14","type":"bond_buy","series":"TOS0828","nominal":10000,"rate":5.0}
```
Types: `buy`, `sell`, `dividend`, `deposit`, `withdrawal`, `fee`, `bond_buy`, `bond_redeem`, `split`, `spin_off`. Fractional quantities supported.

### Company note frontmatter (`companies/PAS.md`)
```yaml
---
ticker: PAS
name: Passus
market: GPW              # GPW | NASDAQ | NYSE ...
currency: PLN
sector: IT / cybersecurity
risk: 3                  # 1 (very low) – 5 (very high)
scenarios:               # 3-year total return, %
  bear: -30
  base: 25
  bull: 70
probabilities: [0.25, 0.5, 0.25]
thesis: "Growing demand for cybersecurity"
updated: 2026-01-15
---
# PAS — Passus
(free-form analysis written by you or an agent)
```

## Features

### v1
- **Transactions & positions** — add buys/sells/dividends/deposits in a modal form; average price, P/L, weights computed automatically.
- **Multiple accounts** — filter by account or see everything combined (incl. fixed-rate bonds valued by formula).
- **Dashboard** — total value, P/L, cash, rule checks, value over time, allocation by company / sector.
- **Automatic prices** — current quotes + FX rates (NBP API) fetched by the local server and cached.
- **PL / EN** interface, **light / dark** theme (follows system by default).

### Later
- Risk & scenarios view: risk/return chart, expected portfolio return, scenario ranges.
- Rule checks with explanations ("ABC is 18.8% of IKE — limit 20%").
- Broker import (e.g. XTB xlsx export).
- CLI with JSON output for agents (`agentfolio positions --json`, `agentfolio check-rules`).
- Agent prompts/skills shipped with the repo (e.g. "update company note after quarterly report").
- Event calendar (earnings dates) read from notes.

## UI

Sidebar + dashboard layout:

```
┌───────────┬─────────────────────────────────────────┐
│ agentfolio│ Account: [All ▾]                PL|EN ◐ │
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

TypeScript everywhere, one repo, one command to run.

```
agentfolio/
├─ packages/core/     # domain: parsing files, positions, P/L, bonds, rules (pure, tested)
├─ apps/server/       # local HTTP server (Hono): reads/writes data dir, fetches prices
└─ apps/web/          # React + Vite + TypeScript, Recharts, i18n (PL/EN)
```

- **Server** is needed for two things: file access to the data directory and fetching quotes (browsers can't call quote providers directly because of CORS).
- **Core** is shared by the server, the future CLI and tests — so agents and the UI compute numbers the same way.
- **Dev environment:** Node 24; a Nix flake dev shell will be provided.

## Price sources (open question)

| Source | Status (2026-11) |
|---|---|
| NBP API (FX rates) | ✅ works, free, no key |
| stooq.pl CSV | ❌ now behind a JavaScript challenge, unusable from a server |
| others (to evaluate) | GPW quotes for small caps are the hard part — needs research |

Provider will be pluggable; manual price override always available.

## Roadmap

1. Repo scaffolding: workspace, lint, tests, Nix dev shell.
2. `core`: file formats + positions/P&L + bonds + tests.
3. `server`: data dir API + price provider + cache.
4. `web`: layout, dashboard, positions, transactions modal, accounts, i18n, themes.
5. Risk & scenarios view, rule checks.
6. CLI + agent integrations.

## Disclaimer

agentfolio is a personal tool, not investment advice. Risk scores and scenarios are opinions stored in your files — the app only displays and aggregates them.

## License

[MIT](LICENSE)
