import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { PositionDetails } from '../api/client'
import { normalizeAmount } from '../format'
import { parseTags } from '../plans'
import { Field, TextAreaField } from './Field'
import { Modal } from './Modal'
import { RiskField } from './RiskField'
import { t } from '../i18n'

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
  const [tags, setTags] = useState((details.positionTags ?? []).join(', '))
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
        tags: parseTags(tags),
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
  const companyRisk = details.companyRisk === undefined ? t('not set') : details.companyRisk

  return (
    <Modal title={t('My plan for {name}', { name: ticker })} onClose={onClose}>
      <form onSubmit={submit}>
        <TextAreaField
          label={t('Plan')}
          rows={6}
          placeholder={t('Why do you hold it here? What would make you sell? Markdown works.')}
          autoFocus
          value={plan}
          error={fieldErrors.plan}
          onChange={(e) => setPlan(e.target.value)}
        />
        <div className="field-row even">
          <Field
            label={`${t('Target price')}${currency}`}
            inputMode="decimal"
            value={target}
            error={fieldErrors.targetPrice}
            onChange={(e) => setTarget(e.target.value)}
          />
          <Field
            label={`${t('Stop price')}${currency}`}
            inputMode="decimal"
            value={stop}
            error={fieldErrors.stopPrice}
            onChange={(e) => setStop(e.target.value)}
          />
        </div>
        <RiskField
          label={t('Risk in this account')}
          emptyLabel={t('Same as the company ({risk})', { risk: companyRisk })}
          value={risk}
          error={fieldErrors.risk}
          onChange={setRisk}
        />
        <Field
          label={t('Position tags')}
          placeholder={t('Comma separated, e.g. core, swing')}
          value={tags}
          onChange={(e) => setTags(e.target.value)}
        />
        {error && Object.keys(fieldErrors).length === 0 && <p className="error" role="alert">{error.message}</p>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>{t('Cancel')}</button>
          <button className="primary" disabled={busy}>{t('Save')}</button>
        </div>
      </form>
    </Modal>
  )
}
