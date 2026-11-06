import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Instrument, Position, PositionDetails, PositionHistory } from '../api/client'
import { formatAmount, formatGain } from '../format'
import { RANGES, inRange } from '../history'
import type { Range } from '../history'
import { today } from '../transactions'
import { CompanySection } from './CompanySection'
import { InstrumentDialog } from './InstrumentDialog'
import { PlanDialog } from './PlanDialog'
import { PlanSection } from './PlanSection'
import { PositionChart } from './PositionChart'

export function PositionDetail({ accountId, ticker }: { accountId: string; ticker: string }) {
  const [history, setHistory] = useState<PositionHistory | null>(null)
  const [position, setPosition] = useState<Position | null>(null)
  const [account, setAccount] = useState<Account | null>(null)
  const [details, setDetails] = useState<PositionDetails | null>(null)
  const [editingPlan, setEditingPlan] = useState(false)
  const [editingCompany, setEditingCompany] = useState<Instrument | null>(null)
  const [range, setRange] = useState<Range>('All')
  const [error, setError] = useState<ApiError | null>(null)

  const fetchAll = useCallback(() =>
    Promise.all([api.positionHistory(accountId, ticker), api.listPositions(), api.listAccounts(),
      api.positionDetails(accountId, ticker)])
      .then(([historyData, positions, accounts, detailsData]) => {
        setHistory(historyData)
        setDetails(detailsData)
        setPosition(positions.find((item) => item.accountId === accountId && item.ticker === historyData.ticker) ?? null)
        setAccount(accounts.find((item) => item.id === accountId) ?? null)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [accountId, ticker])

  useEffect(() => {
    void fetchAll()
  }, [fetchAll])

  function openCompanyEditor() {
    if (details?.instrumentId) {
      api.getInstrument(details.instrumentId).then(setEditingCompany).catch((e: ApiError) => setError(e))
    }
  }

  function saved() {
    setEditingPlan(false)
    setEditingCompany(null)
    void fetchAll()
  }

  const points = history ? inRange(history.points, range, today()) : []

  return (
    <>
      <a className="back" href="#positions">← Positions</a>
      <header className="page-header">
        <h1>{ticker}{account && <span className="muted"> — {account.name}</span>}</h1>
        <div className="ranges">
          {RANGES.map((option) => (
            <button key={option} aria-pressed={option === range} onClick={() => setRange(option)}>{option}</button>
          ))}
        </div>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchAll}>Try again</button>
        </p>
      )}
      {!history && !error && <p className="muted">Loading…</p>}

      {position?.value && (
        <div className="summary">
          <div><span>Value</span><strong>{formatAmount(position.value)} {position.currency}</strong></div>
          {position.unrealizedGain && (
            <div>
              <span>P/L</span>
              <strong>{formatGain(position.unrealizedGain)} {position.currency}</strong>
              {position.unrealizedPercent && <span>{formatGain(position.unrealizedPercent)}%</span>}
            </div>
          )}
          <div><span>Realized</span><strong>{formatGain(position.realizedGain)} {position.currency}</strong></div>
        </div>
      )}

      {history && !history.covered && (
        <p className="muted">No price history: the price source doesn't cover this ticker.</p>
      )}
      {history?.covered && history.points.length === 0 && (
        <p className="muted">Price history is being fetched. It can take a few minutes for a new position.</p>
      )}
      {history?.currencyMismatch && history.points.length > 0 && (
        <p className="muted">Showing the share price: it isn't in the account's currency, so value isn't available yet.</p>
      )}
      {points.length > 0 && history && (
        <PositionChart points={points} currency={history.currency} priceOnly={history.currencyMismatch} />
      )}
      {history && history.points.length > 0 && points.length === 0 && (
        <p className="muted">Nothing in this range.</p>
      )}

      {details && (
        <div className="panels">
          <PlanSection
            accountName={account?.name ?? ''}
            details={details}
            priceCurrency={position?.priceCurrency ?? position?.currency}
            onEdit={() => setEditingPlan(true)}
          />
          <CompanySection
            title={details.name || ticker}
            sector={details.sector}
            risk={details.companyRisk}
            thesis={details.thesis}
            analysis={details.analysis}
            onEdit={openCompanyEditor}
          />
        </div>
      )}
      {editingPlan && details && (
        <PlanDialog
          accountId={accountId}
          ticker={ticker}
          details={details}
          priceCurrency={position?.priceCurrency ?? position?.currency}
          onSaved={saved}
          onClose={() => setEditingPlan(false)}
        />
      )}
      {editingCompany && (
        <InstrumentDialog instrument={editingCompany} onSaved={saved} onClose={() => setEditingCompany(null)} />
      )}
    </>
  )
}
