# Domain model (`domain` package)

Plain Java with no Spring, no I/O and no users. Services load data and pass it in, so all of this can be tested with inputs and expected numbers. Everything else in the app holds rules specific to storing or serving data.

## Implemented

| Class | Does |
|---|---|
| `PositionCalculator` | Adds up one instrument's trades in one account: quantity, cost, realized gain |
| `CostMethod` | `AVERAGE` or `FIFO`. Average is FIFO with a single pooled lot, so both share the selling code |
| `Valuation` | Value, gain and gain % of a position at a price |
| `PositionHistory` | A position's value on each day there's a close |
| `PortfolioHistory` | Adds positions up over time, and works out 1D to 1Y changes |
| `PlanCheck` | How a price sits against a plan's target and stop |
| `Breakdown` | Splits a total by label, for sector and risk |
| `SplitDetector` | Spots a split from stored closes changing by one ratio |
| `TaxEstimate` | Groups accounts by rate, and splits realized gains by year |
| `Weights` | Each value's percent of a total that includes cash |
| `CompanyTotal` | Adds up one company across accounts |

## Calculation rules

**Cost.** Buy fees raise cost, and sell fees reduce proceeds. In the account's currency it's as entered. In the base currency, each trade converts at its own rate first, then the same calculator runs, so lots, FIFO, fees and splits all follow. `CostFxRule` picks the rate's date.

**Splits.** A split multiplies every lot's quantity and leaves its cost alone, so FIFO lots stay apart. Closes are split-adjusted by the source, so `PositionHistory` scales a close up by every split dated after it, giving what the shares really traded at.

**Oversold sales.** A sale past the shares on record counts only the shares with a known cost, and the position is marked incomplete. Invalid history never blocks: backfill can arrive in any order.

**Order.** Trades replay by date, then by when they were entered.

**Period changes.** A change is how much the gain moved, where gain is value − cost + realized, so deposits, buys and sells aren't profit.

**Currency.** Prices in another currency convert to the account's. Value converts to the user's base currency at the latest rate, and history at each day's rate. Currency gain is cost at today's rate minus cost at purchase rates. NBP quotes against PLN, so other pairs are cross rates through PLN.

**Tax.** Accounts with the same rate add up per calendar year, and a net loss owes nothing. "If sold today" is the tax with open gains added, minus the tax without them.

**Plan signals.** Near means within 5% of a level, and the stop wins if both apply.

## Planned

**More transaction types.** Today's types are deposit, withdrawal, buy, sell, dividend, fee and split. Still to come: `SpinOff` (moves a fraction of the cost to the new instrument), `Interest`, `CurrencyExchange`, `BondBuy` and `BondRedeem`.

**Bonds.** One generic model, so any bond works given its parameters: nominal, term, period, a fixed, indexed (CPI + margin) or floating (reference rate + margin) coupon, capitalized or paid interest, and an early redemption fee. Each purchase is its own lot, because interest accrues from the purchase date. Polish retail series (OTS, ROR, DOR, TOS, COI, EDO, ROS, ROD) ship as presets. Exact rules (rounding, fee limits, which CPI month applies) must be verified against official data and tested against published values before presets ship.

**Rules.** `MaxPositionWeight` and `MinLowRiskWeight`, each for one account or all. Low-risk weight counts positions at or below a risk level, plus cash.
