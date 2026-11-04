# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Planning stage: no code, build, lint or test setup exists yet. `README.md` is the spec (data model, export format, MCP tools, features, UI mockup, roadmap). Keep it in sync when design decisions change. When scaffolding adds commands (Maven, web, Nix dev shell, Docker), record them here.

## What it is

koifolio is a self-hosted portfolio tracker (GPW-focused, not GPW-only). It currently runs for a single user on a Raspberry Pi 5 (8 GB, arm64) over Tailscale, but it is designed multi-user from day one so it can later be hosted for others. Never commit personal portfolio data to this repo.

## Planned architecture

Maven multi-module, Java 25, Spring Boot + Postgres, React frontend, deployed as one Docker Compose stack (app + Postgres, multi-arch image).

- `core/`: pure Java domain logic (positions, average/FIFO cost basis, P/L, tax, bond valuation, rules, plain-text export format). It's the single source of truth for calculations, used by the REST API, the MCP tools and export alike. No Spring, no I/O; unit-tested.
- `server/`: Spring Boot.
  - REST API with an OpenAPI spec, plus an MCP server for agents (Spring AI).
  - Spring Security: local accounts, plus an OAuth2 authorization server for MCP clients.
  - Spring Data + Flyway on Postgres.
  - Price/FX/CPI providers and a scheduler for daily snapshots and backfill.
  - Serves the built `web` app.
- `web/`: React + Vite + TypeScript + Recharts, PL/EN, light/dark. Display and editing only, with no calculations. API types are generated from the OpenAPI spec.

Key constraints:
- Every table row is owned by a user. Money is `numeric`/`BigDecimal`, never floating point.
- Transactions are never destroyed (edits create revisions, deletes are soft). Every change (UI or agent) goes to the audit log with its author.
- Postgres is the source of truth. Plain text (YAML/JSONL/Markdown, format in README) is export/import plus an optional one-way git backup, never a live sync.
- Must run on arm64 with modest resources: no headless browser. Price providers are pluggable (free APIs or scraping, no paid keys) with a manual override. Current source findings and the provider chain are in the README "Price sources" section; respect robots.txt.

## Conventions

- Commit style: `<Area>: <short description>` (e.g. `Docs: add README`).
- UI/look-and-feel changes: agree on a mockup/description with the user before implementing.
