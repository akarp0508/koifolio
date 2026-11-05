import { useCallback, useEffect, useState } from 'react'
import { api, clearToken, hasToken, setUnauthorizedHandler } from './api/client'
import type { User } from './api/client'
import { Accounts } from './components/Accounts'
import { Login } from './components/Login'
import { Transactions } from './components/Transactions'
import { useTheme } from './theme'

const THEME_LABELS = { system: 'Theme: system', light: 'Theme: light', dark: 'Theme: dark' }

function useHash() {
  const [hash, setHash] = useState(window.location.hash)

  useEffect(() => {
    const update = () => setHash(window.location.hash)
    window.addEventListener('hashchange', update)
    return () => window.removeEventListener('hashchange', update)
  }, [])

  return hash
}

function NavItem({ href, current, label }: { href: string; current: boolean; label: string }) {
  return (
    <a className={current ? 'nav-item active' : 'nav-item'} aria-current={current ? 'page' : undefined} href={href}>
      {label}
    </a>
  )
}

function App() {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(hasToken)
  const { theme, nextTheme } = useTheme()
  const onTransactions = useHash() === '#transactions'

  const logOut = useCallback(() => {
    clearToken()
    setUser(null)
    setLoading(false)
  }, [])

  const fetchUser = useCallback(() =>
    api.me()
      .then(setUser)
      // A rejected token already logged out; if the server is just unreachable, keep the token
      .catch(() => setUser(null))
      .finally(() => setLoading(false)), [])

  useEffect(() => {
    setUnauthorizedHandler(logOut)
    if (hasToken()) {
      void fetchUser()
    }
  }, [fetchUser, logOut])

  function loggedIn() {
    setLoading(true)
    void fetchUser()
  }

  if (loading) {
    return null
  }
  if (!user) {
    return <Login onLogin={loggedIn} />
  }

  return (
    <div className="layout">
      <nav className="sidebar">
        <strong className="brand">koifolio</strong>
        <NavItem href="#accounts" current={!onTransactions} label="Accounts" />
        <NavItem href="#transactions" current={onTransactions} label="Transactions" />
        <div className="sidebar-footer">
          <div className="user">
            <span>{user.username}</span>
            <button className="icon" aria-label={THEME_LABELS[theme]} title={THEME_LABELS[theme]} onClick={nextTheme}>◐</button>
          </div>
          <button className="link" onClick={logOut}>Log out</button>
        </div>
      </nav>
      <main className="content">
        {onTransactions ? <Transactions /> : <Accounts />}
      </main>
    </div>
  )
}

export default App
