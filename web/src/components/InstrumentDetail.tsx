import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Instrument, Position } from '../api/client'
import { formatAmount, formatGain } from '../format'
import { CompanySection } from './CompanySection'
import { InstrumentDialog } from './InstrumentDialog'

const positionHash = (position: Position) => `#positions/${position.accountId}/${encodeURIComponent(position.ticker)}`

export function InstrumentDetail({ id }: { id: string }) {
  const [instrument, setInstrument] = useState<Instrument | null>(null)
  const [positions, setPositions] = useState<Position[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const fetchAll = useCallback(() =>
    Promise.all([api.getInstrument(id), api.listPositions(), api.listAccounts()])
      .then(([found, positionList, accountList]) => {
        setInstrument(found)
        setPositions(positionList.filter((position) => position.instrumentId === id))
        setAccounts(accountList)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [id])

  useEffect(() => {
    void fetchAll()
  }, [fetchAll])

  const accountName = (accountId: string) => accounts.find((account) => account.id === accountId)?.name ?? ''

  return (
    <>
      <a className="back" href="#instruments">← Instruments</a>
      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchAll}>Try again</button>
        </p>
      )}
      {!instrument && !error && <p className="muted">Loading…</p>}
      {instrument && (
        <>
          <header className="page-header">
            <h1>{instrument.ticker}{instrument.name && <span className="muted"> — {instrument.name}</span>}</h1>
          </header>
          <CompanySection
            title="Company"
            sector={instrument.sector}
            risk={instrument.risk}
            thesis={instrument.thesis}
            analysis={instrument.analysis}
            onEdit={() => setEditing(true)}
          />
          <h2 className="section-title">Where you hold it</h2>
          {positions.length === 0 && <p className="muted">You don't hold this at the moment.</p>}
          {positions.length > 0 && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Account</th>
                    <th className="number">Quantity</th>
                    <th className="number">Value</th>
                    <th className="number">P/L</th>
                  </tr>
                </thead>
                <tbody>
                  {positions.map((position) => (
                    <tr key={position.accountId}>
                      <td><a href={positionHash(position)}>{accountName(position.accountId)}</a></td>
                      <td className="number">{formatAmount(position.quantity)}</td>
                      <td className="number">{position.value ? `${formatAmount(position.value)} ${position.currency}` : '—'}</td>
                      <td className="number">{position.unrealizedGain ? formatGain(position.unrealizedGain) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {editing && (
            <InstrumentDialog
              instrument={instrument}
              onSaved={() => {
                setEditing(false)
                void fetchAll()
              }}
              onClose={() => setEditing(false)}
            />
          )}
        </>
      )}
    </>
  )
}
