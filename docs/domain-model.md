# Domain model (`domain` package)

> **Mostly a plan.** Only `PositionCalculator`, `Valuation`, `PositionHistory` and `PlanCheck` are implemented so far. `docs/api.md` describes what exists.

The `domain` package in `server` is pure Java: no Spring, no I/O, no users. The server loads one user's data and passes it in.

## Central idea

```java
PortfolioState state = Portfolio.replay(transactions, accounts, instruments, asOf, marketData);
```

Replaying transactions up to `asOf` produces everything. Unvalidated agent transactions are replayed like any others; the review state lives in the server. The same call serves the dashboard (`asOf` = today), daily snapshots and backfill (`asOf` = a past date).

## Values

```java
record Money(BigDecimal amount, Currency currency)   // mixing currencies throws
record AccountId(String value)
record InstrumentId(String value)                    // stable id, not the ticker
```

- Tickers change after renames and mergers, so a ticker is only an attribute.
- Calculations keep full precision (`MathContext.DECIMAL128`); rounding happens at the output.
- `Money.equals` compares numerically, so `1.0` equals `1.00`.

## Accounts

```java
record Account(AccountId id, String name, String description, TaxSettings tax)
record TaxSettings(CostBasisMethod method, BigDecimal rate)   // method: AVERAGE | FIFO
```

An account owns its lots and cash, and can hold any instrument. UI presets fill in `TaxSettings`, e.g. regular (FIFO, 19%), IKE (rate 0).

## Instruments

```java
sealed interface Instrument permits Security, BondSeries
record Security(InstrumentId id, String ticker, String market, Currency currency)    // stock or ETF
record BondSeries(InstrumentId id, String series, Money nominal, int termMonths,
                  CouponModel coupon, InterestMode interest, int periodMonths,
                  Money earlyRedemptionFee)

sealed interface CouponModel permits Fixed, Indexed, Floating
record Fixed(BigDecimal rate)
record Indexed(BigDecimal firstPeriodRate, BigDecimal margin)    // CPI + margin
record Floating(BigDecimal firstPeriodRate, BigDecimal margin)   // reference rate + margin

enum InterestMode { CAPITALIZED, PAID }

// Private per user
record InstrumentProfile(InstrumentId id, String sector, Integer risk /* 1–5 */,
                         Scenarios scenarios, String thesis, String note, LocalDate updated)
```

## Transactions

A sealed interface with one record per type, so the compiler forces every calculation to handle every type.

```java
sealed interface Transaction {
    AccountId account();
    LocalDate date();
    int sequence();      // orders transactions within one day
}

record Buy(..., InstrumentId instrument, BigDecimal qty, Money price, Money fee)
record Sell(..., InstrumentId instrument, BigDecimal qty, Money price, Money fee)
record Dividend(..., InstrumentId instrument, Money gross, Money withheldTax)
record Deposit(..., Money amount)
record Withdrawal(..., Money amount)
record Fee(..., Money amount)
record Interest(..., Money amount)                       // interest on cash
record CurrencyExchange(..., Money sold, Money bought)
record BondBuy(..., InstrumentId series, BigDecimal units, Money unitPrice)
record BondRedeem(..., InstrumentId series, BigDecimal units)
record Split(..., InstrumentId instrument, BigDecimal ratio)
record SpinOff(..., InstrumentId from, InstrumentId to, BigDecimal qty, BigDecimal costFraction)
```

- `Split`: quantity × ratio, total cost unchanged.
- `SpinOff`: moves `costFraction` of the cost to the new instrument.
- Each `BondBuy` is its own lot, because interest accrues from the individual purchase date. Rollovers use a lower `unitPrice`.

## Market data

Interfaces implemented by the server.

```java
interface MarketData {
    Optional<Money> closePrice(InstrumentId id, LocalDate date);      // last close on or before date
    Optional<BigDecimal> fxRate(Currency c, LocalDate date);          // last published on or before date
    Optional<BigDecimal> cpi(YearMonth month);
    Optional<BigDecimal> referenceRate(LocalDate date);
}
```

A missing price doesn't fail the replay: the position is returned without a value, plus a `Warning`.

## Results

```java
record PortfolioState(List<Position> positions, List<BondHolding> bonds,
                      Map<AccountId, Map<Currency, Money>> cash,
                      List<RealizedGain> realized, List<Income> income, List<Warning> warnings)

record Position(AccountId account, InstrumentId instrument, BigDecimal qty, Money costBasis,
                Money avgPrice, Money value, Money unrealizedPnl, BigDecimal weight)
record BondHolding(AccountId account, InstrumentId series, LocalDate purchased, BigDecimal units,
                   Money cost, Money value, Money accruedInterest, Money redemptionValue)
record RealizedGain(AccountId account, InstrumentId instrument, LocalDate date,
                    BigDecimal qty, Money proceeds, Money cost, Money gain)
record Income(AccountId account, InstrumentId instrument, LocalDate date, Money gross, Money withheldTax)
```

Weights and totals are computed for the selected view (one account or all).

## Calculation rules

**Lots.** Lots are tracked per account and instrument. A sale consumes them according to the account's method: oldest first for FIFO, the average cost for AVERAGE.

**Fees.** Included in cost basis: buy fees raise cost, sell fees reduce proceeds.

**Invalid history.** Overselling is an error, negative cash a warning.

**Currency.** Totals are reported in the base currency. Values use the latest rate on or before `asOf`. Costs use the rate from the last publication before the transaction date (the Polish tax rule) by default or, as a setting, the transaction date itself. The first FX provider is NBP, which quotes against PLN, so other base currencies are cross rates through PLN, limited to the currencies in its table A (32 on 2026-11-05). Foreign positions also show P/L in their own currency.

**Tax.** An estimate only. Unrealized is gain × rate per position, with no tax on losses. Realized is summed per calendar year and account, with losses offsetting gains in that year.

**Bonds.** Value is nominal plus interest accrued in the current period, compounded across periods when `CAPITALIZED`. The rate for each period comes from the `CouponModel`. `redemptionValue` subtracts the early-redemption fee. Exact rules (rounding, fee limits, which CPI month applies) must be verified against official data and tested against published values before presets ship.

**Presets:** Polish retail series first (OTS, ROR, DOR, TOS, COI, EDO, ROS, ROD). Any other bond can be defined by its parameters. CPI and reference rates are per-country inputs.

## Rules

```java
sealed interface Rule permits MaxPositionWeight, MinLowRiskWeight
record MaxPositionWeight(Optional<AccountId> scope, BigDecimal limit)
record MinLowRiskWeight(Optional<AccountId> scope, int maxRisk, BigDecimal min)
record RuleResult(Rule rule, boolean passed, BigDecimal actual, BigDecimal limit)
```

Low-risk weight counts instruments with `risk <= maxRisk`, plus cash.

## Build order

1. `Money` and value types
2. Replay: positions, average/FIFO, realized gains, cash
3. Splits and spin-offs
4. FX
5. Bonds
6. Rules
