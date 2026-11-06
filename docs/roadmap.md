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
11. **Portfolio chart** (done): value over time, 1D to 1Y changes, gain and loss colours, and FX history.
12. **Splits** (done): split transactions, with past values corrected, and detected splits suggested on the dashboard.
13. **Purchase-date FX** (done): cost and realized gains at each trade's rate, currency gain shown separately, and the rate date as a setting.
14. **Tax estimates** (done): a rate per account, a yearly estimate page and a dashboard tile.
15. **Weights** (done): each position's share of its account and of everything, cash included.
16. **Portfolio page** (done): Dashboard and Positions merged, holdings grouped by company, and Activity replacing Transactions.

## Current focus: extending the site

We're building a website only for now. Deployment and MCP wait until later, and [expectations.md](expectations.md) keeps track of what they were meant to be.

Rough priorities, to be picked one batch at a time:

**Numbers you can trust**

- **Spin-offs:** moving part of the cost to a new instrument

**Promised features**

- **Rules,** pass/fail on the dashboard, such as "max 20% in one company" and "~50% in low-risk"
- **Plain-text export,** then import

**Broader coverage**

- **Broker import,** which backfill really needs
- **Bonds:** the largest single item, so it gets a batch of its own
- **Manual prices,** for what the source doesn't cover
- **PL / EN** interface
- Mobile polish

## Deferred, on purpose

Not being worked on. Pick these up when the site is where it should be.

- **Deployment:** private remote and CI, the Pi over Tailscale, scheduled backups with a restore test
- **MCP server and audit log.** Agents writing company analysis builds on the instrument fields. Revisions and soft delete for transactions come with the audit log
- **Hosting for others:** rate limiting on login and registration (required before exposing the app publicly), signup controls, passkeys, GDPR

## Ideas for later

Risk and scenarios view, rule checks with explanations, agent skills, an earnings calendar.
