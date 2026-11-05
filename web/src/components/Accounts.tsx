import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account } from '../api/client'
import { formatAmount } from '../format'
import { AccountDialog } from './AccountDialog'
import { ConfirmDialog } from './ConfirmDialog'

type Dialog = { kind: 'new' } | { kind: 'edit'; account: Account } | { kind: 'delete'; account: Account }

export function Accounts() {
  const [accounts, setAccounts] = useState<Account[] | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [dialog, setDialog] = useState<Dialog | null>(null)

  const fetchAccounts = useCallback(() =>
    api.listAccounts()
      .then((list) => {
        setAccounts(list)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void fetchAccounts()
  }, [fetchAccounts])

  function saved() {
    setDialog(null)
    void fetchAccounts()
  }

  return (
    <>
      <header className="page-header">
        <h1>Accounts</h1>
        <button className="primary" onClick={() => setDialog({ kind: 'new' })}>+ Account</button>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchAccounts}>Try again</button>
        </p>
      )}
      {accounts === null && !error && <p className="muted">Loading…</p>}
      {accounts?.length === 0 && <p className="muted">No accounts yet. Add your first one.</p>}
      {accounts && accounts.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Description</th>
                <th className="number">Balance</th>
                <th><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>{account.name}</td>
                  <td className="muted">{account.description}</td>
                  <td className="number">{formatAmount(account.balance)} {account.currency}</td>
                  <td className="actions">
                    <button className="icon" aria-label={`Edit ${account.name}`} title="Edit"
                            onClick={() => setDialog({ kind: 'edit', account })}>✎</button>
                    <button className="icon" aria-label={`Delete ${account.name}`} title="Delete"
                            onClick={() => setDialog({ kind: 'delete', account })}>✕</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {dialog?.kind === 'new' && <AccountDialog account={null} onSaved={saved} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'edit' && (
        <AccountDialog account={dialog.account} onSaved={saved} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title="Delete account?"
          message={`“${dialog.account.name}” and all its transactions will be permanently deleted.`}
          onConfirm={() => api.deleteAccount(dialog.account.id)}
          onDone={saved}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
