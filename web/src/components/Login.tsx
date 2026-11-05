import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import { Field } from './Field'

export function Login({ onLogin }: { onLogin: () => void }) {
  const [registering, setRegistering] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (registering) {
        await api.register(username, password)
      }
      await api.login(username, password)
      onLogin()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setBusy(false)
    }
  }

  function switchMode() {
    setRegistering(!registering)
    setError(null)
  }

  const hasFieldErrors = error !== null && Object.keys(error.fieldErrors).length > 0

  return (
    <main className="login">
      <form className="login-card" onSubmit={submit}>
        <h1>koifolio</h1>
        <Field
          label="Username"
          autoComplete="username"
          autoFocus
          required
          value={username}
          error={error?.fieldErrors.username}
          onChange={(e) => setUsername(e.target.value)}
        />
        <Field
          label="Password"
          type="password"
          autoComplete={registering ? 'new-password' : 'current-password'}
          required
          value={password}
          error={error?.fieldErrors.password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {error && !hasFieldErrors && <p className="error" role="alert">{error.message}</p>}
        <button className="primary" disabled={busy}>{registering ? 'Create account' : 'Log in'}</button>
        <button type="button" className="link" onClick={switchMode}>
          {registering ? 'I already have an account' : 'Create an account'}
        </button>
      </form>
    </main>
  )
}
