import { useState } from 'react'
import { api } from '../api/client'
import type { Account, Transaction } from '../api/client'
import { TYPE_LABELS, details, transactionAmount } from '../transactions'
import { ConfirmDialog } from './ConfirmDialog'
import { TransactionDialog } from './TransactionDialog'

type Dialog = { kind: 'edit'; transaction: Transaction } | { kind: 'delete'; transaction: Transaction }

const OVERSOLD_HINT = 'This sells more than the earlier transactions bought. It clears once the history adds up.'

export function TransactionTable({ transactions, accounts, onChanged }: {
  transactions: Transaction[]
  accounts: Account[]
  onChanged: () => void
}) {
  const [dialog, setDialog] = useState<Dialog | null>(null)

  function saved() {
    setDialog(null)
    onChanged()
  }

  const accountName = (id: string) => accounts.find((account) => account.id === id)?.name ?? ''

  return (
    <>
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
          {transactions.map((transaction) => (
            <tr key={transaction.id}>
              <td className="nowrap">{transaction.date}</td>
              <td>{accountName(transaction.accountId)}</td>
              <td>{TYPE_LABELS[transaction.type]}</td>
              <td>
                {details(transaction)}
                {transaction.oversold && (
                  <span className="warning" role="img" aria-label={OVERSOLD_HINT} title={OVERSOLD_HINT}>⚠</span>
                )}
                {transaction.unvalidated && (
                  <span className="not-in-balance"><a href="#review">unvalidated</a>, from {transaction.createdBy}</span>
                )}
              </td>
              <td className={transaction.affectsBalance ? 'number' : 'number backfill'}>
                {transactionAmount(transaction)}
                {!transaction.affectsBalance && transaction.type !== 'split' && (
                  <span className="not-in-balance">not in balance</span>
                )}
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

      {dialog?.kind === 'edit' && (
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
            ? 'It will be removed from all your numbers, and its effect on the account balance undone.'
            : 'It will be removed from all your numbers.'}
          onConfirm={() => api.deleteTransaction(dialog.transaction.id)}
          onDone={saved}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
