import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Dashboard, DashboardSlice, PeriodChange, PortfolioHistory, Position, Settings, TaxEstimate } from '../api/client'
import { formatAmount, formatGain, formatQuoteTime, gainClass } from '../format'
import { RANGES, inRange } from '../history'
import type { Range } from '../history'
import { SIGNAL_LABELS, allTags, signalClass } from '../plans'
import { today } from '../transactions'
import { Holdings } from './Holdings'
import type { TradeRequest } from './Holdings'
import { SplitSuggestions } from './SplitSuggestions'
import { TagFilter } from './TagFilter'
import { TransactionDialog } from './TransactionDialog'

// Holds the chart library, so the rest of the page opens without it
const PortfolioChart = lazy(() =>
  import('./PortfolioChart').then((module) => ({ default: module.PortfolioChart })))

const ALL = ''

const PERIOD_LABELS: Record<NonNullable<PeriodChange['period']>, string> = {
  DAY: '1D',
  WEEK: '1W',
  MONTH: '1M',
  YEAR_TO_DATE: 'YTD',
  YEAR: '1Y',
}

function gainNote(dashboard: Dashboard) {
  const percent = dashboard.unrealizedPercent && `${formatGain(dashboard.unrealizedPercent)}%`
  const currency = dashboard.currencyGain && gainClass(dashboard.currencyGain)
    ? `currency ${formatGain(dashboard.currencyGain)}`
    : undefined
  return [percent, currency].filter(Boolean).join(' · ') || undefined
}

function taxNote(tax: TaxEstimate) {
  const unset = tax.unsetAccounts?.length ?? 0
  const sold = tax.totalIfSoldToday && `${formatGain(tax.totalIfSoldToday)} if sold today`
  return unset > 0 ? `${sold} · ${unset} without a rate` : sold
}

function Tile({ label, children, note, tone = '' }: {
  label: string
  children: ReactNode
  note?: string
  tone?: string
}) {
  return (
    <div className={`tile ${tone}`}>
      <span>{label}</span>
      <strong>{children}</strong>
      {note && <small>{note}</small>}
    </div>
  )
}

function Periods({ changes, currency }: { changes: PeriodChange[]; currency: string }) {
  return (
    <div className="periods">
      {Object.entries(PERIOD_LABELS).map(([period, label]) => {
        const change = changes.find((item) => item.period === period)
        return (
          <div key={period} className={`period ${gainClass(change?.change)}`}>
            <span>{label}</span>
            <strong>{change ? `${formatGain(change.change ?? '')} ${currency}` : '—'}</strong>
            {change?.percent && <small>{formatGain(change.percent)}%</small>}
          </div>
        )
      })}
    </div>
  )
}

function Bars({ title, slices, currency }: { title: string; slices: DashboardSlice[]; currency: string }) {
  return (
    <section className="panel">
      <header><h2>{title}</h2></header>
      {slices.length === 0 && <p className="muted">Nothing to show.</p>}
      {slices.map((slice) => (
        <div className="bar-row" key={slice.label ?? 'none'} title={`${formatAmount(slice.value ?? '')} ${currency}`}>
          <span className={slice.label ? undefined : 'muted'}>{slice.label ?? 'Not set'}</span>
          <div className="bar"><div style={{ width: `${slice.percent}%` }} /></div>
          <span className="number">{slice.percent}%</span>
        </div>
      ))}
    </section>
  )
}

export function Portfolio() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [settings, setSettings] = useState<Settings | null>(null)
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [account, setAccount] = useState(ALL)
  const [instrumentTags, setInstrumentTags] = useState<string[]>([])
  const [positionTags, setPositionTags] = useState<string[]>([])
  const [history, setHistory] = useState<PortfolioHistory | null>(null)
  const [tax, setTax] = useState<TaxEstimate | null>(null)
  const [range, setRange] = useState<Range>('1Y')
  const [error, setError] = useState<ApiError | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  // null while closed; an empty request is a transaction for anything
  const [trade, setTrade] = useState<Partial<TradeRequest> | null>(null)

  const fetchDashboard = useCallback(() => {
    const filter = { account: account || undefined, instrumentTags, positionTags }
    return Promise.all([api.dashboard(filter), api.portfolioHistory(filter)])
      .then(([data, historyData]) => {
        setDashboard(data)
        setHistory(historyData)
        setError(null)
      })
      .catch((e: ApiError) => setError(e))
  }, [account, instrumentTags, positionTags])

  useEffect(() => {
    void fetchDashboard()
  }, [fetchDashboard])

  const fetchOverview = useCallback(() =>
    Promise.all([api.listAccounts(), api.listPositions(), api.settings(), api.tax()])
      .then(([accountList, positionList, settingsData, taxData]) => {
        setTax(taxData)
        setAccounts(accountList)
        setPositions(positionList)
        setSettings(settingsData)
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void fetchOverview()
  }, [fetchOverview])

  function traded() {
    setTrade(null)
    void Promise.all([fetchOverview(), fetchDashboard()])
  }

  async function refresh() {
    setRefreshing(true)
    setError(null)
    try {
      await api.refreshPrices()
      setPositions(await api.listPositions())
      await fetchDashboard()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setRefreshing(false)
    }
  }

  function changeSettings(changes: Parameters<typeof api.updateSettings>[0]) {
    api.updateSettings(changes)
      .then((updated) => {
        setSettings(updated)
        return fetchDashboard()
      })
      .catch((e: ApiError) => setError(e))
  }

  const currency = dashboard?.currency ?? ''
  const filteredByTags = instrumentTags.length > 0 || positionTags.length > 0
  const points = history ? inRange(history.points, range, today()) : []
  const quoteTime = formatQuoteTime(positions.map((position) => position.quotedAt))

  return (
    <>
      <header className="page-header">
        <h1>Portfolio</h1>
        <div className="header-actions wrap">
          {quoteTime && <span className="muted">Prices as of {quoteTime}</span>}
          <button disabled={refreshing} onClick={refresh}>{refreshing ? 'Refreshing…' : 'Refresh'}</button>
          <button className="primary" disabled={accounts.length === 0} onClick={() => setTrade({})}>+ Transaction</button>
          {settings && (
            <>
              <label className="filter-check">
                Base currency
                <select value={settings.baseCurrency} onChange={(e) => changeSettings({ baseCurrency: e.target.value })}>
                  {settings.currencies.map((code) => <option key={code} value={code}>{code}</option>)}
                </select>
              </label>
              <label className="filter-check" title="Which day's exchange rate converts a buy or sell to the base currency">
                Trade FX rate
                <select
                  value={settings.costFxRule}
                  onChange={(e) => changeSettings({ costFxRule: e.target.value as Settings['costFxRule'] })}
                >
                  <option value="previous_publication">Day before (Polish tax)</option>
                  <option value="transaction_date">Trade date</option>
                </select>
              </label>
            </>
          )}
        </div>
      </header>

      <SplitSuggestions onApplied={fetchDashboard} />

      <div className="filter wrap">
        <label className="filter-check">
          Account
          <select value={account} onChange={(e) => setAccount(e.target.value)}>
            <option value={ALL}>All</option>
            {accounts.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
          </select>
        </label>
        <TagFilter label="Company tags" tags={allTags(positions, 'instrumentTags')} selected={instrumentTags}
                   onChange={setInstrumentTags} />
        <TagFilter label="Position tags" tags={allTags(positions, 'positionTags')} selected={positionTags}
                   onChange={setPositionTags} />
      </div>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchDashboard}>Try again</button>
        </p>
      )}
      {!dashboard && !error && <p className="muted">Loading…</p>}

      {dashboard && (
        <>
          <div className="tiles">
            <Tile label="Value">{formatAmount(dashboard.value)} {currency}</Tile>
            <Tile label="P/L" tone={gainClass(dashboard.unrealizedGain)} note={gainNote(dashboard)}>
              {formatGain(dashboard.unrealizedGain)} {currency}
            </Tile>
            <Tile label="Realized" tone={gainClass(dashboard.realizedGain)}>{formatGain(dashboard.realizedGain)} {currency}</Tile>
            {tax && (
              <Tile label={`Est. tax ${tax.year}`} note={taxNote(tax)}>
                <a className="tile-link" href="#tax">{formatAmount(tax.totalTax ?? '')} {tax.currency}</a>
              </Tile>
            )}
            <Tile label="Cash" note={filteredByTags ? 'not filtered by tags' : undefined}>
              {dashboard.cash ? `${formatAmount(dashboard.cash)} ${currency}` : '—'}
            </Tile>
          </div>

          {dashboard.attention.length > 0 && (
            <ul className="attention" aria-label="Needs attention">
              {dashboard.attention.map((item) => (
                <li key={`${item.accountId}-${item.ticker}`}>
                  <a href={`#positions/${item.accountId}/${encodeURIComponent(item.ticker ?? '')}`}>{item.ticker}</a>
                  <span className="muted">{item.accountName}</span>
                  {item.signal && <span className={signalClass(item.signal)}>{SIGNAL_LABELS[item.signal]}</span>}
                  {item.distance && <span className="muted">{formatGain(item.distance)}%</span>}
                </li>
              ))}
            </ul>
          )}

          <div className="overview">
            <div>
              {history && history.points.length > 0 && (
                <section className="panel chart-panel">
                  <header>
                    <h2>Value over time</h2>
                    <div className="ranges">
                      {RANGES.map((option) => (
                        <button key={option} aria-pressed={option === range} onClick={() => setRange(option)}>{option}</button>
                      ))}
                    </div>
                  </header>
                  {points.length > 0 ? (
                    <Suspense fallback={<p className="muted">Loading…</p>}>
                      <PortfolioChart points={points} currency={currency} />
                    </Suspense>
                  ) : <p className="muted">Nothing in this range.</p>}
                </section>
              )}
              {history && <Periods changes={history.changes} currency={currency} />}
            </div>
            <div className="overview-side">
              <Bars title="By sector" slices={dashboard.bySector} currency={currency} />
              <Bars title="By risk" slices={dashboard.byRisk} currency={currency} />
            </div>
          </div>

          {dashboard.unpriced > 0 && (
            <p className="muted">
              {dashboard.unpriced === 1 ? '1 position has' : `${dashboard.unpriced} positions have`} no price or exchange
              rate, so {dashboard.unpriced === 1 ? "it isn't" : "they aren't"} counted.
            </p>
          )}

          <Holdings holdings={dashboard.holdings ?? []} currency={currency} onTrade={setTrade} />
        </>
      )}

      {trade && (
        <TransactionDialog
          transaction={null}
          accounts={accounts}
          initialAccountId={trade.accountId ?? (account || accounts[0].id)}
          initialType={trade.type}
          initialTicker={trade.ticker}
          onSaved={traded}
          onClose={() => setTrade(null)}
        />
      )}
    </>
  )
}
