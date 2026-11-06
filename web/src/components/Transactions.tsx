import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Transaction } from '../api/client'
import { TransactionDialog } from './TransactionDialog'
import { TransactionTable } from './TransactionTable'
import { t } from '../i18n'

const ALL = ''

export function Transactions() {
  const [accounts, setAccounts] = useState<Account[] | null>(null)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [filter, setFilter] = useState(ALL)
  const [error, setError] = useState<ApiError | null>(null)
  const [adding, setAdding] = useState(false)

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
    setAdding(false)
    void fetchAll()
  }

  const shown = transactions.filter((transaction) => filter === ALL || transaction.accountId === filter)
  const hasAccounts = accounts !== null && accounts.length > 0

  return (
    <>
      <header className="page-header">
        <h1>{t('Activity')}</h1>
        <button className="primary" disabled={!hasAccounts} onClick={() => setAdding(true)}>{t('+ Transaction')}</button>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchAll}>{t('Try again')}</button>
        </p>
      )}
      {accounts === null && !error && <p className="muted">{t('Loading…')}</p>}
      {accounts?.length === 0 && (
        <p className="muted">{t('Transactions belong to an account.')}<a href="#accounts">{t('Add an account first.')}</a></p>
      )}
      {hasAccounts && (
        <label className="filter">
          {t('Account')}
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value={ALL}>{t('All')}</option>
            {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
          </select>
        </label>
      )}
      {hasAccounts && shown.length === 0 && <p className="muted">{t('No transactions yet.')}</p>}
      {hasAccounts && shown.length > 0 && <TransactionTable transactions={shown} accounts={accounts} onChanged={fetchAll} />}

      {hasAccounts && adding && (
        <TransactionDialog
          transaction={null}
          accounts={accounts}
          initialAccountId={filter === ALL ? accounts[0].id : filter}
          onSaved={saved}
          onClose={() => setAdding(false)}
        />
      )}
    </>
  )
}
