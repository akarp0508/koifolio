import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, AuditEntry, PendingChange, Transaction } from '../api/client'
import { fieldChanges } from '../auditChanges'
import { TYPE_LABELS, details, transactionAmount } from '../transactions'
import { ConfirmDialog } from './ConfirmDialog'

const ALL = ''

const who = (entry: AuditEntry) => entry.actorType === 'AGENT' ? entry.actorName : 'You'

function Changes({ before, after, beforeLabel, afterLabel }: {
  before: unknown
  after: unknown
  beforeLabel: string
  afterLabel: string
}) {
  const changes = fieldChanges(before, after)
  if (changes.length === 0) {
    return <p className="muted">No values changed.</p>
  }

  return (
    <table className="log-changes">
      <thead>
        <tr>
          <th>Field</th>
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
          <button className="trade buy" aria-label={`Approve ${change.summary}`} onClick={onApprove}>Approve</button>
          <button className="trade sell" aria-label={`Reject ${change.summary}`} onClick={onReject}>Reject</button>
        </div>
      </header>
      <p className="muted">From {change.agentName}</p>
      <Changes before={change.current} after={change.proposed} beforeLabel="Now" afterLabel="Proposed" />
    </section>
  )
}

function LogEntry({ entry, onRevert }: { entry: AuditEntry; onRevert: () => void }) {
  const [open, setOpen] = useState(false)

  return (
    <>
      <tr className="clickable" onClick={() => setOpen(!open)}>
        <td className="nowrap">
          <button className="expand" aria-expanded={open} aria-label={`Details of ${entry.summary}`}>{open ? '▾' : '▸'}</button>
          {entry.createdAt ? new Date(entry.createdAt).toLocaleString() : ''}
        </td>
        <td>{who(entry)}</td>
        <td>{entry.summary}</td>
        <td className="actions">
          {entry.revertible && (
            <button
              className="link"
              aria-label={`Revert ${entry.summary}`}
              onClick={(event) => {
                event.stopPropagation()
                onRevert()
              }}
            >
              Revert
            </button>
          )}
        </td>
      </tr>
      {open && (
        <tr>
          <td colSpan={4}>
            <Changes before={entry.before} after={entry.after} beforeLabel="Before" afterLabel="After" />
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
  const shownTransactions = (waiting ?? []).filter((transaction) => !shownAgent || transaction.createdBy === shownAgent)
  const shownChanges = changes.filter((change) => !shownAgent || change.agentName === shownAgent)
  const total = shownTransactions.length + shownChanges.length

  return (
    <>
      <header className="page-header">
        <h1>Review</h1>
        {agents.length > 1 && (
          <label className="filter-check">
            From
            <select value={shownAgent} onChange={(e) => setAgent(e.target.value)}>
              <option value={ALL}>All agents</option>
              {agents.map((name) => <option key={name} value={name}>{name}</option>)}
            </select>
          </label>
        )}
        {total > 1 && (
          <>
            <button className="primary" onClick={() => run(api.approveAll(shownAgent || undefined))}>Approve all</button>
            <button onClick={() => setRejectingAll(true)}>Reject all</button>
          </>
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
            Transactions from agents count straight away, until you reject them. Company and rule changes apply only once you
            approve them, and can still be reverted from the change log.
          </p>

          {total === 0 && <p className="muted">Nothing is waiting for review.</p>}

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
                  {shownTransactions.map((transaction) => (
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
          {log.length === 0 && <p className="muted">Changes to transactions, companies, rules, accounts and plans will show here.</p>}
          {log.length > 0 && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Who</th>
                    <th>What</th>
                    <th><span className="visually-hidden">Actions</span></th>
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
      {rejectingAll && (
        <ConfirmDialog
          title={`Reject ${total} ${total === 1 ? 'change' : 'changes'}${shownAgent ? ` from ${shownAgent}` : ''}?`}
          message="Rejected transactions come out of all your numbers, and company and rule changes are dropped."
          action="Reject all"
          onConfirm={() => api.rejectAll(shownAgent || undefined)}
          onDone={() => {
            setRejectingAll(false)
            void load()
          }}
          onClose={() => setRejectingAll(false)}
        />
      )}
      {reverting && (
        <ConfirmDialog
          title="Revert this change?"
          message={reverting.changedSince
            ? 'This has changed again since, and reverting puts back the older values, which undoes those later changes too.'
            : 'The values from before this change will be put back.'}
          action="Revert"
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
