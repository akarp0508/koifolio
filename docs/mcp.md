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
| `analysis:write` | Proposing company changes | on |
| `transactions:write` | Adding transactions, marked unvalidated, and proposing edits and deletions | on |
| `accounts:write` | Proposing new accounts and account edits | on |
| `rules:write` | Proposing rule changes | off |

Scopes can't be changed later, so an older token needs replacing to get a new one. A token without the scope gets a tool error. Every tool still shows in the list.

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
| `list_pending_changes` | | read |
| `list_bonds` | | read |
| `update_company` | `ticker`, `name?`, `sector?`, `risk?`, `thesis?`, `analysis?`, `tags?` | analysis:write |
| `add_transaction` | `account`, `type`, `date`, `affectsBalance`, then the fields of `POST /transactions` | transactions:write |
| `add_transactions` | `transactions`, a list of up to 1000 rows with the fields of `add_transaction` | transactions:write |
| `update_transaction` | `id`, then any fields of `add_transaction` | transactions:write |
| `delete_transaction` | `id` | transactions:write |
| `create_account` | `name`, `currency`, `balance?`, `description?`, `costMethod?`, `taxRate?` | accounts:write |
| `update_account` | `id`, `name?`, `description?`, `balance?`, `costMethod?`, `taxRate?`, `clearTaxRate?` | accounts:write |
| `add_bond` | `series`, `account`, `quantity`, `purchaseDate`, `rates`, `affectsBalance`, `earlyFee?` | transactions:write |
| `save_rule` | `type`, `limitPercent`, `id?`, `account?`, `maxRisk?` | rules:write |
| `delete_rule` | `id` | rules:write |

Money and other decimals are sent as text, as in the REST API, so they never pass through a floating-point number. `update_company`, `update_transaction` and `update_account` propose only the fields they're given, and `update_company` adds the company if it's new. Agents can't change plans or delete accounts. `note` holds the reason for a transaction, and empty text in `update_transaction` removes it.

`add_transactions` adds all its rows or none. It checks every row, in order, and if any is refused nothing is added and `rows` says what's wrong with each.

Account descriptions are included in `list_accounts`, so agents can use the notes you keep there.

## Review

Agents can't change anything you can't see and undo.

| What the agent does | When it applies | How you undo it |
|---|---|---|
| `add_transaction`, `add_transactions` | At once, marked unvalidated | Reject it, or delete it from Activity once approved |
| `update_company`, `update_transaction`, `delete_transaction`, `create_account`, `update_account`, `add_bond`, `save_rule`, `delete_rule` | Only when you approve it | Reject it, or Revert it from the change log once approved |

**Approve all** and **Reject all** cover both, for one agent or everyone, or for one `add_transactions` call, which shows as an import. Approving a company, transaction or account edit applies its fields over whatever is there by then, so edits made in the meantime survive. A transaction edit can take the balance below zero once approved, and approving an account's balance sets it to that amount. Reverting a balance edit takes back only that edit, so transactions since then still count. Revert puts back everything from before the change, in full, and warns first if the same thing has changed since, because those later changes would go too. An approved account can only be reverted while it has no transactions, because deleting it erases them.

### Unvalidated transactions

Transactions added by an agent are marked unvalidated and listed on the Review page, with a line in Needs attention. They count in all numbers straight away, and Activity marks them. You approve them, which clears the mark, or reject them, which soft-deletes them and undoes their effect on the balance.

## Change log

Changes to transactions, companies, rules, accounts and plans are logged with who made them, you or the agent by name, and the values before and after. They show on the Review page and at `GET /audit-log`. An entry is written in the same database transaction as the change, so a refused change leaves none, and an edit that changes nothing leaves none either. Proposals and your decisions are logged too. Settings, tokens and restores aren't.

## Build order

Read tools and tokens came first, then the write tools with the change log, marks and review. OAuth is next, when something needs it.
