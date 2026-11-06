import { useState } from 'react'
import type { MouseEvent } from 'react'
import type { Dashboard, Instrument } from '../api/client'
import { formatAmount, formatGain, gainClass } from '../format'

type Holding = NonNullable<Dashboard['holdings']>[number]
type AccountHolding = NonNullable<Holding['accounts']>[number]

export type TradeRequest = { type: 'buy' | 'sell'; ticker: string; accountId?: string }

const INCOMPLETE_HINT = 'A sale exceeds the shares on record, so realized gain only counts the shares with a known cost.'

const companyHash = (instrumentId?: string, accountId?: string) =>
  accountId ? `#companies/${instrumentId}/${accountId}` : `#companies/${instrumentId}`

function Numbers({ row }: { row: Holding | AccountHolding }) {
  return (
    <>
      <td className="number">{row.value ? formatAmount(row.value) : '—'}</td>
      <td className="number">{row.weight ? `${row.weight}%` : '—'}</td>
      <td className={`number ${gainClass(row.unrealizedGain)}`}>
        {row.unrealizedGain ? formatGain(row.unrealizedGain) : '—'}
        {row.unrealizedPercent && <span className="not-in-balance">{formatGain(row.unrealizedPercent)}%</span>}
      </td>
      <td className={`number ${gainClass(row.realizedGain)}`}>{row.realizedGain ? formatGain(row.realizedGain) : '—'}</td>
      <td className={`number ${gainClass(row.dividends)}`}>{gainClass(row.dividends) ? formatGain(row.dividends ?? '') : '—'}</td>
    </>
  )
}

function TradeButtons({ ticker, accountId, canSell, onTrade }: {
  ticker: string
  accountId?: string
  canSell: boolean
  onTrade: (request: TradeRequest) => void
}) {
  function trade(event: MouseEvent, type: TradeRequest['type']) {
    event.stopPropagation()
    onTrade({ type, ticker, accountId })
  }

  return (
    <td className="actions">
      <button className="trade buy" aria-label={`Buy ${ticker}`} onClick={(e) => trade(e, 'buy')}>Buy</button>
      {canSell && <button className="trade sell" aria-label={`Sell ${ticker}`} onClick={(e) => trade(e, 'sell')}>Sell</button>}
    </td>
  )
}

function Company({ holding, onTrade }: { holding: Holding; onTrade: (request: TradeRequest) => void }) {
  const [open, setOpen] = useState(false)
  const accounts = holding.accounts ?? []
  const several = accounts.length > 1
  const href = companyHash(holding.instrumentId)
  const incomplete = accounts.some((account) => account.incomplete)
  const ticker = holding.ticker ?? ''
  const tradingAccount = accounts.find((account) => !account.closed)

  function toggle(event: MouseEvent) {
    event.stopPropagation()
    setOpen(!open)
  }

  return (
    <>
      <tr className={holding.held ? 'clickable' : 'closed clickable'} onClick={() => { window.location.hash = href }}>
        <td>
          {several
            ? <button className="expand" aria-expanded={open} aria-label={`Accounts holding ${ticker}`} onClick={toggle}>{open ? '▾' : '▸'}</button>
            : <span className="expand" />}
          <a href={href} onClick={(e) => e.stopPropagation()}>{ticker}</a>
          {accounts.length === 1 && <span className="muted holding-account">{accounts[0].accountName}</span>}
          {incomplete && <span className="warning" role="img" aria-label={INCOMPLETE_HINT} title={INCOMPLETE_HINT}>⚠</span>}
        </td>
        <Numbers row={holding} />
        <TradeButtons ticker={ticker} accountId={tradingAccount?.accountId ?? accounts[0]?.accountId}
                      canSell={tradingAccount !== undefined} onTrade={onTrade} />
      </tr>
      {open && accounts.map((account) => {
        const accountHash = companyHash(holding.instrumentId, account.accountId)
        return (
          <tr key={account.accountId} className={account.closed ? 'closed clickable sub-row' : 'clickable sub-row'}
              onClick={() => { window.location.hash = accountHash }}>
            <td><a href={accountHash} onClick={(e) => e.stopPropagation()}>{account.accountName}</a></td>
            <Numbers row={account} />
            <TradeButtons ticker={ticker} accountId={account.accountId} canSell={!account.closed} onTrade={onTrade} />
          </tr>
        )
      })}
    </>
  )
}

export function Holdings({ holdings, watched, currency, onTrade }: {
  holdings: Holding[]
  watched: Instrument[]
  currency: string
  onTrade: (request: TradeRequest) => void
}) {
  const [showNotHeld, setShowNotHeld] = useState(false)
  const held = holdings.filter((holding) => holding.held)
  const notHeld: Holding[] = [
    ...holdings.filter((holding) => !holding.held),
    ...watched.map((instrument) => ({ instrumentId: instrument.id, ticker: instrument.ticker, held: false })),
  ]

  if (held.length === 0 && notHeld.length === 0) {
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
            <th className="number" title="Received, after tax withheld">Dividends</th>
            <th><span className="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          {held.map((holding) => <Company key={holding.instrumentId} holding={holding} onTrade={onTrade} />)}
          {notHeld.length > 0 && (
            <tr className="clickable" onClick={() => setShowNotHeld(!showNotHeld)}>
              <td colSpan={7}>
                <button className="expand" aria-expanded={showNotHeld} aria-label="Companies not held">{showNotHeld ? '▾' : '▸'}</button>
                <span className="muted">Not held ({notHeld.length})</span>
              </td>
            </tr>
          )}
          {showNotHeld && notHeld.map((holding) => <Company key={holding.instrumentId} holding={holding} onTrade={onTrade} />)}
        </tbody>
      </table>
    </div>
  )
}
