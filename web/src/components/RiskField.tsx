import { RISK_LABELS } from '../plans'

export function RiskField({ label, emptyLabel, value, error, onChange }: {
  label: string
  emptyLabel: string
  value: number | undefined
  error?: string
  onChange: (risk: number | undefined) => void
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <select
        value={value ?? ''}
        aria-invalid={error !== undefined}
        onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))}
      >
        <option value="">{emptyLabel}</option>
        {Object.entries(RISK_LABELS).map(([risk, text]) => <option key={risk} value={risk}>{text}</option>)}
      </select>
      {error && <small className="field-error">{error}</small>}
    </label>
  )
}
