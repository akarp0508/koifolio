import { useState } from 'react'
import type { Dashboard } from '../api/client'
import { formatAmount, formatGain, gainClass } from '../format'

type Holding = NonNullable<Dashboard['holdings']>[number]
type AccountHolding = NonNullable<Holding['accounts']>[number]

const INCOMPLETE_HINT = 'A sale exceeds the shares on record, so realized gain only counts the shares with a known cost.'

const positionHash = (holding: Holding, account: AccountHolding) =>
  `#positions/${account.accountId}/${encodeURIComponent(holding.ticker ?? '')}`

function Numbers({ row }: { row: Holding | AccountHolding }) {
  return (
    <>
      <td className="number">{row.value ? formatAmount(row.value) : '—'}</td>
      <td className="number">{row.weight ? `${row.weight}%` : '—'}</td>
      <td className={`number ${gainClass(row.unrealizedGain)}`}>
        {row.unrealizedGain ? formatGain(row.unrealizedGain) : '—'}
        {row.unrealizedPercent && <span className="not-in-balance">{formatGain(row.unrealizedPercent)}%</span>}
      </td>
      <td className={`number ${gainClass(row.realizedGain)}`}>{formatGain(row.realizedGain ?? '0')}</td>
    </>
  )
}

function Company({ holding }: { holding: Holding }) {
  const [open, setOpen] = useState(false)
  const accounts = holding.accounts ?? []
  const several = accounts.length > 1
  const href = several ? `#instruments/${holding.instrumentId}` : positionHash(holding, accounts[0])
  const incomplete = accounts.some((account) => account.incomplete)

  function click() {
    if (several) {
      setOpen(!open)
    } else {
      window.location.hash = href
    }
  }

  return (
    <>
      <tr className={holding.held ? 'clickable' : 'closed clickable'} onClick={click}>
        <td>
          {several
            ? <button className="expand" aria-expanded={open} aria-label={`Accounts holding ${holding.ticker}`}>{open ? '▾' : '▸'}</button>
            : <span className="expand" />}
          <a href={href} onClick={(e) => e.stopPropagation()}>{holding.ticker}</a>
          {!several && <span className="muted holding-account">{accounts[0].accountName}</span>}
          {incomplete && <span className="warning" role="img" aria-label={INCOMPLETE_HINT} title={INCOMPLETE_HINT}>⚠</span>}
        </td>
        <Numbers row={holding} />
      </tr>
      {open && accounts.map((account) => (
        <tr key={account.accountId} className={account.closed ? 'closed clickable sub-row' : 'clickable sub-row'}
            onClick={() => { window.location.hash = positionHash(holding, account) }}>
          <td><a href={positionHash(holding, account)} onClick={(e) => e.stopPropagation()}>{account.accountName}</a></td>
          <Numbers row={account} />
        </tr>
      ))}
    </>
  )
}

export function Holdings({ holdings, currency }: { holdings: Holding[]; currency: string }) {
  const [showSold, setShowSold] = useState(false)
  const held = holdings.filter((holding) => holding.held)
  const sold = holdings.filter((holding) => !holding.held)

  if (holdings.length === 0) {
    return <p className="muted">No positions here yet. They appear once you add buys in <a href="#activity">Activity</a>.</p>
  }

  return (
    <div className="table-scroll holdings">
      <table>
        <thead>
          <tr>
            <th>Company</th>
            <th className="number">Value ({currency})</th>
            <th className="number" title="Share of the selected account, or of everything, cash included">Weight</th>
            <th className="number">P/L</th>
            <th className="number">Realized</th>
          </tr>
        </thead>
        <tbody>
          {held.map((holding) => <Company key={holding.instrumentId} holding={holding} />)}
          {sold.length > 0 && (
            <tr className="clickable" onClick={() => setShowSold(!showSold)}>
              <td colSpan={5}>
                <button className="expand" aria-expanded={showSold} aria-label="Companies not held">{showSold ? '▾' : '▸'}</button>
                <span className="muted">Not held ({sold.length})</span>
              </td>
            </tr>
          )}
          {showSold && sold.map((holding) => <Company key={holding.instrumentId} holding={holding} />)}
        </tbody>
      </table>
    </div>
  )
}
