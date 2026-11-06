# Plain-text export format

> **Planned.** Nothing here is built yet, and the examples still need updating to the current model.

The export (and the optional git backup) is a directory of small, human- and agent-readable files. Import reads the same format, on demand (replace or merge). There is no live two-way sync.

```
my-portfolio/
├─ accounts.yaml
├─ rules.yaml
├─ bonds.yaml
├─ transactions/
│  ├─ ike-xtb.jsonl         # one line = one transaction
│  └─ bonds-tos-2025-08.jsonl
├─ companies/
│  └─ PAS.md                # YAML frontmatter + free-form analysis
└─ history/
   └─ snapshots.jsonl
```

## `accounts.yaml`
```yaml
- id: ike-xtb
  name: IKE (XTB)
  description: Long-term retirement account, held until 60.   # free text
  tax:
    method: average        # average | fifo
    rate: 0
- id: bonds-tos-2025-08
  name: TOS bonds 08.2025
  description: Treasury bonds bought at PKO.
  tax:
    method: fifo
    rate: 0.19
```

## `transactions/*.jsonl`
```json
{"date":"2026-03-12","type":"buy","ticker":"PAS","qty":10,"price":120.00,"currency":"PLN","fee":0}
{"date":"2026-07-02","type":"dividend","ticker":"PAS","amount":25.00,"currency":"PLN"}
{"date":"2026-01-05","type":"deposit","amount":5000,"currency":"PLN"}
{"date":"2025-08-14","type":"bond_buy","series":"TOS0828","nominal":10000,"rate":5.0}
```

## `companies/PAS.md`
```yaml
---
ticker: PAS
name: Passus
market: GPW              # GPW | NASDAQ | NYSE ...
currency: PLN
sector: IT / cybersecurity
risk: 3                  # 1 (very low) – 5 (very high)
scenarios:               # 3-year total return, %
  bear: -30
  base: 25
  bull: 70
probabilities: [0.25, 0.5, 0.25]
thesis: "Growing demand for cybersecurity"
updated: 2026-01-15
---
# PAS — Passus
(free-form analysis written by you or an agent)
```
