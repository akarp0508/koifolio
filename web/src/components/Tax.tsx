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
            An estimate of tax on sales, not tax advice. Losses offset gains within a year between accounts at the same
            rate. Dividends aren't included, and losses aren't carried to other years.
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

          {hasOpen && (
            <>
              <h2 className="section-title">Open positions, if sold today</h2>
              <div className="table-scroll">
                <table>
                  <tbody>
                    {estimate.openPositions?.map((position) => (
                      <tr key={`${position.accountId}-${position.ticker}`}>
                        <td><a href={`#positions/${position.accountId}/${encodeURIComponent(position.ticker ?? '')}`}>{position.ticker}</a></td>
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
