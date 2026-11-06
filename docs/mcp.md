# MCP server

> **Deferred.** Nothing here is built yet. The site comes first; see [roadmap.md](roadmap.md).

Exposed by `server` at `/mcp`, protected by OAuth as the MCP spec defines for remote servers. Tools call the same services as the REST API, so agents and the UI get the same numbers and the same validation.

Over Tailscale, Claude Code on the desktop can reach it. claude.ai web and mobile can't, unless the server is exposed publicly.

## Permissions

Granted per MCP client, as OAuth scopes:

| Scope | Allows | Default |
|---|---|---|
| `portfolio:read` | All read tools | on |
| `analysis:write` | Profiles and notes | on |
| `transactions:write` | Adding transactions, marked unvalidated | on |
| `rules:write` | Changing rules | off |

## Tools

| Tool | Parameters | Scope |
|---|---|---|
| `get_portfolio` | `account?`, `as_of?` | read |
| `get_positions` | `account?`, `as_of?` | read |
| `list_accounts` | | read |
| `get_company` | `ticker` | read |
| `get_history` | `account?`, `from?`, `to?` | read |
| `check_rules` | `account?` | read |
| `update_company` | `ticker`, fields, `note?` | analysis:write |
| `add_transaction` | same fields as `POST /transactions` | transactions:write |
| `update_rules` | rule definition | rules:write |

Account descriptions are included in `list_accounts`, so agents can use the notes you keep there.

## Unvalidated transactions

Transactions added by an agent are marked unvalidated and listed in the "Unvalidated changes" tab. They count in all numbers straight away, and the UI marks them. You approve them, which clears the mark, or reject them, which soft-deletes them. Agent edits to analysis apply immediately.

A settings screen for choosing what MCP may do comes later. Until then, scopes are chosen when a client is authorized.

## Audit

Every write is logged with the client's name:

```
agent: claude-code — update PAS risk 3 → 4
```

## Build order

Read tools first, then write tools (roadmap step 4).
