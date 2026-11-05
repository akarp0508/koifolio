import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Transaction, TransactionInput, TransactionType } from '../api/client'
import { normalizeAmount } from '../format'
import { TYPES, TYPE_LABELS, defaultAffectsBalance, isTrade, today } from '../transactions'
import { Field } from './Field'
import { Modal } from './Modal'

const optionalAmount = (value: string) => normalizeAmount(value) || undefined

export function TransactionDialog({ transaction, accounts, initialAccountId, onSaved, onClose }: {
  transaction: Transaction | null
  accounts: Account[]
  initialAccountId: string
  onSaved: () => void
  onClose: () => void
}) {
  const [accountId, setAccountId] = useState(transaction?.accountId ?? initialAccountId)
  const [type, setType] = useState<TransactionType>(transaction?.type ?? 'buy')
  const [date, setDate] = useState(transaction?.date ?? today())
  const [ticker, setTicker] = useState(transaction?.ticker ?? '')
  const [quantity, setQuantity] = useState(transaction?.quantity ?? '')
  const [price, setPrice] = useState(transaction?.price ?? '')
  const [fee, setFee] = useState(transaction?.fee ?? '')
  const [amount, setAmount] = useState(transaction?.amount ?? '')
  const [withheldTax, setWithheldTax] = useState(transaction?.withheldTax ?? '')
  // null until decided by hand, so the default can follow the date
  const [chosenAffectsBalance, setChosenAffectsBalance] = useState<boolean | null>(transaction?.affectsBalance ?? null)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const account = accounts.find((candidate) => candidate.id === accountId)
  const balanceSetOn = account?.balanceSetOn ?? ''
  const suggested = defaultAffectsBalance(date, balanceSetOn)
  const affectsBalance = chosenAffectsBalance ?? suggested
  const dividend = type === 'dividend'
  const trade = isTrade(type)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const input: TransactionInput = {
      accountId,
      type,
      date,
      ticker: trade || dividend ? ticker : undefined,
      quantity: trade ? normalizeAmount(quantity) : undefined,
      price: trade ? normalizeAmount(price) : undefined,
      fee: trade ? optionalAmount(fee) : undefined,
      amount: trade ? undefined : normalizeAmount(amount),
      withheldTax: dividend ? optionalAmount(withheldTax) : undefined,
      affectsBalance,
    }
    try {
      if (transaction) {
        await api.updateTransaction(transaction.id, input)
      } else {
        await api.createTransaction(input)
      }
      onSaved()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const fieldErrors = error?.fieldErrors ?? {}
  const currency = account?.currency ?? ''

  return (
    <Modal title={transaction ? 'Edit transaction' : 'New transaction'} onClose={onClose}>
      <form onSubmit={submit}>
        <div className="field-row even">
          <label className="field">
            <span>Account</span>
            <select value={accountId} disabled={transaction !== null} onChange={(e) => setAccountId(e.target.value)}>
              {accounts.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
            </select>
          </label>
          <label className="field">
            <span>Type</span>
            <select value={type} onChange={(e) => setType(e.target.value as TransactionType)}>
              {TYPES.map((option) => <option key={option} value={option}>{TYPE_LABELS[option]}</option>)}
            </select>
          </label>
        </div>
        <Field
          label="Date"
          type="date"
          required
          value={date}
          error={fieldErrors.date}
          onChange={(e) => setDate(e.target.value)}
        />
        {(trade || dividend) && (
          <Field
            label="Ticker"
            required
            value={ticker}
            error={fieldErrors.ticker}
            onChange={(e) => setTicker(e.target.value.toUpperCase())}
          />
        )}
        {trade && (
          <>
            <div className="field-row even">
              <Field
                label="Quantity"
                inputMode="decimal"
                required
                value={quantity}
                error={fieldErrors.quantity}
                onChange={(e) => setQuantity(e.target.value)}
              />
              <Field
                label={`Price (${currency})`}
                inputMode="decimal"
                required
                value={price}
                error={fieldErrors.price}
                onChange={(e) => setPrice(e.target.value)}
              />
            </div>
            <Field
              label={`Fee (${currency})`}
              inputMode="decimal"
              placeholder="0.00"
              value={fee}
              error={fieldErrors.fee}
              onChange={(e) => setFee(e.target.value)}
            />
          </>
        )}
        {!trade && (
          <Field
            label={`${dividend ? 'Gross amount' : 'Amount'} (${currency})`}
            inputMode="decimal"
            required
            value={amount}
            error={fieldErrors.amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        )}
        {dividend && (
          <Field
            label={`Tax withheld (${currency})`}
            inputMode="decimal"
            placeholder="0.00"
            value={withheldTax}
            error={fieldErrors.withheldTax}
            onChange={(e) => setWithheldTax(e.target.value)}
          />
        )}
        <label className="check">
          <input
            type="checkbox"
            checked={affectsBalance}
            onChange={(e) => setChosenAffectsBalance(e.target.checked)}
          />
          <span>Update account balance</span>
        </label>
        {!suggested && chosenAffectsBalance === null && (
          <small className="check-hint">Off: dated before the balance was set on {balanceSetOn}</small>
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
