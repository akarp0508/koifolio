import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Transaction } from '../api/client'
import { TYPE_LABELS, details, signedAmount } from '../transactions'
import { ConfirmDialog } from './ConfirmDialog'
import { TransactionDialog } from './TransactionDialog'

type Dialog = { kind: 'new' } | { kind: 'edit'; transaction: Transaction } | { kind: 'delete'; transaction: Transaction }

const ALL = ''
const OVERSOLD_HINT = 'This sells more than the earlier transactions bought. It clears once the history adds up.'

export function Transactions() {
  const [accounts, setAccounts] = useState<Account[] | null>(null)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [filter, setFilter] = useState(ALL)
  const [error, setError] = useState<ApiError | null>(null)
  const [dialog, setDialog] = useState<Dialog | null>(null)

  const fetchAll = useCallback(() =>
    Promise.all([api.listAccounts(), api.listTransactions()])
      .then(([accountList, transactionList]) => {
        setAccounts(accountList)
        setTransactions(transactionList)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void fetchAll()
  }, [fetchAll])

  function saved() {
    setDialog(null)
    void fetchAll()
  }

  const accountName = (id: string) => accounts?.find((account) => account.id === id)?.name ?? ''
  const shown = transactions.filter((transaction) => filter === ALL || transaction.accountId === filter)
  const hasAccounts = accounts !== null && accounts.length > 0

  return (
    <>
      <header className="page-header">
        <h1>Transactions</h1>
        <button className="primary" disabled={!hasAccounts} onClick={() => setDialog({ kind: 'new' })}>
          + Transaction
        </button>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchAll}>Try again</button>
        </p>
      )}
      {accounts === null && !error && <p className="muted">Loading…</p>}
      {accounts?.length === 0 && (
        <p className="muted">Transactions belong to an account. <a href="#accounts">Add an account first.</a></p>
      )}
      {hasAccounts && (
        <label className="filter">
          Account
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value={ALL}>All</option>
            {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
          </select>
        </label>
      )}
      {hasAccounts && shown.length === 0 && <p className="muted">No transactions yet.</p>}
      {shown.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Account</th>
                <th>Type</th>
                <th>Details</th>
                <th className="number">Amount</th>
                <th><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {shown.map((transaction) => (
                <tr key={transaction.id}>
                  <td className="nowrap">{transaction.date}</td>
                  <td>{accountName(transaction.accountId)}</td>
                  <td>{TYPE_LABELS[transaction.type]}</td>
                  <td>
                    {details(transaction)}
                    {transaction.oversold && (
                      <span className="warning" role="img" aria-label={OVERSOLD_HINT} title={OVERSOLD_HINT}>⚠</span>
                    )}
                  </td>
                  <td className={transaction.affectsBalance ? 'number' : 'number backfill'}>
                    {signedAmount(transaction.cashEffect)} {transaction.currency}
                    {!transaction.affectsBalance && <span className="not-in-balance">not in balance</span>}
                  </td>
                  <td className="actions">
                    <button className="icon" aria-label="Edit transaction" title="Edit"
                            onClick={() => setDialog({ kind: 'edit', transaction })}>✎</button>
                    <button className="icon" aria-label="Delete transaction" title="Delete"
                            onClick={() => setDialog({ kind: 'delete', transaction })}>✕</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {hasAccounts && dialog?.kind === 'new' && (
        <TransactionDialog
          transaction={null}
          accounts={accounts}
          initialAccountId={filter === ALL ? accounts[0].id : filter}
          onSaved={saved}
          onClose={() => setDialog(null)}
        />
      )}
      {hasAccounts && dialog?.kind === 'edit' && (
        <TransactionDialog
          transaction={dialog.transaction}
          accounts={accounts}
          initialAccountId={dialog.transaction.accountId}
          onSaved={saved}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title="Delete transaction?"
          message={dialog.transaction.affectsBalance
            ? 'It will be permanently deleted, and its effect on the account balance undone.'
            : 'It will be permanently deleted.'}
          onConfirm={() => api.deleteTransaction(dialog.transaction.id)}
          onDone={saved}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
