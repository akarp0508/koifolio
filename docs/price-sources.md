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

## Current quotes
| Source | Status |
|---|---|
| bankier `api.bankier.pl/quotes/public/instrument-header/?symbol=PKOBP,XTB,…` | ✅ JSON, batched, delayed ~15 min. Covers GPW main market, NewConnect, ETFs and foreign stocks (e.g. `APPLE` → AAPL). Uses bankier symbols (`PKOBP`, not `PKO`); map them with `instrument-search/?bankier=true&query=…`. Undocumented internal endpoint. |
| biznesradar `/notowania/<TICKER>` | ✅ HTML, price in `itemprop="price"`. Allowed by robots.txt. Fallback. |
| NBP API (FX, reference rates) | ✅ official, free, no key |

## Historical closes (for backfill)
| Source | Status |
|---|---|
| bankier `www.bankier.pl/new-charts/get-data?symbol=…&type=ohlc&max_period=true` | ⚠️ JSON OHLC from listing date, but **frozen: data ends 2026-01-02** |
| bankier `api.bankier.pl/quotes/public/investor-chart/` | ❓ the new chart API; parameters not figured out yet |
| biznesradar `/notowania-historyczne/<TICKER>` | ⚠️ HTML OHLC + volume, ~50 sessions per page. robots.txt disallows pages 2+, so only the latest page is used |
| EODHD (`.WAR`) | ❓ free tier with a key; limits not verified |

## Not usable
| Source | Status |
|---|---|
| stooq.pl CSV | ❌ behind a JavaScript challenge |
| Yahoo Finance (`.WA`) | ❌ HTTP 429 without cookie/crumb |
| gpw.pl / newconnect.pl archive | ❌ connection reset by bot protection |

## Provider chain
- **Current:** bankier `instrument-header`. Planned: biznesradar as a fallback, and manual prices.
- **History:** bankier legacy chart (until 2026-01-02) + biznesradar latest page + our own daily snapshots. Until `investor-chart` or another source fills the 2026 gap, backfill (v1.1) is incomplete for that period.
- **Cache:** every cached price stores its source and fetch time.
