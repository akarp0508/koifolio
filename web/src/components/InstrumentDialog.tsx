import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Instrument } from '../api/client'
import { parseTags, usedSectors } from '../plans'
import { Field, TextAreaField } from './Field'
import { Modal } from './Modal'
import { RiskField } from './RiskField'

export function InstrumentDialog({ instrument, onSaved, onClose }: {
  instrument: Instrument | null
  onSaved: () => void
  onClose: () => void
}) {
  const [ticker, setTicker] = useState(instrument?.ticker ?? '')
  const [name, setName] = useState(instrument?.name ?? '')
  const [sector, setSector] = useState(instrument?.sector ?? '')
  const [risk, setRisk] = useState(instrument?.risk)
  const [thesis, setThesis] = useState(instrument?.thesis ?? '')
  const [analysis, setAnalysis] = useState(instrument?.analysis ?? '')
  const [tags, setTags] = useState((instrument?.tags ?? []).join(', '))
  const [sectors, setSectors] = useState<string[]>([])
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    // Only suggestions: any sector can still be typed
    api.listInstruments().then((list) => setSectors(usedSectors(list))).catch(() => {})
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const details = { name, sector, risk, thesis, analysis, tags: parseTags(tags) }
    try {
      if (instrument) {
        await api.updateInstrument(instrument.id, details)
      } else {
        await api.createInstrument({ ticker, ...details })
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
  const shownInline = tickerError || Object.keys(fieldErrors).length > 0

  return (
    <Modal title={instrument ? `Edit ${instrument.ticker}` : 'New instrument'} onClose={onClose}>
      <form onSubmit={submit}>
        <div className="field-row even">
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
        </div>
        <div className="field-row even">
          <Field
            label="Sector"
            list="sectors"
            placeholder="Optional"
            value={sector}
            error={fieldErrors.sector}
            onChange={(e) => setSector(e.target.value)}
          />
          <datalist id="sectors">
            {sectors.map((option) => <option key={option} value={option} />)}
          </datalist>
          <RiskField label="Risk" emptyLabel="Not set" value={risk} error={fieldErrors.risk} onChange={setRisk} />
        </div>
        <Field
          label="Thesis"
          placeholder="Why this company, in a sentence"
          value={thesis}
          error={fieldErrors.thesis}
          onChange={(e) => setThesis(e.target.value)}
        />
        <Field
          label="Company tags"
          placeholder="Comma separated, e.g. dividend, long-term"
          value={tags}
          onChange={(e) => setTags(e.target.value)}
        />
        <TextAreaField
          label="Analysis"
          rows={10}
          placeholder="Markdown works."
          value={analysis}
          error={fieldErrors.analysis}
          onChange={(e) => setAnalysis(e.target.value)}
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
