# Price sources

Acceptable: free APIs and scraping of public pages (no paid keys). Must work on a Raspberry Pi without a headless browser. Findings below were tested on 2026-11-04. None of the sources are official APIs, so every provider must fail gracefully.

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
- **Current:** bankier `instrument-header` → biznesradar → manual override.
- **History:** bankier legacy chart (until 2026-01-02) + biznesradar latest page + our own daily snapshots. Until `investor-chart` or another source fills the 2026 gap, backfill (v1.1) is incomplete for that period.
- **Cache:** every cached price stores its source and fetch time.
