import type { InputHTMLAttributes } from 'react'

type FieldProps = InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }

export function Field({ label, error, ...input }: FieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <input aria-invalid={error !== undefined} {...input} />
      {error && <small className="field-error">{error}</small>}
    </label>
  )
}
