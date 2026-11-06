import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Instrument, Transaction, TransactionInput, TransactionType } from '../api/client'
import { normalizeAmount } from '../format'
import { TYPES, TYPE_LABELS, defaultAffectsBalance, isTrade, tickerOptions, today } from '../transactions'
import { Field } from './Field'
import { Modal } from './Modal'

const optionalAmount = (value: string) => normalizeAmount(value) || undefined
const NEW_TICKER = 'new-ticker'

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
  const [splitFrom, setSplitFrom] = useState(transaction?.splitFrom ?? '1')
  const [splitTo, setSplitTo] = useState(transaction?.splitTo ?? '')
  // null until decided by hand, so the default can follow the date
  const [chosenAffectsBalance, setChosenAffectsBalance] = useState<boolean | null>(transaction?.affectsBalance ?? null)
  const [instruments, setInstruments] = useState<Instrument[] | null>(null)
  const [typingTicker, setTypingTicker] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  // The server's question when saving would take the balance below zero
  const [balanceWarning, setBalanceWarning] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    // If the list can't load, the ticker can still be typed
    api.listInstruments().then(setInstruments).catch(() => setInstruments([]))
  }, [])

  const account = accounts.find((candidate) => candidate.id === accountId)
  const balanceSetOn = account?.balanceSetOn ?? ''
  const suggested = defaultAffectsBalance(date, balanceSetOn)
  const affectsBalance = chosenAffectsBalance ?? suggested
  const dividend = type === 'dividend'
  const trade = isTrade(type)
  const split = type === 'split'

  async function save(allowNegativeBalance: boolean) {
    setBusy(true)
    setError(null)
    setBalanceWarning(null)
    const input: TransactionInput = {
      accountId,
      type,
      date,
      ticker: trade || dividend || split ? ticker : undefined,
      quantity: trade ? normalizeAmount(quantity) : undefined,
      price: trade ? normalizeAmount(price) : undefined,
      fee: trade ? optionalAmount(fee) : undefined,
      amount: trade || split ? undefined : normalizeAmount(amount),
      withheldTax: dividend ? optionalAmount(withheldTax) : undefined,
      splitFrom: split ? normalizeAmount(splitFrom) : undefined,
      splitTo: split ? normalizeAmount(splitTo) : undefined,
      affectsBalance,
      allowNegativeBalance,
    }
    try {
      if (transaction) {
        await api.updateTransaction(transaction.id, input)
      } else {
        await api.createTransaction(input)
      }
      onSaved()
    } catch (e) {
      const failure = e as ApiError
      if (failure.code === 'negative_balance') {
        setBalanceWarning(failure.message)
      } else {
        setError(failure)
      }
    } finally {
      setBusy(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void save(false)
  }

  const fieldErrors = error?.fieldErrors ?? {}
  const currency = account?.currency ?? ''

  return (
    <Modal title={transaction ? 'Edit transaction' : 'New transaction'} onClose={onClose}>
      <form onSubmit={submit} onChange={() => setBalanceWarning(null)}>
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
        {(trade || dividend || split) && (typingTicker || instruments?.length === 0 ? (
          <>
            <Field
              label="Ticker"
              autoFocus={typingTicker}
              required
              value={ticker}
              error={fieldErrors.ticker}
              onChange={(e) => setTicker(e.target.value.toUpperCase())}
            />
            {typingTicker && (
              <button type="button" className="link field-link" onClick={() => {
                setTypingTicker(false)
                setTicker('')
              }}>Choose from my list</button>
            )}
          </>
        ) : (
          <label className="field">
            <span>Ticker</span>
            <select
              required
              disabled={instruments === null}
              value={ticker}
              aria-invalid={fieldErrors.ticker !== undefined}
              onChange={(e) => {
                if (e.target.value === NEW_TICKER) {
                  setTypingTicker(true)
                  setTicker('')
                } else {
                  setTicker(e.target.value)
                }
              }}
            >
              <option value="" disabled>{instruments === null ? 'Loading…' : 'Choose…'}</option>
              {tickerOptions(instruments ?? [], ticker).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
              <option value={NEW_TICKER}>New ticker…</option>
            </select>
            {fieldErrors.ticker && <small className="field-error">{fieldErrors.ticker}</small>}
          </label>
        ))}
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
        {split && (
          <div className="field-row even">
            <Field
              label="New shares"
              inputMode="decimal"
              placeholder="10"
              required
              value={splitTo}
              error={fieldErrors.splitTo}
              onChange={(e) => setSplitTo(e.target.value)}
            />
            <Field
              label="For every old shares"
              inputMode="decimal"
              required
              value={splitFrom}
              error={fieldErrors.splitFrom}
              onChange={(e) => setSplitFrom(e.target.value)}
            />
          </div>
        )}
        {!trade && !split && (
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
        {!split && (
          <label className="check">
            <input
              type="checkbox"
              checked={affectsBalance}
              onChange={(e) => setChosenAffectsBalance(e.target.checked)}
            />
            <span>Update account balance</span>
          </label>
        )}
        {!split && !suggested && chosenAffectsBalance === null && (
          <small className="check-hint">Off: dated before the balance was set on {balanceSetOn}</small>
        )}
        {error && Object.keys(fieldErrors).length === 0 && <p className="error" role="alert">{error.message}</p>}
        {balanceWarning ? (
          <div className="confirm" role="alert">
            <p>{balanceWarning}. Save anyway?</p>
            <div className="modal-actions">
              <button type="button" onClick={() => setBalanceWarning(null)}>Go back</button>
              <button type="button" className="primary" disabled={busy} onClick={() => save(true)}>Save anyway</button>
            </div>
          </div>
        ) : (
          <div className="modal-actions">
            <button type="button" onClick={onClose}>Cancel</button>
            <button className="primary" disabled={busy}>Save</button>
          </div>
        )}
      </form>
    </Modal>
  )
}
