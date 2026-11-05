# koifolio

A self-hosted, AI-agent-friendly portfolio tracker for individual investors — built around the Warsaw Stock Exchange (GPW), but not limited to it.

> **Status:** planning. Nothing is implemented yet — this README is the plan.

## Why

Brokerage apps show *what* you hold. They don't help you keep track of *why* you hold it, how risky it is, or whether your portfolio still follows your own rules.

koifolio keeps your transactions, accounts and company analyses in one place. You use it through a web UI, and AI agents (Claude Code and others) work with the same data through an MCP server: they write company analyses, refresh risk scores and check rules. Everything is exportable to plain text (Markdown, YAML, JSONL) at any time, optionally backed up to your own git repo. The app is open source and self-hosted.

## Core ideas

- **Analysis lives next to the numbers.** Each company has a Markdown note plus structured fields (sector, risk score, scenarios). Agents update the analysis and the numbers in one place.
- **Agents are first-class users.** A built-in MCP server exposes the same calculations the UI shows. Every agent change is validated and recorded in the audit log.
- **Your data is never locked in.** Plain-text export/import in documented formats, plus an optional scheduled git backup.
- **Multiple accounts.** Tax-advantaged and regular accounts (e.g. Polish IKE, OKI, IKZE), plus bonds — viewed separately or together.
- **Your rules, checked automatically.** E.g. "max 20% in one company", "~50% in low-risk assets".
- **Self-hosted, always on.** One Docker Compose stack (app + Postgres), light enough for a Raspberry Pi 5. Built multi-user from day one, even if you're the only user.

## Data model

Postgres is the source of truth. Every row belongs to a user. Money is `numeric` / `BigDecimal`, never floating point.

- **Accounts:** name, free-form description, tax settings. An account owns its lots and cash, and can hold any instrument, bonds included.
- **Transactions:** never destroyed. Editing creates a new revision that supersedes the old one, and deleting is a soft delete; both are recorded in the audit log. Types: `buy`, `sell`, `dividend`, `deposit`, `withdrawal`, `fee`, `bond_buy`, `bond_redeem`, `split`, `spin_off`. Fractional quantities supported. Full lot detail is kept, so any cost-basis method can be computed from them.
- **Instruments & company notes:** ticker, market, currency, sector, risk (1–5), scenarios with probabilities, thesis, plus a free-form Markdown analysis.
- **Bonds:** the generic bond model below.
- **Rules:** portfolio constraints checked against positions.
- **Snapshots:** daily portfolio values for the history chart.
- **Audit log:** who made each change (UI user or which agent), when, and the before/after values.

### Cost basis & tax
Every account has its own tax settings: method `average` or `fifo` (Polish tax rules use FIFO) and a tax rate. There is no fixed list of account types, so any account can be modelled. The UI offers presets that fill in these fields (e.g. regular: FIFO, 19% Belka; IKE: 0%). P/L is shown pre-tax and, optionally, after estimated tax.

### Currency
Totals are reported in a configurable base currency (default PLN). Values use the current NBP rate. Costs use the rate from the transaction date (D-1 NBP mid rate, as Polish tax rules require).

### Bonds
One generic model, so any bond can be valued if its parameters are given:
```yaml
series: EDO0136
nominal: 100
term_months: 120
coupon:
  type: indexed           # fixed | indexed (CPI + margin) | floating (reference rate + margin)
  first_period_rate: 5.6  # %
  margin: 2.0
interest: capitalized     # capitalized | paid
period_months: 12
early_redemption_fee: 2.0 # per bond
```
Polish retail series (OTS, ROR, DOR, TOS, COI, EDO, ROS, ROD) ship as presets. CPI (GUS) and reference rates (NBP) are fetched and cached, with manual override.

## Plain-text export format

The export (and the optional git backup) is a directory of small, human- and agent-readable files. Import reads the same format, on demand (replace or merge). There is no live two-way sync.

```
my-portfolio/
├─ accounts.yaml
├─ rules.yaml
├─ bonds.yaml
├─ transactions/
│  ├─ ike-xtb.jsonl         # one line = one transaction
│  └─ bonds-tos-2025-08.jsonl
├─ companies/
│  └─ PAS.md                # YAML frontmatter + free-form analysis
└─ history/
   └─ snapshots.jsonl
```

### `accounts.yaml`
```yaml
- id: ike-xtb
  name: IKE (XTB)
  description: Long-term retirement account, held until 60.   # free text
  tax:
    method: average        # average | fifo
    rate: 0
- id: bonds-tos-2025-08
  name: TOS bonds 08.2025
  description: Treasury bonds bought at PKO.
  tax:
    method: fifo
    rate: 0.19
```

### `transactions/*.jsonl`
```json
{"date":"2026-03-12","type":"buy","ticker":"PAS","qty":10,"price":120.00,"currency":"PLN","fee":0}
{"date":"2026-07-02","type":"dividend","ticker":"PAS","amount":25.00,"currency":"PLN"}
{"date":"2026-01-05","type":"deposit","amount":5000,"currency":"PLN"}
{"date":"2025-08-14","type":"bond_buy","series":"TOS0828","nominal":10000,"rate":5.0}
```

### `companies/PAS.md`
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

## Agents (MCP)

The server exposes an MCP endpoint (OAuth-protected, as the MCP spec defines for remote servers). Planned tools:

- read: `get_portfolio`, `get_positions`, `get_company`, `check_rules`, `get_history`
- write: `update_company` (fields + note), `add_transaction`, `update_rules`

Write tools are validated by the same domain code as the UI and attributed in the audit log (`agent: claude-code — update PAS risk 3 → 4`). Agents can be given read-only access, or be allowed to write analysis only.

## Features

### v1
- **Transactions & positions** — add, edit and delete buys/sells/dividends/deposits in a modal form; average price, P/L, weights computed automatically.
- **Multiple accounts** — filter by account or see everything combined, incl. bonds valued by the generic bond model (all Polish series).
- **Cost basis & tax** — average/FIFO and tax rate per account, with presets.
- **Dashboard** — total value, P/L, cash, rule status (pass/fail), value over time (from daily snapshots), allocation by company / sector.
- **Prices** — quotes, FX (NBP), CPI and reference rates fetched on a schedule and cached; manual price override in the UI.
- **Company notes** — edit structured fields and the Markdown analysis in the UI.
- **Rules** — define rules, see pass/fail.
- **MCP server** for agents, with audit log.
- **Login** — local user accounts (first user created on first run), password.
- **PL / EN** interface, **light / dark** theme (follows system by default).

### v1.1
- **Backfill** — fill history gaps (downtime, the period before koifolio existed) from transactions + historical closing prices where the provider has them.
- **Plain-text export/import** in the format above.
- **Git backup** — scheduled, one-way, pushes the export to a repo you configure.
- **Passkeys.**

### Later
- Risk & scenarios view: risk/return chart, expected portfolio return, scenario ranges.
- Rule checks with explanations ("ABC is 18.8% of IKE — limit 20%").
- Broker import (e.g. XTB xlsx export).
- Agent skills shipped with the repo (e.g. "update company note after quarterly report").
- Event calendar (earnings dates) read from notes.
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
├─ pom.xml            # Maven multi-module parent
├─ core/              # domain: positions, cost basis, P/L, bonds, rules, export format (pure Java, no Spring)
├─ server/            # Spring Boot: REST API, MCP server, auth, persistence, price providers, scheduler
└─ web/               # React + Vite + TypeScript, Recharts, i18n (PL/EN)
```

- **core** holds all calculations, so the UI, MCP tools and export produce the same numbers. It has no I/O and is unit-tested.
- **server:** Spring Boot, Java 25.
  - Spring Security (sessions, password then passkeys, OAuth2 authorization server for MCP clients)
  - Spring Data + Flyway on Postgres
  - Spring AI MCP server
  - jsoup for scraping, JGit for the git backup
- **web** only displays and edits data through the REST API; no calculations. TypeScript types are generated from the server's OpenAPI spec.
- **Deployment:** a multi-arch Docker image (`linux/arm64` for Raspberry Pi 5, plus `amd64`). A multi-stage build compiles `web` and serves it from Spring Boot. Compose runs app + Postgres. Reached over Tailscale for now; public exposure later is a config change, since auth already exists. No headless browser.
- **Dev environment:** JDK 25, Maven, Node 24 (for `web`). In dev, Vite proxies API calls to Spring Boot.

## Price sources

Acceptable: free APIs and scraping of public pages (no paid keys). Must work on a Raspberry Pi without a headless browser. Findings below were tested on 2026-11-04. None of the sources are official APIs, so every provider must fail gracefully.

### Current quotes
| Source | Status |
|---|---|
| bankier `api.bankier.pl/quotes/public/instrument-header/?symbol=PKOBP,XTB,…` | ✅ JSON, batched, delayed ~15 min. Covers GPW main market, NewConnect, ETFs and foreign stocks (e.g. `APPLE` → AAPL). Uses bankier symbols (`PKOBP`, not `PKO`); map them with `instrument-search/?bankier=true&query=…`. Undocumented internal endpoint. |
| biznesradar `/notowania/<TICKER>` | ✅ HTML, price in `itemprop="price"`. Allowed by robots.txt. Fallback. |
| NBP API (FX, reference rates) | ✅ official, free, no key |

### Historical closes (for backfill)
| Source | Status |
|---|---|
| bankier `www.bankier.pl/new-charts/get-data?symbol=…&type=ohlc&max_period=true` | ⚠️ JSON OHLC from listing date, but **frozen: data ends 2026-01-02** |
| bankier `api.bankier.pl/quotes/public/investor-chart/` | ❓ the new chart API; parameters not figured out yet |
| biznesradar `/notowania-historyczne/<TICKER>` | ⚠️ HTML OHLC + volume, ~50 sessions per page. robots.txt disallows pages 2+, so only the latest page is used |
| EODHD (`.WAR`) | ❓ free tier with a key; limits not verified |

### Not usable
| Source | Status |
|---|---|
| stooq.pl CSV | ❌ behind a JavaScript challenge |
| Yahoo Finance (`.WA`) | ❌ HTTP 429 without cookie/crumb |
| gpw.pl / newconnect.pl archive | ❌ connection reset by bot protection |

### Provider chain
- **Current:** bankier `instrument-header` → biznesradar → manual override.
- **History:** bankier legacy chart (until 2026-01-02) + biznesradar latest page + our own daily snapshots. Until `investor-chart` or another source fills the 2026 gap, backfill (v1.1) is incomplete for that period.
- **Cache:** every cached price stores its source and fetch time.

## Roadmap

1. Scaffolding: Maven multi-module, web app, Dockerfile + compose, CI.
2. `core`: domain model, positions/cost basis/P&L, bonds, rules + tests.
3. `server`: persistence + migrations, audit log, login, REST API, price providers + cache + manual override, scheduler (snapshots).
4. MCP server (read tools first, then write tools).
5. `web`: layout, dashboard, positions, transactions modal, accounts, company notes, rules, i18n, themes. → **v1**
6. Backfill, export/import, git backup, passkeys. → **v1.1**
7. Risk & scenarios view, rule checks with explanations.

## Disclaimer

koifolio is a personal tool, not investment advice. Risk scores and scenarios are opinions you store — the app only displays and aggregates them.

## License

[GPL-3.0-or-later](LICENSE)
