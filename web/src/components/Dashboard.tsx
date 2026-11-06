import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Dashboard as DashboardData, DashboardSlice, PeriodChange, PortfolioHistory, Position, Settings } from '../api/client'
import { formatAmount, formatGain, gainClass } from '../format'
import { RANGES, inRange } from '../history'
import type { Range } from '../history'
import { SIGNAL_LABELS, allTags, signalClass } from '../plans'
import { today } from '../transactions'
import { TagFilter } from './TagFilter'

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

export function Dashboard() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [settings, setSettings] = useState<Settings | null>(null)
  const [dashboard, setDashboard] = useState<DashboardData | null>(null)
  const [account, setAccount] = useState(ALL)
  const [instrumentTags, setInstrumentTags] = useState<string[]>([])
  const [positionTags, setPositionTags] = useState<string[]>([])
  const [history, setHistory] = useState<PortfolioHistory | null>(null)
  const [range, setRange] = useState<Range>('1Y')
  const [error, setError] = useState<ApiError | null>(null)

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

  useEffect(() => {
    Promise.all([api.listAccounts(), api.listPositions(), api.settings()])
      .then(([accountList, positionList, settingsData]) => {
        setAccounts(accountList)
        setPositions(positionList)
        setSettings(settingsData)
      })
      .catch((e: ApiError) => setError(e))
  }, [])

  function changeBase(baseCurrency: string) {
    api.updateSettings(baseCurrency)
      .then((updated) => {
        setSettings(updated)
        return fetchDashboard()
      })
      .catch((e: ApiError) => setError(e))
  }

  const currency = dashboard?.currency ?? ''
  const filteredByTags = instrumentTags.length > 0 || positionTags.length > 0
  const points = history ? inRange(history.points, range, today()) : []

  return (
    <>
      <header className="page-header">
        <h1>Dashboard</h1>
        {settings && (
          <label className="filter-check">
            Base currency
            <select value={settings.baseCurrency} onChange={(e) => changeBase(e.target.value)}>
              {settings.currencies.map((code) => <option key={code} value={code}>{code}</option>)}
            </select>
          </label>
        )}
      </header>

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
            <Tile label="P/L" tone={gainClass(dashboard.unrealizedGain)}
                  note={dashboard.unrealizedPercent && `${formatGain(dashboard.unrealizedPercent)}%`}>
              {formatGain(dashboard.unrealizedGain)} {currency}
            </Tile>
            <Tile label="Realized" tone={gainClass(dashboard.realizedGain)}>{formatGain(dashboard.realizedGain)} {currency}</Tile>
            <Tile label="Cash" note={filteredByTags ? 'not filtered by tags' : undefined}>
              {dashboard.cash ? `${formatAmount(dashboard.cash)} ${currency}` : '—'}
            </Tile>
          </div>

          {history && <Periods changes={history.changes} currency={currency} />}

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

          {dashboard.unpriced > 0 && (
            <p className="muted">
              {dashboard.unpriced === 1 ? '1 position has' : `${dashboard.unpriced} positions have`} no price or exchange
              rate, so {dashboard.unpriced === 1 ? "it isn't" : "they aren't"} counted.
            </p>
          )}

          <div className="panels">
            <Bars title="By sector" slices={dashboard.bySector} currency={currency} />
            <Bars title="By risk" slices={dashboard.byRisk} currency={currency} />
          </div>

          <h2 className="section-title">Needs attention</h2>
          {dashboard.attention.length === 0 && <p className="muted">Nothing near a target or stop.</p>}
          {dashboard.attention.length > 0 && (
            <div className="table-scroll">
              <table>
                <tbody>
                  {dashboard.attention.map((item) => (
                    <tr key={`${item.accountId}-${item.ticker}`}>
                      <td><a href={`#positions/${item.accountId}/${encodeURIComponent(item.ticker ?? '')}`}>{item.ticker}</a></td>
                      <td>{item.accountName}</td>
                      <td>
                        {item.signal && <span className={signalClass(item.signal)}>{SIGNAL_LABELS[item.signal]}</span>}
                      </td>
                      <td className="number muted">{item.distance && `${formatGain(item.distance)}%`}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </>
  )
}
