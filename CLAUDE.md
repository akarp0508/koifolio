# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Scaffolding done (roadmap step 1); working towards v1. `docs/` holds the plans (see `docs/README.md`): domain model, database, REST API, MCP, export format, price sources, roadmap, and `docs/decisions.md` for settled and open decisions. Read the relevant file before implementing, don't treat **OPEN** items as decided, and keep them in sync when decisions change.

## Commands

Requires JDK 25, Maven and Node 24 installed locally (or use Docker for full builds).

Server commands run in `server/`:

- Build + all tests: `mvn -q -B verify`
- Single test: `mvn -q -B test -Dtest=ClassName#method`
- Run server: `mvn -q spring-boot:run` (port 8080)

Other:

- Web (in `web/`): `npm install`, `npm run dev` (Vite, proxies `/api` and `/actuator` to :8080), `npm run lint` (oxlint), `npm run build` (tsc + vite)
- Docker: `cp .env.example .env`, then `docker compose up -d --build`. Multi-arch: `docker buildx build --platform linux/amd64,linux/arm64 -t koifolio .`

Spring Boot 4.1 (Boot 4 starter names, e.g. `spring-boot-starter-webmvc`). Base package `io.github.akarp0508.koifolio`. The Docker build copies `web/dist` into `server/src/main/resources/static` (git-ignored) so Spring Boot serves the UI.

## What it is

koifolio is a self-hosted portfolio tracker (universal: not tied to any broker, exchange, country or tax system; Polish specifics and GPW are presets and providers on top of a generic model). It currently runs for a single user on a Raspberry Pi 5 (8 GB, arm64) over Tailscale, but it is designed multi-user from day one so it can later be hosted for others. Never commit personal portfolio data to this repo.

## Planned architecture

Java 25, Spring Boot + Postgres (Maven project in `server/`), React frontend (`web/`), deployed as one Docker Compose stack (app + Postgres, multi-arch image).

- `server/`: Spring Boot.
  - `domain` package: pure domain logic (positions, average/FIFO cost basis, P/L, tax, bond valuation, rules). It's the single source of truth for calculations, used by the REST API, the MCP tools and export alike. No Spring imports and no I/O in this package (only a convention, nothing enforces it), and it's unit-tested without a Spring context.
  - REST API with an OpenAPI spec, plus an MCP server for agents (Spring AI).
  - Spring Security: local accounts, plus an OAuth2 authorization server for MCP clients.
  - Spring Data + Flyway on Postgres.
  - Price/FX/CPI providers and a scheduler for daily snapshots and backfill.
  - Serves the built `web` app.
- `web/`: React + Vite + TypeScript + Recharts, PL/EN, light/dark. Display and editing only, with no calculations. API types are generated from the OpenAPI spec.

Key constraints:
- All user data is scoped by `user_id`; market data is shared. Money is `numeric`/`BigDecimal`, never floating point.
- Transactions are never destroyed (edits create revisions, deletes are soft). Agent transactions count immediately but stay marked unvalidated until approved. Every change (UI or agent) goes to the audit log with its author.
- Postgres is the source of truth. Plain text (YAML/JSONL/Markdown, format in `docs/export-format.md`) is export/import plus an optional one-way git backup, never a live sync.
- Must run on arm64 with modest resources: no headless browser. Price providers are pluggable (free APIs or scraping, no paid keys) with a manual override. Current source findings and the provider chain are in `docs/price-sources.md`; respect robots.txt.

## Conventions

- Commit style: `<Area>: <short description>` (e.g. `Docs: add README`).
- UI/look-and-feel changes: agree on a mockup/description with the user before implementing.
