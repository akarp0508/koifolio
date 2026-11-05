import { useCallback, useEffect, useState } from 'react'
import { api, clearToken, hasToken, setUnauthorizedHandler } from './api/client'
import type { User } from './api/client'
import { Accounts } from './components/Accounts'
import { Login } from './components/Login'
import { useTheme } from './theme'

const THEME_LABELS = { system: 'Theme: system', light: 'Theme: light', dark: 'Theme: dark' }

function App() {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(hasToken)
  const { theme, nextTheme } = useTheme()

  const logOut = useCallback(() => {
    clearToken()
    setUser(null)
    setLoading(false)
  }, [])

  const loadUser = useCallback(async () => {
    setLoading(true)
    try {
      setUser(await api.me())
    } catch {
      // A rejected token already logged out; if the server is just unreachable, keep the token
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [logOut])

  useEffect(() => {
    setUnauthorizedHandler(logOut)
    if (hasToken()) {
      void loadUser()
    }
  }, [loadUser, logOut])

  if (loading) {
    return null
  }
  if (!user) {
    return <Login onLogin={loadUser} />
  }

  return (
    <div className="layout">
      <nav className="sidebar">
        <strong className="brand">koifolio</strong>
        <a className="nav-item active" aria-current="page" href="#accounts">Accounts</a>
        <div className="sidebar-footer">
          <div className="user">
            <span>{user.username}</span>
            <button className="icon" aria-label={THEME_LABELS[theme]} title={THEME_LABELS[theme]} onClick={nextTheme}>◐</button>
          </div>
          <button className="link" onClick={logOut}>Log out</button>
        </div>
      </nav>
      <main className="content">
        <Accounts />
      </main>
    </div>
  )
}

export default App
