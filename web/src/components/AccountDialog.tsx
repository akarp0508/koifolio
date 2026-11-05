import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, CostMethod } from '../api/client'
import { normalizeAmount } from '../format'
import { Field } from './Field'
import { Modal } from './Modal'

const COMMON_CURRENCIES = ['PLN', 'USD', 'EUR', 'GBP', 'CHF']

export function AccountDialog({ account, onSaved, onClose }: {
  account: Account | null
  onSaved: () => void
  onClose: () => void
}) {
  const [name, setName] = useState(account?.name ?? '')
  const [description, setDescription] = useState(account?.description ?? '')
  const [currency, setCurrency] = useState(account?.currency ?? 'PLN')
  const [balance, setBalance] = useState(account?.balance ?? '')
  const [costMethod, setCostMethod] = useState<CostMethod>(account?.costMethod ?? 'average')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const amount = normalizeAmount(balance) || undefined
    try {
      if (account) {
        await api.updateAccount(account.id, { name, description, balance: amount, costMethod })
      } else {
        await api.createAccount({ name, description, currency, balance: amount, costMethod })
      }
      onSaved()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const fieldErrors = error?.fieldErrors ?? {}
  const amountError = error?.code === 'invalid_amount' ? error.message : fieldErrors.balance
  const currencyError = error?.code === 'unknown_currency' ? error.message : fieldErrors.currency
  const shownInline = Object.keys(fieldErrors).length > 0 || amountError || currencyError

  return (
    <Modal title={account ? 'Edit account' : 'New account'} onClose={onClose}>
      <form onSubmit={submit}>
        <Field
          label="Name"
          autoFocus
          required
          value={name}
          error={fieldErrors.name}
          onChange={(e) => setName(e.target.value)}
        />
        <Field
          label="Description"
          value={description}
          error={fieldErrors.description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <div className="field-row">
          <Field
            label="Balance"
            inputMode="decimal"
            placeholder="0.00"
            required={account !== null}
            value={balance}
            error={amountError}
            onChange={(e) => setBalance(e.target.value)}
          />
          <Field
            label="Currency"
            list="currencies"
            maxLength={3}
            required
            disabled={account !== null}
            value={currency}
            error={currencyError}
            onChange={(e) => setCurrency(e.target.value.toUpperCase())}
          />
          <datalist id="currencies">
            {COMMON_CURRENCIES.map((code) => <option key={code} value={code} />)}
          </datalist>
        </div>
        <label className="field">
          <span>Cost method</span>
          <select value={costMethod} onChange={(e) => setCostMethod(e.target.value as CostMethod)}>
            <option value="average">Average cost</option>
            <option value="fifo">FIFO (oldest shares sold first)</option>
          </select>
        </label>
        {error && !shownInline && <p className="error" role="alert">{error.message}</p>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>Cancel</button>
          <button className="primary" disabled={busy}>Save</button>
        </div>
      </form>
    </Modal>
  )
}
