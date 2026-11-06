import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Rule, RuleInput } from '../api/client'
import { normalizeAmount } from '../format'
import { RISK_LABELS } from '../plans'
import { Field } from './Field'
import { Modal } from './Modal'

const ALL = ''

export function RuleDialog({ rule, accounts, onSaved, onClose }: {
  rule: Rule | null
  accounts: Account[]
  onSaved: () => void
  onClose: () => void
}) {
  const [type, setType] = useState<Rule['type']>(rule?.type ?? 'max_position_weight')
  const [accountId, setAccountId] = useState(rule?.accountId ?? ALL)
  const [limitPercent, setLimitPercent] = useState(rule?.limitPercent ?? '')
  const [maxRisk, setMaxRisk] = useState(rule?.maxRisk ?? 2)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const lowRisk = type === 'min_low_risk_weight'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const input: RuleInput = {
      type,
      accountId: accountId || undefined,
      limitPercent: normalizeAmount(limitPercent),
      maxRisk: lowRisk ? maxRisk : undefined,
    }
    try {
      if (rule) {
        await api.updateRule(rule.id, input)
      } else {
        await api.createRule(input)
      }
      onSaved()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const fieldErrors = error?.fieldErrors ?? {}

  return (
    <Modal title={rule ? 'Edit rule' : 'New rule'} onClose={onClose}>
      <form onSubmit={submit}>
        <label className="field">
          <span>Rule</span>
          <select value={type} onChange={(e) => setType(e.target.value as Rule['type'])}>
            <option value="max_position_weight">Max share in one company</option>
            <option value="min_low_risk_weight">Min share in low risk, cash included</option>
          </select>
        </label>
        <label className="field">
          <span>Covers</span>
          <select value={accountId} onChange={(e) => setAccountId(e.target.value)}>
            <option value={ALL}>All accounts</option>
            {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
          </select>
        </label>
        <Field
          label="Limit (%)"
          autoFocus
          inputMode="decimal"
          required
          value={limitPercent}
          error={fieldErrors.limitPercent}
          onChange={(e) => setLimitPercent(e.target.value)}
        />
        {lowRisk && (
          <label className="field">
            <span>Counts as low risk up to</span>
            <select value={maxRisk} onChange={(e) => setMaxRisk(Number(e.target.value))}>
              {[1, 2, 3, 4, 5].map((risk) => <option key={risk} value={risk}>{RISK_LABELS[risk]}</option>)}
            </select>
          </label>
        )}
        {error && Object.keys(fieldErrors).length === 0 && <p className="error" role="alert">{error.message}</p>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>Cancel</button>
          <button className="primary" disabled={busy}>Save</button>
        </div>
      </form>
    </Modal>
  )
}
