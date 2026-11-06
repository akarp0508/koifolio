import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import { api, clearToken, hasToken, setUnauthorizedHandler } from './api/client'
import type { User } from './api/client'
import { Accounts } from './components/Accounts'
import { Login } from './components/Login'
import { Portfolio } from './components/Portfolio'
import { Profile } from './components/Profile'
import { Rules } from './components/Rules'
import { Tax } from './components/Tax'
import { Transactions } from './components/Transactions'
import { useTheme } from './theme'

// Holds the chart library, which most pages don't need
const CompanyDetail = lazy(() =>
  import('./components/CompanyDetail').then((module) => ({ default: module.CompanyDetail })))

const THEME_LABELS = { system: 'Theme: system', light: 'Theme: light', dark: 'Theme: dark' }

const MAIN_PAGES = [
  { hash: '#portfolio', label: 'Portfolio', Page: Portfolio },
  { hash: '#activity', label: 'Activity', Page: Transactions },
]

const OTHER_PAGES = [
  { hash: '#accounts', label: 'Accounts', Page: Accounts },
  { hash: '#rules', label: 'Rules', Page: Rules },
  { hash: '#tax', label: 'Tax', Page: Tax },
]

const PAGES = [...MAIN_PAGES, ...OTHER_PAGES]

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
  const company = hash.match(/^#companies\/([^/]+)(?:\/([^/]+))?$/)
  const parent = company ? '#portfolio' : hash
  const profile = hash === '#profile'
  const current = profile ? null : PAGES.find((page) => page.hash === parent) ?? PAGES[0]

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
        {MAIN_PAGES.map((page) => (
          <NavItem key={page.hash} href={page.hash} current={page === current} label={page.label} />
        ))}
        <div className="nav-group">
          {OTHER_PAGES.map((page) => (
            <NavItem key={page.hash} href={page.hash} current={page === current} label={page.label} />
          ))}
        </div>
        <div className="sidebar-footer">
          <div className="user">
            <a className="user-link" aria-current={profile ? 'page' : undefined} href="#profile">{user.username}</a>
            <button className="icon" aria-label={THEME_LABELS[theme]} title={THEME_LABELS[theme]} onClick={nextTheme}>◐</button>
          </div>
          <button className="link" onClick={logOut}>Log out</button>
        </div>
      </nav>
      <main className="content">
        <Suspense fallback={<p className="muted">Loading…</p>}>
          {company && <CompanyDetail key={company[1]} id={company[1]} accountId={company[2]} />}
          {profile && <Profile user={user} />}
          {!company && current && <current.Page />}
        </Suspense>
      </main>
    </div>
  )
}

export default App
