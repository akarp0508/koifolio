# MCP server

> **Read tools are built.** Writes come next, then OAuth when cloud clients need it. See [roadmap.md](roadmap.md).

Exposed by `server` at `/mcp` over stateless Streamable HTTP. Tools call the same services and return the same response records as the REST API, so agents and the UI get the same numbers and the same validation.

## Connecting

Create a token under **Agents** on your profile page, then give it to the agent as a bearer token:

```
claude mcp add --transport http koifolio http://<host>:8080/mcp --header "Authorization: Bearer <token>"
```

Tokens work only at `/mcp`, and website logins don't work there. Only a hash is stored, the secret is shown once, and a token can have a last day and be revoked at any time. Each agent should have its own, because the name will identify it in the audit log.

Any client that can send a header can connect from anywhere that can reach the server. Over plain HTTP the token crosses the network unencrypted, so outside your own machine use a trusted network, Tailscale or HTTPS. claude.ai web and mobile need OAuth and a publicly reachable server, so they can't connect yet.

Tools only see an `AgentCaller`, a user and a name. OAuth can be added as another way to produce one, without changing them.

## Permissions

Planned with the write tools. Tokens have no scopes yet, because everything available reads. Granted per client:

| Scope | Allows | Default |
|---|---|---|
| `portfolio:read` | All read tools | on |
| `analysis:write` | Profiles and notes | on |
| `transactions:write` | Adding transactions, marked unvalidated | on |
| `rules:write` | Changing rules | off |

## Tools

| Tool | Parameters | Scope |
|---|---|---|
| `list_accounts` | | read |
| `get_portfolio` | `account?` | read |
| `get_positions` | `account?`, `closed?` | read |
| `get_history` | `account?`, `from?`, `to?` | read |
| `get_plan` | `account`, `ticker` | read |
| `get_company` | `ticker` | read |
| `list_transactions` | `account?`, `from?`, `to?` | read |
| `check_rules` | | read |
| `get_tax` | `year?` | read |
| `update_company` (planned) | `ticker`, fields, `note?` | analysis:write |
| `add_transaction` (planned) | same fields as `POST /transactions` | transactions:write |
| `update_rules` (planned) | rule definition | rules:write |

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

Read tools and tokens, then `add_transaction` with the audit log, unvalidated marks and the review tab, then company and rule edits.
