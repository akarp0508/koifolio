import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Position } from '../api/client'
import { formatAmount, formatGain, formatQuoteTime } from '../format'

const ALL = ''
const gainClass = (amount?: string) => {
  if (amount === undefined || /^-?[0.]+$/.test(amount)) {
    return 'number'
  }
  return amount.startsWith('-') ? 'number loss' : 'number gain'
}

const INCOMPLETE_HINT = 'A sale exceeds the shares on record, so realized gain only counts the shares with a known cost.'

export function Positions() {
  const [accounts, setAccounts] = useState<Account[] | null>(null)
  const [positions, setPositions] = useState<Position[]>([])
  const [filter, setFilter] = useState(ALL)
  const [showClosed, setShowClosed] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  const fetchAll = useCallback(() =>
    Promise.all([api.listAccounts(), api.listPositions()])
      .then(([accountList, positionList]) => {
        setAccounts(accountList)
        setPositions(positionList)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void fetchAll()
  }, [fetchAll])

  async function refresh() {
    setRefreshing(true)
    setError(null)
    try {
      await api.refreshPrices()
      await fetchAll()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setRefreshing(false)
    }
  }

  const quoteTime = formatQuoteTime(positions.map((position) => position.quotedAt))
  const accountName = (id: string) => accounts?.find((account) => account.id === id)?.name ?? ''
  const shown = positions
    .filter((position) => filter === ALL || position.accountId === filter)
    .filter((position) => showClosed || !position.closed || position.incomplete)

  return (
    <>
      <header className="page-header">
        <h1>Positions</h1>
        <div className="header-actions">
          {quoteTime && <span className="muted">Prices as of {quoteTime}</span>}
          <button disabled={refreshing} onClick={refresh}>{refreshing ? 'Refreshing…' : 'Refresh'}</button>
        </div>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchAll}>Try again</button>
        </p>
      )}
      {accounts === null && !error && <p className="muted">Loading…</p>}
      {accounts !== null && (
        <div className="filter">
          <label className="filter-check">
            Account
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option value={ALL}>All</option>
              {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
            </select>
          </label>
          <label className="filter-check">
            <input type="checkbox" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} />
            Show closed
          </label>
        </div>
      )}
      {accounts !== null && shown.length === 0 && (
        <p className="muted">No positions yet. They appear once you add buys in <a href="#transactions">Transactions</a>.</p>
      )}
      {shown.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Ticker</th>
                <th>Account</th>
                <th className="number">Quantity</th>
                <th className="number">Avg cost</th>
                <th className="number">Price</th>
                <th className="number">Value</th>
                <th className="number">P/L</th>
                <th className="number">Realized</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((position) => (
                <tr key={`${position.accountId}-${position.ticker}`} className={position.closed ? 'closed' : undefined}>
                  <td>
                    {position.ticker}
                    {position.incomplete && (
                      <span className="warning" role="img" aria-label={INCOMPLETE_HINT} title={INCOMPLETE_HINT}>⚠</span>
                    )}
                  </td>
                  <td>{accountName(position.accountId)}</td>
                  <td className="number">{formatAmount(position.quantity)}</td>
                  <td className="number">{position.averageCost ? formatAmount(position.averageCost) : '—'}</td>
                  <td className="number">
                    {position.price ? `${formatAmount(position.price)} ${position.priceCurrency}` : '—'}
                  </td>
                  <td className="number">
                    {position.value ? `${formatAmount(position.value)} ${position.currency}` : '—'}
                    {position.currencyMismatch && <span className="not-in-balance">priced in {position.priceCurrency}</span>}
                  </td>
                  <td className={gainClass(position.unrealizedGain)}>
                    {position.unrealizedGain ? formatGain(position.unrealizedGain) : '—'}
                    {position.unrealizedPercent && (
                      <span className="not-in-balance">{formatGain(position.unrealizedPercent)}%</span>
                    )}
                  </td>
                  <td className={gainClass(position.realizedGain)}>{formatGain(position.realizedGain)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
