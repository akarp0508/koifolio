# Plain-text export format

The export is a zip of small files that people and agents can read. Your profile page, opened by clicking your username, has **Download export**, and **Restore** to load one back, replacing all your data. There's no live sync, and no merge.

```
koifolio-export-2026-11-06.zip
├─ koifolio.yaml            # format version and settings
├─ accounts.yaml
├─ plans.yaml
├─ rules.yaml
├─ transactions/
│  └─ ike-xtb.jsonl         # one line = one transaction, one file per account
└─ companies/
   └─ PAS.md                # YAML frontmatter, then the Markdown analysis
```

It holds everything you entered, but no password and no shared market data: prices, closes and FX rates are fetched again. Numbers are written in full, so they don't lose precision.

## `koifolio.yaml`

```yaml
format: 1
exportedAt: "2026-11-06T14:00:00Z"
baseCurrency: "PLN"
costFxRule: "previous_publication"   # or transaction_date
```

A file in another format is refused.

## `accounts.yaml`

```yaml
- key: "ike-xtb"               # names the transactions file, and links plans and rules
  name: "IKE (XTB)"
  description: "Long-term account"
  currency: "PLN"
  balance: 1000.5              # as it stood, not rebuilt from transactions
  balanceSetOn: "2026-01-05"   # optional
  costMethod: "fifo"           # average | fifo, defaults to average
  taxRate: 19                  # percent, left out when unset
  createdAt: "2026-01-05T10:00:00Z"   # optional
```

## `transactions/<account key>.jsonl`

```json
{"date":"2026-01-05","type":"deposit","amount":5000,"affectsBalance":true}
{"date":"2026-03-12","type":"buy","ticker":"PAS","quantity":10,"price":120,"fee":3.15,"affectsBalance":true}
{"date":"2026-07-02","type":"dividend","ticker":"PAS","amount":25,"withheldTax":4.75,"affectsBalance":true}
{"date":"2026-08-01","type":"split","ticker":"PAS","splitFrom":1,"splitTo":10,"affectsBalance":false}
```

- Types are `deposit`, `withdrawal`, `buy`, `sell`, `dividend`, `fee` and `split`.
- Amounts are in the account's currency.
- `createdAt` is optional and orders trades on the same day. Without it, lines keep their order in the file.
- `createdBy` names the agent that added a line, and `unvalidated` is `true` while it waits for review. Both are optional, and restore keeps them.
- Deleted and rejected transactions aren't exported. Neither are agent tokens, the change log or changes waiting for review, and a restore drops those waiting.
- Each line is checked like a new transaction, and an error names the file and line.
- `affectsBalance` doesn't move the balance on restore, because `accounts.yaml` already has it.

## `companies/<ticker>.md`

```markdown
---
ticker: "PAS"
name: "Passus"
sector: "IT"
risk: 3                # 1 (very low) to 5 (very high)
thesis: "Growing demand for cybersecurity"
tags: ["core"]
listed: true           # false: removed from your list, still on transactions
---
# Passus
Your analysis, kept exactly as written.
```

## `plans.yaml`

```yaml
- account: "ike-xtb"
  ticker: "PAS"
  plan: "Hold through the next report"
  risk: 2
  targetPrice: 180.5
  stopPrice: 90
  tags: ["long"]
```

## `rules.yaml`

```yaml
- type: "max_position_weight"
  limitPercent: 20
- type: "min_low_risk_weight"
  account: "ike-xtb"           # left out to cover every account
  limitPercent: 50
  maxRisk: 2
```
