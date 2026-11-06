import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, AuditEntry, Transaction } from '../api/client'
import { TYPE_LABELS, details, transactionAmount } from '../transactions'
import { ConfirmDialog } from './ConfirmDialog'

const who = (entry: AuditEntry) => entry.actorType === 'AGENT' ? entry.actorName : 'You'

export function Review() {
  const [waiting, setWaiting] = useState<Transaction[] | null>(null)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [log, setLog] = useState<AuditEntry[]>([])
  const [rejecting, setRejecting] = useState<Transaction | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  const load = useCallback(() =>
    Promise.all([api.listUnvalidated(), api.listAccounts(), api.auditLog()])
      .then(([transactions, accountList, entries]) => {
        setWaiting(transactions)
        setAccounts(accountList)
        setLog(entries)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void load()
  }, [load])

  function run(action: Promise<unknown>) {
    action.then(load).catch((e: ApiError) => setError(e))
  }

  const accountName = (id: string) => accounts.find((account) => account.id === id)?.name ?? ''

  return (
    <>
      <header className="page-header">
        <h1>Review</h1>
        {(waiting?.length ?? 0) > 1 && (
          <button className="primary" onClick={() => run(api.approveAllTransactions())}>Approve all</button>
        )}
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={load}>Try again</button>
        </p>
      )}
      {!waiting && !error && <p className="muted">Loading…</p>}

      {waiting && (
        <>
          <p className="muted">
            Transactions from agents count straight away. Approving clears the mark, and rejecting takes them back out.
          </p>

          {waiting.length === 0 && <p className="muted">Nothing is waiting for review.</p>}
          {waiting.length > 0 && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Account</th>
                    <th>Type</th>
                    <th>Details</th>
                    <th className="number">Amount</th>
                    <th>From</th>
                    <th><span className="visually-hidden">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {waiting.map((transaction) => (
                    <tr key={transaction.id}>
                      <td className="nowrap">{transaction.date}</td>
                      <td>{accountName(transaction.accountId)}</td>
                      <td>{TYPE_LABELS[transaction.type]}</td>
                      <td>{details(transaction)}</td>
                      <td className="number">{transactionAmount(transaction)}</td>
                      <td>{transaction.createdBy}</td>
                      <td className="actions">
                        <button className="trade buy" aria-label={`Approve ${details(transaction)}`}
                                onClick={() => run(api.approveTransaction(transaction.id))}>Approve</button>
                        <button className="trade sell" aria-label={`Reject ${details(transaction)}`}
                                onClick={() => setRejecting(transaction)}>Reject</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <h2 className="section-title">Change log</h2>
          {log.length === 0 && <p className="muted">Changes to transactions, companies and rules will show here.</p>}
          {log.length > 0 && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Who</th>
                    <th>What</th>
                  </tr>
                </thead>
                <tbody>
                  {log.map((entry) => (
                    <tr key={entry.id}>
                      <td className="nowrap">{entry.createdAt ? new Date(entry.createdAt).toLocaleString() : ''}</td>
                      <td>{who(entry)}</td>
                      <td>{entry.summary}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {rejecting && (
        <ConfirmDialog
          title="Reject this transaction?"
          message="It will be removed from all your numbers, and its effect on the account balance undone."
          action="Reject"
          onConfirm={() => api.rejectTransaction(rejecting.id)}
          onDone={() => {
            setRejecting(null)
            void load()
          }}
          onClose={() => setRejecting(null)}
        />
      )}
    </>
  )
}
