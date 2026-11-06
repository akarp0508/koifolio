import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { Account } from '../api/client'
import { normalizeAmount } from '../format'
import { today } from '../transactions'
import { Field } from './Field'
import { Modal } from './Modal'

export function BondDialog({ accounts, onSaved, onClose }: {
  accounts: Account[]
  onSaved: () => void
  onClose: () => void
}) {
  const plnAccounts = accounts.filter((account) => account.currency === 'PLN')
  const [series, setSeries] = useState('')
  const [accountId, setAccountId] = useState(plnAccounts[0]?.id ?? '')
  const [quantity, setQuantity] = useState('')
  const [purchaseDate, setPurchaseDate] = useState(today())
  const [rates, setRates] = useState('')
  const [earlyFee, setEarlyFee] = useState('')
  const [affectsBalance, setAffectsBalance] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)
  const fieldErrors = error?.fieldErrors ?? {}

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const input = {
      series: series.trim().toUpperCase(),
      accountId,
      quantity: Number(quantity),
      purchaseDate,
      rates: rates.split(/[\s;]+/).filter(Boolean).map(normalizeAmount) as unknown as number[],
      earlyFee: earlyFee ? normalizeAmount(earlyFee) : undefined,
      affectsBalance,
    }
    try {
      await api.createBond(input)
      onSaved()
    } catch (e) {
      setError(e as ApiError)
      setBusy(false)
    }
  }

  return (
    <Modal title="Add bonds" onClose={onClose}>
      <form onSubmit={submit}>
        <Field label="Series" placeholder="TOS0828" required value={series} error={fieldErrors.series}
               onChange={(e) => setSeries(e.target.value)} />
        <label className="field">
          <span>Account</span>
          <select value={accountId} onChange={(e) => setAccountId(e.target.value)}>
            {plnAccounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
          </select>
        </label>
        <Field label="Bonds, each 100 PLN" inputMode="numeric" required value={quantity} error={fieldErrors.quantity}
               onChange={(e) => setQuantity(e.target.value)} />
        <Field label="Purchase date" type="date" required value={purchaseDate} error={fieldErrors.purchaseDate}
               onChange={(e) => setPurchaseDate(e.target.value)} />
        <Field label="Rate in percent. For EDO, ROS and ROD, each year's so far, with spaces between" inputMode="decimal" required
               placeholder="5.40" value={rates} error={fieldErrors.rates} onChange={(e) => setRates(e.target.value)} />
        <Field label="Early redemption fee per bond (not needed for TOS)" inputMode="decimal" value={earlyFee}
               error={fieldErrors.earlyFee} onChange={(e) => setEarlyFee(e.target.value)} />
        <label className="check">
          <input type="checkbox" checked={affectsBalance} onChange={(e) => setAffectsBalance(e.target.checked)} />
          Paid from this account's cash
        </label>
        {error && Object.keys(fieldErrors).length === 0 && <p className="error" role="alert">{error.message}</p>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>Cancel</button>
          <button className="primary" disabled={busy || !accountId}>Save</button>
        </div>
      </form>
    </Modal>
  )
}
