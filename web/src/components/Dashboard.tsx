import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Dashboard as DashboardData, DashboardSlice, Position, Settings } from '../api/client'
import { formatAmount, formatGain } from '../format'
import { SIGNAL_LABELS, allTags, signalClass } from '../plans'
import { TagFilter } from './TagFilter'

const ALL = ''

function Tile({ label, children, note }: { label: string; children: ReactNode; note?: string }) {
  return (
    <div className="tile">
      <span>{label}</span>
      <strong>{children}</strong>
      {note && <small>{note}</small>}
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
  const [error, setError] = useState<ApiError | null>(null)

  const fetchDashboard = useCallback(() =>
    api.dashboard({ account: account || undefined, instrumentTags, positionTags })
      .then((data) => {
        setDashboard(data)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [account, instrumentTags, positionTags])

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
            <Tile label="P/L" note={dashboard.unrealizedPercent && `${formatGain(dashboard.unrealizedPercent)}%`}>
              {formatGain(dashboard.unrealizedGain)} {currency}
            </Tile>
            <Tile label="Realized">{formatGain(dashboard.realizedGain)} {currency}</Tile>
            <Tile label="Cash" note={filteredByTags ? 'not filtered by tags' : undefined}>
              {dashboard.cash ? `${formatAmount(dashboard.cash)} ${currency}` : '—'}
            </Tile>
          </div>

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
                      <td className="number">{item.distance && `${formatGain(item.distance)}%`}</td>
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
