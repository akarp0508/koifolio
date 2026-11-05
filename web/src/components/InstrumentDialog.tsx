import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Instrument } from '../api/client'
import { Field } from './Field'
import { Modal } from './Modal'

export function InstrumentDialog({ instrument, onSaved, onClose }: {
  instrument: Instrument | null
  onSaved: () => void
  onClose: () => void
}) {
  const [ticker, setTicker] = useState(instrument?.ticker ?? '')
  const [name, setName] = useState(instrument?.name ?? '')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (instrument) {
        await api.renameInstrument(instrument.id, name)
      } else {
        await api.createInstrument(ticker, name)
      }
      onSaved()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const fieldErrors = error?.fieldErrors ?? {}
  const tickerError = error?.code === 'instrument_exists' ? error.message : fieldErrors.ticker
  const shownInline = tickerError || fieldErrors.name

  return (
    <Modal title={instrument ? 'Edit instrument' : 'New instrument'} onClose={onClose}>
      <form onSubmit={submit}>
        <Field
          label="Ticker"
          autoFocus={instrument === null}
          required
          disabled={instrument !== null}
          value={ticker}
          error={tickerError}
          onChange={(e) => setTicker(e.target.value.toUpperCase())}
        />
        <Field
          label="Name"
          autoFocus={instrument !== null}
          placeholder="Optional"
          value={name}
          error={fieldErrors.name}
          onChange={(e) => setName(e.target.value)}
        />
        {error && !shownInline && <p className="error" role="alert">{error.message}</p>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>Cancel</button>
          <button className="primary" disabled={busy}>Save</button>
        </div>
      </form>
    </Modal>
  )
}
