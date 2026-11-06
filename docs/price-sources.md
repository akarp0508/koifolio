# Price sources

Acceptable: free APIs and scraping of public pages (no paid keys). Must work on a Raspberry Pi without a headless browser. Findings below were tested on 2026-11-04. None of the sources are official APIs, so every provider must fail gracefully.

## Scope for now

Automatic prices cover GPW, NewConnect, NYSE, NASDAQ, Deutsche Börse and Polish ETFs, all through bankier. Anything else shows no price.

Checked live on 2026-11-05 through the app: PKO, PAS, XTB, CDR, ETFBW20TR, AAPL, KO and SIEGN all resolved and priced. Shell, LVMH and Toyota weren't found in search, so LSE, Euronext and Tokyo aren't covered.

## How bankier is used

Implemented in `BankierQuoteProvider`, sent with the user agent `koifolio`.

1. `instrument-search/?bankier=true&query=<ticker>` gives candidates. It has no tickers for Polish companies.
2. `instrument-header/?symbol=<candidates>` gives each candidate's real ticker, and an exact match wins. Polish companies are preferred if a ticker exists on several markets.
3. `investor-chart/?symbols=<symbol>` gives the currency, which quotes don't include.
4. The symbol and currency are stored on the instrument, and tickers that aren't found are retried after a day.
5. Every 15 minutes, one `instrument-header` request per 50 symbols updates the shared `quotes` table. If it fails, the last prices stay.
6. If re-read closes differ from stored ones, the source rewrote its history, so the whole symbol is fetched again. If they all changed by one usual ratio, that's stored as a split suggestion.
7. In the same run, `PriceHistoryService` compares what's stored with each symbol's first trade across all users, and fetches anything missing, up to 20 symbols per run. Each symbol is checked once a day, re-reading the last 7 days in case of corrections, so downtime and backfilled transactions repair themselves.

## Current quotes
| Source | Status |
|---|---|
| bankier `api.bankier.pl/quotes/public/instrument-header/?symbol=PKOBP,XTB,…` | ✅ JSON, batched, delayed ~15 min. Covers GPW main market, NewConnect, ETFs and foreign stocks (e.g. `APPLE` → AAPL). Uses bankier symbols (`PKOBP`, not `PKO`); map them with `instrument-search/?bankier=true&query=…`. Undocumented internal endpoint. |
| biznesradar `/notowania/<TICKER>` | ✅ HTML, price in `itemprop="price"`. Allowed by robots.txt. Fallback. |
| NBP API (FX, reference rates) | ✅ official, free, no key |

## Historical closes

Implemented in `BankierQuoteProvider`. Checked live on 2026-11-06.

| Source | Status |
|---|---|
| `company-profile-chart/<isin>/?symbols=<symbol>` | ✅ GPW, NewConnect and ETFs, e.g. PKO from 2004. Takes `date_from` and `date_to` in ms, or `max_period=true` |
| `foreign-company-section-chart/?symbols=<symbol>` | ✅ US and German stocks, e.g. Apple from 1980. Same parameters |
| `www.bankier.pl/new-charts/get-data` | ⚠️ the old endpoint, frozen at 2026-01-02; not used |
| `investor-chart/` | ⚠️ only useful for the currency |

Rows are `[time, open, high, low, close]`. Foreign responses nest them in `data[0]`.

- **History is split-adjusted:** Apple shows about $109 for August 2020, a quarter of what it traded at before its 4:1 split, and Dino Polska shows no jump at its 1:10 split on 2025-07-31. Older Polish history has some unexplained large moves, such as CD Projekt in 2002. Closes are not adjusted for dividends. Position history scales closes back up using the split transactions you enter.
- **Today's row is unfinished,** so only earlier days are stored.

## Not usable
| Source | Status |
|---|---|
| stooq.pl CSV | ❌ behind a JavaScript challenge |
| Yahoo Finance (`.WA`) | ❌ HTTP 429 without cookie/crumb |
| gpw.pl / newconnect.pl archive | ❌ connection reset by bot protection |

## Provider chain
- **Current:** bankier `instrument-header`. Planned: biznesradar as a fallback, and manual prices.
- **History:** the bankier chart endpoints above, stored in `daily_closes`.
- **Cache:** every cached price stores its source and fetch time.
