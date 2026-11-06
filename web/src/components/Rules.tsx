import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Rule, RuleResult } from '../api/client'
import { ruleLabel } from '../rules'
import { ConfirmDialog } from './ConfirmDialog'
import { RuleDialog } from './RuleDialog'

type Dialog = { kind: 'new' } | { kind: 'edit'; rule: Rule } | { kind: 'delete'; rule: Rule }

export function Rules() {
  const [results, setResults] = useState<RuleResult[] | null>(null)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  const fetchAll = useCallback(() =>
    Promise.all([api.checkRules(), api.listAccounts()])
      .then(([resultList, accountList]) => {
        setResults(resultList)
        setAccounts(accountList)
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

  const accountName = (id?: string) => id ? accounts.find((account) => account.id === id)?.name ?? '' : 'All accounts'

  return (
    <>
      <header className="page-header">
        <h1>Rules</h1>
        <button className="primary" onClick={() => setDialog({ kind: 'new' })}>+ Rule</button>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchAll}>Try again</button>
        </p>
      )}
      {results === null && !error && <p className="muted">Loading…</p>}
      {results?.length === 0 && <p className="muted">No rules yet. Add a limit to have it checked against your holdings.</p>}
      {results && results.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Rule</th>
                <th>Covers</th>
                <th className="number">Now</th>
                <th>Status</th>
                <th><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {results.map(({ rule, passed, actual, breaches, unpriced }) => (
                <tr key={rule.id}>
                  <td>{ruleLabel(rule)}</td>
                  <td>{accountName(rule.accountId)}</td>
                  <td className="number">{actual ? `${actual}%` : '—'}</td>
                  <td>
                    <span className={passed ? 'gain' : 'loss'}>{passed ? 'Pass' : 'Fail'}</span>
                    {breaches.length > 0 && (
                      <span className="not-in-balance">
                        {breaches.map((breach) => `${breach.ticker} ${breach.weight}%`).join(', ')}
                      </span>
                    )}
                    {unpriced > 0 && <span className="not-in-balance">{unpriced} without a price not counted</span>}
                  </td>
                  <td className="actions">
                    <button className="icon" aria-label="Edit rule" title="Edit"
                            onClick={() => setDialog({ kind: 'edit', rule })}>✎</button>
                    <button className="icon" aria-label="Delete rule" title="Delete"
                            onClick={() => setDialog({ kind: 'delete', rule })}>✕</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {dialog?.kind === 'new' && <RuleDialog rule={null} accounts={accounts} onSaved={saved} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'edit' && <RuleDialog rule={dialog.rule} accounts={accounts} onSaved={saved} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title="Delete rule?"
          message={ruleLabel(dialog.rule)}
          onConfirm={() => api.deleteRule(dialog.rule.id)}
          onDone={saved}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
