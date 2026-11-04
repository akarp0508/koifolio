# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Planning stage: no code, build, lint or test setup exists yet. `README.md` is the spec (data formats, features, UI mockup, roadmap). Keep it in sync when design decisions change. When scaffolding adds commands (workspace, lint, tests, Nix dev shell), record them here.

## What it is

koifolio is a local-first portfolio tracker (GPW-focused, not GPW-only). The user's portfolio data lives in a **separate private repo** of plain files. This repo is only the app. Never commit sample personal data here.

- **Files are the database:** `accounts.yaml`, `rules.yaml`, `transactions/<account-id>.jsonl` (one transaction per line), `companies/<TICKER>.md` (YAML frontmatter + free-form analysis), `.cache/` (prices/FX, git-ignored). The README has the full schemas.
- AI agents and the web UI edit the same files. Writes must keep files human-readable and produce small, clean git diffs (e.g. append JSONL lines, preserve the Markdown body when updating frontmatter).

## Planned architecture

TypeScript monorepo, Node 24, one command to run:

- `packages/core/`: pure domain logic (file parsing, positions, avg price, P/L, bond valuation by formula, rule checks). This is the single source of truth for calculations, shared by server, future CLI and tests, so the UI and agents always get the same numbers. Keep it free of I/O side effects, and test it.
- `apps/server/`: Hono local HTTP server. It reads/writes the data dir and fetches quotes and FX (NBP API). It exists because browsers can't reach quote providers (CORS). Price providers are pluggable, and a manual price override is always available.
- `apps/web/`: React + Vite + Recharts, PL/EN i18n, light/dark theme (follows system by default).

Price sources are an open question: stooq.pl is unusable (JS challenge), and GPW small-cap quotes need research.

## Conventions

- Commit style: `<Area>: <short description>` (e.g. `Docs: add README`).
- UI/look-and-feel changes: agree on a mockup/description with the user before implementing.
