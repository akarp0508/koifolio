# Original expectations

What koifolio set out to be, and where each promise stands. Kept so that nothing gets lost while we focus on the site. Update the status as things change, and revisit this when the focus moves on.

Last reviewed: 2026-11-06.

## Core promises

| Promise | Status |
|---|---|
| **Analysis lives next to the numbers.** Each company has notes plus structured fields, in one place with its figures | ✅ Delivered: company details, per-account plans |
| **Agents are first-class users.** An MCP server gives agents the same calculations as the UI, with every change checked and recorded | ⏸ Deferred on purpose: the site comes first. See [mcp.md](mcp.md) |
| **Your data is never locked in.** Plain-text export/import, optional git backup | ❌ Not built, so this isn't true yet. See [export-format.md](export-format.md) |
| **Multiple accounts,** viewed separately or together | ✅ Delivered |
| **Your rules, checked automatically,** like "max 20% in one company" or "~50% in low-risk" | ❌ Not built. The data it needs now exists |
| **Self-hosted and always on,** on a Raspberry Pi over Tailscale | ⏸ Deferred on purpose: runs on the desktop for now |
| **Universal:** any broker, exchange, country or tax system | 🟡 The model is universal, but prices and FX cover only what bankier and NBP do |

## Features from the original v1

| Feature | Status |
|---|---|
| Transactions, positions, P/L, weights | ✅ Done |
| Dashboard | ✅ Done, apart from rule status |
| Automatic prices, FX | ✅ Done, for the covered markets |
| Value history | ✅ Done, with backfill built in |
| Average and FIFO cost per account | ✅ Done |
| Bonds: one generic model, Polish series as presets | ❌ Not built |
| Tax estimates per account | ✅ Done, as an estimate on sales |
| Splits | ✅ Done; spin-offs aren't |
| Purchase-date FX, with currency gains separated | ✅ Done |
| Editing transactions without losing history | ❌ Edits are in place until the audit log exists |
| Manual prices | ❌ Not built |
| PL / EN interface | ❌ English only |
| Light / dark theme | ✅ Done |

## Later ideas from the original plan

Risk and scenarios view (bull, base and bear returns with probabilities), rule checks with explanations, broker import such as XTB exports, agent skills shipped with the repo, an earnings calendar from notes, and hosting for others with signup and OIDC.

## What changed from the original design

These were deliberate, and are recorded in [decisions.md](decisions.md):

- Spring Boot and Postgres instead of TypeScript with files as the database, so plain text became an export rather than the storage.
- One big v1 became small batches, each tested by hand.
- Overselling became a warning instead of an error, to allow backfill.
- Tags came back, as two independent kinds.

## Known risks while deployment is deferred

Accepted for now, and worth rechecking before relying on the app for real data:

- The repo has no remote, so the code exists only on the desktop.
- Data is in one Docker volume with no scheduled backup.
- CI has never run, and the arm64 image has never been tested on a Pi.
- Live prices and daily closes only update while the app runs, though closes fill in afterwards.
