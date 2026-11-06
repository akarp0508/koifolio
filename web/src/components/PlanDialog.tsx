import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { PositionDetails } from '../api/client'
import { normalizeAmount } from '../format'
import { Field, TextAreaField } from './Field'
import { Modal } from './Modal'
import { RiskField } from './RiskField'

export function PlanDialog({ accountId, ticker, details, priceCurrency, onSaved, onClose }: {
  accountId: string
  ticker: string
  details: PositionDetails
  priceCurrency?: string
  onSaved: () => void
  onClose: () => void
}) {
  const [plan, setPlan] = useState(details.plan ?? '')
  const [risk, setRisk] = useState(details.risk)
  const [target, setTarget] = useState(details.targetPrice ?? '')
  const [stop, setStop] = useState(details.stopPrice ?? '')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.savePlan(accountId, ticker, {
        plan,
        risk,
        targetPrice: normalizeAmount(target) || undefined,
        stopPrice: normalizeAmount(stop) || undefined,
      })
      onSaved()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const fieldErrors = error?.fieldErrors ?? {}
  const currency = priceCurrency ? ` (${priceCurrency})` : ''
  const companyRisk = details.companyRisk === undefined ? 'not set' : details.companyRisk

  return (
    <Modal title={`My plan for ${ticker}`} onClose={onClose}>
      <form onSubmit={submit}>
        <TextAreaField
          label="Plan"
          rows={6}
          placeholder="Why do you hold it here? What would make you sell? Markdown works."
          autoFocus
          value={plan}
          error={fieldErrors.plan}
          onChange={(e) => setPlan(e.target.value)}
        />
        <div className="field-row even">
          <Field
            label={`Target price${currency}`}
            inputMode="decimal"
            value={target}
            error={fieldErrors.targetPrice}
            onChange={(e) => setTarget(e.target.value)}
          />
          <Field
            label={`Stop price${currency}`}
            inputMode="decimal"
            value={stop}
            error={fieldErrors.stopPrice}
            onChange={(e) => setStop(e.target.value)}
          />
        </div>
        <RiskField
          label="Risk in this account"
          emptyLabel={`Same as the company (${companyRisk})`}
          value={risk}
          error={fieldErrors.risk}
          onChange={setRisk}
        />
        {error && Object.keys(fieldErrors).length === 0 && <p className="error" role="alert">{error.message}</p>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>Cancel</button>
          <button className="primary" disabled={busy}>Save</button>
        </div>
      </form>
    </Modal>
  )
}
