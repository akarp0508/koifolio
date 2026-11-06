import type { InputHTMLAttributes, TextareaHTMLAttributes } from 'react'

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

type TextAreaFieldProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string }

export function TextAreaField({ label, error, ...textarea }: TextAreaFieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <textarea aria-invalid={error !== undefined} {...textarea} />
      {error && <small className="field-error">{error}</small>}
    </label>
  )
}
