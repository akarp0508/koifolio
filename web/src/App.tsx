import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import { api, clearToken, hasToken, setUnauthorizedHandler } from './api/client'
import type { User } from './api/client'
import { Accounts } from './components/Accounts'
import { Dashboard } from './components/Dashboard'
import { Instruments } from './components/Instruments'
import { Login } from './components/Login'
import { Positions } from './components/Positions'
import { Tax } from './components/Tax'
import { Transactions } from './components/Transactions'
import { useTheme } from './theme'

// Holds the chart library, which most pages don't need
const PositionDetail = lazy(() =>
  import('./components/PositionDetail').then((module) => ({ default: module.PositionDetail })))

const InstrumentDetail = lazy(() =>
  import('./components/InstrumentDetail').then((module) => ({ default: module.InstrumentDetail })))

const THEME_LABELS = { system: 'Theme: system', light: 'Theme: light', dark: 'Theme: dark' }

const PAGES = [
  { hash: '#dashboard', label: 'Dashboard', Page: Dashboard },
  { hash: '#accounts', label: 'Accounts', Page: Accounts },
  { hash: '#transactions', label: 'Transactions', Page: Transactions },
  { hash: '#positions', label: 'Positions', Page: Positions },
  { hash: '#instruments', label: 'Instruments', Page: Instruments },
  { hash: '#tax', label: 'Tax', Page: Tax },
]

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
  const hash = useHash()
  const detail = hash.match(/^#positions\/([^/]+)\/(.+)$/)
  const instrumentDetail = hash.match(/^#instruments\/(.+)$/)
  const parent = detail ? '#positions' : instrumentDetail ? '#instruments' : hash
  const current = PAGES.find((page) => page.hash === parent) ?? PAGES[0]

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
        {PAGES.map((page) => (
          <NavItem key={page.hash} href={page.hash} current={page === current} label={page.label} />
        ))}
        <div className="sidebar-footer">
          <div className="user">
            <span>{user.username}</span>
            <button className="icon" aria-label={THEME_LABELS[theme]} title={THEME_LABELS[theme]} onClick={nextTheme}>◐</button>
          </div>
          <button className="link" onClick={logOut}>Log out</button>
        </div>
      </nav>
      <main className="content">
        <Suspense fallback={<p className="muted">Loading…</p>}>
          {detail && <PositionDetail key={hash} accountId={detail[1]} ticker={decodeURIComponent(detail[2])} />}
          {instrumentDetail && <InstrumentDetail key={hash} id={instrumentDetail[1]} />}
          {!detail && !instrumentDetail && <current.Page />}
        </Suspense>
      </main>
    </div>
  )
}

export default App
