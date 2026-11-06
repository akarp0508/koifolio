# MCP server

> **Built, apart from OAuth,** which waits until cloud clients need it. See [roadmap.md](roadmap.md).

Exposed by `server` at `/mcp` over stateless Streamable HTTP. Tools call the same services and return the same response records as the REST API, so agents and the UI get the same numbers and the same validation.

## Connecting

Create a token under **Agents** on your profile page, then give it to the agent as a bearer token:

```
claude mcp add --transport http koifolio http://<host>:8080/mcp --header "Authorization: Bearer <token>"
```

Tokens work only at `/mcp`, and website logins don't work there. Only a hash is stored, the secret is shown once, and a token can have a last day and be revoked at any time. Each agent should have its own, because the name identifies it in the change log.

Any client that can send a header can connect from anywhere that can reach the server. Over plain HTTP the token crosses the network unencrypted, so outside your own machine use a trusted network, Tailscale or HTTPS. claude.ai web and mobile need OAuth and a publicly reachable server, so they can't connect yet.

Tools only see an `AgentCaller`, a user and a name. OAuth can be added as another way to produce one, without changing them.

## Permissions

Set per token when it's created. Every token can read, and tokens created before scopes existed stay read-only.

| Scope | Allows | Default |
|---|---|---|
| `analysis:write` | Company details and analysis | on |
| `transactions:write` | Adding transactions, marked unvalidated | on |
| `rules:write` | Changing rules | off |

A token without the scope gets a tool error. Every tool still shows in the list.

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
| `update_company` | `ticker`, `name?`, `sector?`, `risk?`, `thesis?`, `analysis?`, `tags?` | analysis:write |
| `add_transaction` | `account`, `type`, `date`, `affectsBalance`, then the fields of `POST /transactions` | transactions:write |
| `save_rule` | `type`, `limitPercent`, `id?`, `account?`, `maxRisk?` | rules:write |
| `delete_rule` | `id` | rules:write |

Money and other decimals are sent as text, as in the REST API, so they never pass through a floating-point number. `update_company` changes only the fields it's given, and adds the company if it's new. Agents can't edit or delete transactions: a wrong one is rejected and entered again. They can't change plans or accounts.

Account descriptions are included in `list_accounts`, so agents can use the notes you keep there.

## Unvalidated transactions

Transactions added by an agent are marked unvalidated and listed on the Review page, with a line in Needs attention. They count in all numbers straight away, and Activity marks them. You approve them, which clears the mark, or reject them, which soft-deletes them and undoes their effect on the balance. Agent edits to companies and rules apply immediately.

## Change log

Changes to transactions, companies and rules are logged with who made them, you or the agent by name, and the values before and after. They show on the Review page and at `GET /audit-log`. An entry is written in the same database transaction as the change, so a refused change leaves none. Accounts, plans and settings aren't logged, and neither is what a restore replaces.

## Build order

Read tools and tokens came first, then the write tools with the change log, marks and review. OAuth is next, when something needs it.
