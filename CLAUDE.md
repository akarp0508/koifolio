# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Scaffolding done (roadmap step 1); working towards v1. `README.md` is the spec (data model, export format, MCP tools, features, UI mockup, roadmap). Keep it in sync when design decisions change.

## Commands

Requires JDK 25, Maven and Node 24 installed locally (or use Docker for full builds).

- Build + all tests: `mvn -q -B verify`
- One module: `mvn -q -B -pl core test` (server tests need core: `-pl server -am`)
- Single test: `mvn -q -B -pl core test -Dtest=ClassName#method -Dsurefire.failIfNoSpecifiedTests=false`
- Run server: `mvn -q -pl server -am spring-boot:run` (port 8080)
- Web (in `web/`): `npm install`, `npm run dev` (Vite, proxies `/api` and `/actuator` to :8080), `npm run lint` (oxlint), `npm run build` (tsc + vite)
- Docker (on the Pi; Docker isn't installed on the dev machine): `cp .env.example .env`, then `docker compose up -d --build`. Multi-arch: `docker buildx build --platform linux/amd64,linux/arm64 -t koifolio .`

Spring Boot 4.1 (Boot 4 starter names, e.g. `spring-boot-starter-webmvc`). Base package `io.github.akarp0508.koifolio`. The Docker build copies `web/dist` into `server/src/main/resources/static` (git-ignored) so Spring Boot serves the UI.

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
