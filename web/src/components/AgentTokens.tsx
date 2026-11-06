import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import type { AgentToken, NewAgentToken } from '../api/client'
import { ConfirmDialog } from './ConfirmDialog'
import { Field } from './Field'

const formatDay = (time: string) => new Date(time).toLocaleDateString()

export function AgentTokens() {
  const [tokens, setTokens] = useState<AgentToken[] | null>(null)
  const [name, setName] = useState('')
  const [expiresOn, setExpiresOn] = useState('')
  const [created, setCreated] = useState<NewAgentToken | null>(null)
  const [revoking, setRevoking] = useState<AgentToken | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  function load() {
    api.listAgentTokens().then(setTokens).catch((e: ApiError) => setError(e))
  }

  useEffect(load, [])

  function create(event: FormEvent) {
    event.preventDefault()
    setError(null)
    api.createAgentToken({ name, expiresOn: expiresOn || undefined })
      .then((token) => {
        setCreated(token)
        setName('')
        setExpiresOn('')
        load()
      })
      .catch((e: ApiError) => setError(e))
  }

  return (
    <section className="panel">
      <header><h2>Agents</h2></header>
      <p className="muted">
        A token lets an agent read your portfolio through MCP, and nothing else on this site. Give each agent its own, so you
        can revoke it alone.
      </p>

      {error && <p className="error" role="alert">{error.message}</p>}

      {created && (
        <div className="notice">
          <p>Copy the token for {created.token.name} now. It won't be shown again.</p>
          <code className="agent-secret">{created.secret}</code>
          <p className="muted">For Claude Code:</p>
          <code className="agent-secret">
            claude mcp add --transport http koifolio {window.location.origin}/mcp --header "Authorization: Bearer {created.secret}"
          </code>
          <button className="link" onClick={() => setCreated(null)}>Done</button>
        </div>
      )}

      {tokens && tokens.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Last used</th>
                <th>Expires</th>
                <th><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {tokens.map((token) => (
                <tr key={token.id}>
                  <td>{token.name}</td>
                  <td>{token.lastUsedAt ? formatDay(token.lastUsedAt) : 'Never'}</td>
                  <td>{token.expiresOn ?? 'Never'}</td>
                  <td className="actions">
                    <button className="link" aria-label={`Revoke ${token.name}`} onClick={() => setRevoking(token)}>Revoke</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form onSubmit={create}>
        <Field label="Agent name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        <Field label="Last day it works (optional)" type="date" value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} />
        <button className="primary" disabled={!name.trim()}>Create token</button>
      </form>

      {revoking && (
        <ConfirmDialog
          title={`Revoke ${revoking.name}?`}
          message="The agent stops working at once, and the token can't be brought back."
          action="Revoke"
          onConfirm={() => api.revokeAgentToken(revoking.id)}
          onDone={() => {
            setRevoking(null)
            load()
          }}
          onClose={() => setRevoking(null)}
        />
      )}
    </section>
  )
}
