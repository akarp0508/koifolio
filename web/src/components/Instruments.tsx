import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Instrument } from '../api/client'
import { ConfirmDialog } from './ConfirmDialog'
import { InstrumentDialog } from './InstrumentDialog'

type Dialog = { kind: 'new' } | { kind: 'edit'; instrument: Instrument } | { kind: 'delete'; instrument: Instrument }

export function Instruments() {
  const [instruments, setInstruments] = useState<Instrument[] | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [dialog, setDialog] = useState<Dialog | null>(null)

  const fetchInstruments = useCallback(() =>
    api.listInstruments()
      .then((list) => {
        setInstruments(list)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void fetchInstruments()
  }, [fetchInstruments])

  function saved() {
    setDialog(null)
    void fetchInstruments()
  }

  return (
    <>
      <header className="page-header">
        <h1>Instruments</h1>
        <button className="primary" onClick={() => setDialog({ kind: 'new' })}>+ Instrument</button>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchInstruments}>Try again</button>
        </p>
      )}
      {instruments === null && !error && <p className="muted">Loading…</p>}
      {instruments?.length === 0 && (
        <p className="muted">No instruments yet. Add some here, or use a new ticker in a transaction.</p>
      )}
      {instruments && instruments.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Ticker</th>
                <th>Name</th>
                <th><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {instruments.map((instrument) => (
                <tr key={instrument.id}>
                  <td>{instrument.ticker}</td>
                  <td className="muted">{instrument.name}</td>
                  <td className="actions">
                    <button className="icon" aria-label={`Edit ${instrument.ticker}`} title="Edit"
                            onClick={() => setDialog({ kind: 'edit', instrument })}>✎</button>
                    <button className="icon" aria-label={`Remove ${instrument.ticker}`} title="Remove"
                            onClick={() => setDialog({ kind: 'delete', instrument })}>✕</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {dialog?.kind === 'new' && <InstrumentDialog instrument={null} onSaved={saved} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'edit' && (
        <InstrumentDialog instrument={dialog.instrument} onSaved={saved} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title={`Remove ${dialog.instrument.ticker}?`}
          message="It leaves your list. Transactions keep their ticker, and using it again adds it back."
          onConfirm={() => api.deleteInstrument(dialog.instrument.id)}
          onDone={saved}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
