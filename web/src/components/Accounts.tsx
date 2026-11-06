import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account } from '../api/client'
import { formatAmount } from '../format'
import { AccountDialog } from './AccountDialog'
import { ConfirmDialog } from './ConfirmDialog'
import { t } from '../i18n'

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
        <h1>{t('Accounts')}</h1>
        <button className="primary" onClick={() => setDialog({ kind: 'new' })}>{t('+ Account')}</button>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchAccounts}>{t('Try again')}</button>
        </p>
      )}
      {accounts === null && !error && <p className="muted">{t('Loading…')}</p>}
      {accounts?.length === 0 && <p className="muted">{t('No accounts yet. Add your first one.')}</p>}
      {accounts && accounts.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>{t('Name')}</th>
                <th>{t('Description')}</th>
                <th className="number">{t('Balance')}</th>
                <th><span className="visually-hidden">{t('Actions')}</span></th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>{account.name}</td>
                  <td className="muted">{account.description}</td>
                  <td className="number">{formatAmount(account.balance)} {account.currency}</td>
                  <td className="actions">
                    <button className="icon" aria-label={t('Edit {name}', { name: account.name })} title={t('Edit')}
                            onClick={() => setDialog({ kind: 'edit', account })}>✎</button>
                    <button className="icon" aria-label={t('Delete {name}', { name: account.name })} title={t('Delete')}
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
          title={t('Delete account?')}
          message={t('“{name}” and all its transactions will be permanently deleted.', { name: dialog.account.name })}
          onConfirm={() => api.deleteAccount(dialog.account.id)}
          onDone={saved}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
