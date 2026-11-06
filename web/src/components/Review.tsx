import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, AuditEntry, PendingChange, Transaction } from '../api/client'
import { fieldChanges } from '../auditChanges'
import { reviewChanged } from '../reviewCount'
import { TYPE_LABELS, details, transactionAmount } from '../transactions'
import { ConfirmDialog } from './ConfirmDialog'
import { t } from '../i18n'

const ALL = ''

const who = (entry: AuditEntry) => entry.actorType === 'AGENT' ? entry.actorName : t('You')

/** The imports among these transactions, newest first. */
function importsIn(transactions: Transaction[]) {
  const counts = new Map<string, { count: number; createdAt: string }>()
  for (const transaction of transactions) {
    if (transaction.importId) {
      const found = counts.get(transaction.importId)
      counts.set(transaction.importId, { count: (found?.count ?? 0) + 1, createdAt: transaction.createdAt ?? '' })
    }
  }
  return [...counts].sort(([, a], [, b]) => b.createdAt.localeCompare(a.createdAt)).map(([id, { count, createdAt }]) => ({
    id,
    label: t('{date}, {count} items', { date: new Date(createdAt).toLocaleDateString(), count }),
  }))
}

function Changes({ before, after, beforeLabel, afterLabel }: {
  before: unknown
  after: unknown
  beforeLabel: string
  afterLabel: string
}) {
  const changes = fieldChanges(before, after)
  if (changes.length === 0) {
    return <p className="muted">{t('No values changed.')}</p>
  }

  return (
    <table className="log-changes">
      <thead>
        <tr>
          <th>{t('Field')}</th>
          <th>{beforeLabel}</th>
          <th>{afterLabel}</th>
        </tr>
      </thead>
      <tbody>
        {changes.map((change) => (
          <tr key={change.field}>
            <td>{change.field}</td>
            <td><div className="log-value">{change.before ?? '—'}</div></td>
            <td><div className="log-value">{change.after ?? '—'}</div></td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function ChangeRow({ change, onApprove, onReject }: {
  change: PendingChange
  onApprove: () => void
  onReject: () => void
}) {
  return (
    <section className="panel">
      <header>
        <h2>{change.summary}</h2>
        <div className="actions">
          <button className="trade buy" aria-label={t('Approve {name}', { name: change.summary ?? '' })} onClick={onApprove}>{t('Approve')}</button>
          <button className="trade sell" aria-label={t('Reject {name}', { name: change.summary ?? '' })} onClick={onReject}>{t('Reject')}</button>
        </div>
      </header>
      <p className="muted">From {change.agentName}</p>
      <Changes before={change.current} after={change.proposed} beforeLabel={t('Now')} afterLabel={t('Proposed')} />
    </section>
  )
}

function LogEntry({ entry, onRevert }: { entry: AuditEntry; onRevert: () => void }) {
  const [open, setOpen] = useState(false)

  return (
    <>
      <tr className="clickable" onClick={() => setOpen(!open)}>
        <td className="nowrap">
          <button className="expand" aria-expanded={open} aria-label={t('Details of {name}', { name: entry.summary ?? '' })}>{open ? '▾' : '▸'}</button>
          {entry.createdAt ? new Date(entry.createdAt).toLocaleString() : ''}
        </td>
        <td>{who(entry)}</td>
        <td>{entry.summary}</td>
        <td className="actions">
          {entry.revertible && (
            <button
              className="link"
              aria-label={t('Revert {name}', { name: entry.summary ?? '' })}
              onClick={(event) => {
                event.stopPropagation()
                onRevert()
              }}
            >{t('Revert')}</button>
          )}
        </td>
      </tr>
      {open && (
        <tr>
          <td colSpan={4}>
            <Changes before={entry.before} after={entry.after} beforeLabel={t('Before')} afterLabel={t('After')} />
          </td>
        </tr>
      )}
    </>
  )
}

export function Review() {
  const [waiting, setWaiting] = useState<Transaction[] | null>(null)
  const [changes, setChanges] = useState<PendingChange[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [log, setLog] = useState<AuditEntry[]>([])
  const [agent, setAgent] = useState(ALL)
  const [importId, setImportId] = useState(ALL)
  const [rejecting, setRejecting] = useState<Transaction | null>(null)
  const [rejectingAll, setRejectingAll] = useState(false)
  const [reverting, setReverting] = useState<AuditEntry | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  const load = useCallback(() =>
    Promise.all([api.listUnvalidated(), api.pendingChanges(), api.listAccounts(), api.auditLog()])
      .then(([transactions, pending, accountList, entries]) => {
        setWaiting(transactions)
        setChanges(pending)
        setAccounts(accountList)
        setLog(entries)
        setError(null)
        reviewChanged()
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void load()
  }, [load])

  function run(action: Promise<unknown>) {
    action.then(load).catch((e: ApiError) => setError(e))
  }

  const accountName = (id: string) => accounts.find((account) => account.id === id)?.name ?? ''
  const agents = [...new Set([
    ...(waiting ?? []).map((transaction) => transaction.createdBy ?? ''),
    ...changes.map((change) => change.agentName ?? ''),
  ])].sort()
  const shownAgent = agents.includes(agent) ? agent : ALL
  const agentsTransactions = (waiting ?? []).filter((transaction) => !shownAgent || transaction.createdBy === shownAgent)
  const imports = importsIn(agentsTransactions)
  const shownImport = imports.some((found) => found.id === importId) ? importId : ALL
  const shownTransactions = agentsTransactions.filter((transaction) => !shownImport || transaction.importId === shownImport)
  const shownChanges = changes.filter((change) => !shownImport && (!shownAgent || change.agentName === shownAgent))
  const total = shownTransactions.length + shownChanges.length

  return (
    <>
      <header className="page-header">
        <h1>{t('Review')}</h1>
        {agents.length > 1 && (
          <label className="filter-check">
            {t('From')}
            <select value={shownAgent} onChange={(e) => setAgent(e.target.value)}>
              <option value={ALL}>{t('All agents')}</option>
              {agents.map((name) => <option key={name} value={name}>{name}</option>)}
            </select>
          </label>
        )}
        {imports.length > 0 && (
          <label className="filter-check">
            {t('Import')}
            <select value={shownImport} onChange={(e) => setImportId(e.target.value)}>
              <option value={ALL}>{t('All imports')}</option>
              {imports.map((found) => <option key={found.id} value={found.id}>{found.label}</option>)}
            </select>
          </label>
        )}
        {total > 1 && (
          <>
            <button className="primary" onClick={() => run(api.approveAll(shownAgent || undefined, shownImport || undefined))}>{t('Approve all')}</button>
            <button onClick={() => setRejectingAll(true)}>{t('Reject all')}</button>
          </>
        )}
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={load}>{t('Try again')}</button>
        </p>
      )}
      {!waiting && !error && <p className="muted">{t('Loading…')}</p>}

      {waiting && (
        <>
          <p className="muted">{t('New transactions from agents count straight away, until you reject them. Their other changes apply only once you approve them, and can still be reverted from the change log.')}</p>

          {total === 0 && <p className="muted">{t('Nothing is waiting for review.')}</p>}

          {shownChanges.map((change) => (
            <ChangeRow
              key={change.id}
              change={change}
              onApprove={() => run(api.approveChange(change.id ?? ''))}
              onReject={() => run(api.rejectChange(change.id ?? ''))}
            />
          ))}

          {shownTransactions.length > 0 && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('Date')}</th>
                    <th>{t('Account')}</th>
                    <th>{t('Type')}</th>
                    <th>{t('Details')}</th>
                    <th className="number">{t('Amount')}</th>
                    <th>{t('From')}</th>
                    <th><span className="visually-hidden">{t('Actions')}</span></th>
                  </tr>
                </thead>
                <tbody>
                  {shownTransactions.map((transaction) => (
                    <tr key={transaction.id}>
                      <td className="nowrap">{transaction.date}</td>
                      <td>{accountName(transaction.accountId)}</td>
                      <td>{TYPE_LABELS[transaction.type]}</td>
                      <td>{details(transaction)}</td>
                      <td className="number">{transactionAmount(transaction)}</td>
                      <td>{transaction.createdBy}</td>
                      <td className="actions">
                        <button className="trade buy" aria-label={t('Approve {name}', { name: details(transaction) })}
                                onClick={() => run(api.approveTransaction(transaction.id))}>{t('Approve')}</button>
                        <button className="trade sell" aria-label={t('Reject {name}', { name: details(transaction) })}
                                onClick={() => setRejecting(transaction)}>{t('Reject')}</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <h2 className="section-title">{t('Change log')}</h2>
          {log.length === 0 && <p className="muted">{t('Changes to transactions, companies, rules, accounts and plans will show here.')}</p>}
          {log.length > 0 && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('When')}</th>
                    <th>{t('Who')}</th>
                    <th>{t('What')}</th>
                    <th><span className="visually-hidden">{t('Actions')}</span></th>
                  </tr>
                </thead>
                <tbody>
                  {log.map((entry) => <LogEntry key={entry.id} entry={entry} onRevert={() => setReverting(entry)} />)}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {rejecting && (
        <ConfirmDialog
          title={t('Reject this transaction?')}
          message={t('It will be removed from all your numbers, and its effect on the account balance undone.')}
          action={t('Reject')}
          onConfirm={() => api.rejectTransaction(rejecting.id)}
          onDone={() => {
            setRejecting(null)
            void load()
          }}
          onClose={() => setRejecting(null)}
        />
      )}
      {rejectingAll && (
        <ConfirmDialog
          title={shownAgent ? t('Reject {count} from {name}?', { count: total, name: shownAgent }) : t('Reject {count}?', { count: total })}
          message={t('Rejected transactions come out of all your numbers, and other changes are dropped.')}
          action={t('Reject all')}
          onConfirm={() => api.rejectAll(shownAgent || undefined, shownImport || undefined)}
          onDone={() => {
            setRejectingAll(false)
            void load()
          }}
          onClose={() => setRejectingAll(false)}
        />
      )}
      {reverting && (
        <ConfirmDialog
          title={t('Revert this change?')}
          message={reverting.changedSince
            ? t('This has changed again since, and reverting puts back the older values, which undoes those later changes too.')
            : t('The values from before this change will be put back.')}
          action={t('Revert')}
          onConfirm={() => api.revertChange(reverting.id ?? '', reverting.changedSince ?? false)}
          onDone={() => {
            setReverting(null)
            void load()
          }}
          onClose={() => setReverting(null)}
        />
      )}
    </>
  )
}
