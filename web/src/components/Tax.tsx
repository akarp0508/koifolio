import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { TaxEstimate } from '../api/client'
import { formatAmount, formatGain, gainClass } from '../format'

export function Tax() {
  const [year, setYear] = useState<number | undefined>(undefined)
  const [estimate, setEstimate] = useState<TaxEstimate | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  const fetchEstimate = useCallback(() =>
    api.tax(year)
      .then((data) => {
        setEstimate(data)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [year])

  useEffect(() => {
    void fetchEstimate()
  }, [fetchEstimate])

  const currency = estimate?.currency ?? ''
  const hasOpen = (estimate?.openPositions?.length ?? 0) > 0
  const hasDividends = (estimate?.dividendGroups?.length ?? 0) > 0

  return (
    <>
      <header className="page-header">
        <h1>Tax estimate</h1>
        {estimate && (
          <label className="filter-check">
            Year
            <select value={estimate.year} onChange={(e) => setYear(Number(e.target.value))}>
              {estimate.years?.map((option) => <option key={option} value={option}>{option}</option>)}
            </select>
          </label>
        )}
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchEstimate}>Try again</button>
        </p>
      )}
      {!estimate && !error && <p className="muted">Loading…</p>}

      {estimate && (
        <>
          <p className="muted">
            An estimate of tax on sales and dividends, not tax advice. Losses offset gains within a year between accounts
            at the same rate, but they don't offset dividends and aren't carried to other years. A dividend entered without
            its withheld tax counts as not taxed yet.
          </p>

          {(estimate.unsetAccounts?.length ?? 0) > 0 && (
            <p className="notice">
              No tax rate set for {estimate.unsetAccounts?.join(', ')}, so {estimate.unsetAccounts?.length === 1 ? "it isn't" : "they aren't"} counted.
              {' '}<a href="#accounts">Set it in Accounts.</a>
            </p>
          )}

          <div className="tiles">
            <div className="tile">
              <span>Tax on {estimate.year} sales</span>
              <strong>{formatAmount(estimate.totalTax ?? '')} {currency}</strong>
            </div>
            {hasDividends && (
              <div className="tile">
                <span>Dividend tax left</span>
                <strong>{formatAmount(estimate.totalDividendTax ?? '')} {currency}</strong>
              </div>
            )}
            {estimate.totalIfSoldToday && (
              <div className="tile">
                <span>Extra if sold today</span>
                <strong>{formatGain(estimate.totalIfSoldToday)} {currency}</strong>
              </div>
            )}
          </div>

          {estimate.groups?.length === 0 && <p className="muted">No sales to estimate for {estimate.year}.</p>}
          {estimate.groups?.map((group) => (
            <section className="panel tax-group" key={group.ratePercent}>
              <header><h2>{group.ratePercent === '0' ? 'Tax free (0%)' : `Taxed at ${group.ratePercent}%`}</h2></header>
              <div className="table-scroll">
                <table>
                  <tbody>
                    {group.accounts?.map((account) => (
                      <tr key={account.accountId}>
                        <td>{account.name}</td>
                        <td className={`number ${gainClass(account.realized)}`}>
                          {formatGain(account.realized ?? '')} {currency}
                        </td>
                      </tr>
                    ))}
                    <tr>
                      <td><strong>Total</strong></td>
                      <td className="number">
                        <strong className={gainClass(group.realized)}>{formatGain(group.realized ?? '')} {currency}</strong>
                        <span className="not-in-balance">tax {formatAmount(group.tax ?? '')} {currency}</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          ))}

          {estimate.dividendGroups?.map((group) => (
            <section className="panel tax-group" key={group.ratePercent}>
              <header>
                <h2>Dividends {estimate.year} — {group.ratePercent === '0' ? 'tax free (0%)' : `taxed at ${group.ratePercent}%`}</h2>
              </header>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Account</th>
                      <th className="number">Gross</th>
                      <th className="number">Withheld</th>
                      <th className="number">Left to pay</th>
                    </tr>
                  </thead>
                  <tbody>
                    {group.accounts?.map((account) => (
                      <tr key={account.accountId}>
                        <td>{account.name}</td>
                        <td className="number">{formatAmount(account.gross ?? '')} {currency}</td>
                        <td className="number">{formatAmount(account.withheld ?? '')} {currency}</td>
                        <td className="number">{formatAmount(account.taxLeft ?? '')} {currency}</td>
                      </tr>
                    ))}
                    {(group.accounts?.length ?? 0) > 1 && (
                      <tr>
                        <td><strong>Total</strong></td>
                        <td className="number"><strong>{formatAmount(group.gross ?? '')} {currency}</strong></td>
                        <td className="number"><strong>{formatAmount(group.withheld ?? '')} {currency}</strong></td>
                        <td className="number"><strong>{formatAmount(group.taxLeft ?? '')} {currency}</strong></td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          ))}

          {hasOpen && (
            <>
              <h2 className="section-title">Open positions, if sold today</h2>
              <div className="table-scroll">
                <table>
                  <tbody>
                    {estimate.openPositions?.map((position) => (
                      <tr key={`${position.accountId}-${position.ticker}`}>
                        <td>{position.instrumentId ? <a href={`#companies/${position.instrumentId}/${position.accountId}`}>{position.ticker}</a> : <a href="#bonds">{position.ticker}</a>}</td>
                        <td>{position.accountName}</td>
                        <td className={`number ${gainClass(position.gain)}`}>{formatGain(position.gain ?? '')} {currency}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </>
      )}
    </>
  )
}
