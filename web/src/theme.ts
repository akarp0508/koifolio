import { useEffect, useState } from 'react'

export type Theme = 'system' | 'light' | 'dark'

const KEY = 'koifolio.theme'
const NEXT: Record<Theme, Theme> = { system: 'light', light: 'dark', dark: 'system' }

function stored(): Theme {
  const value = localStorage.getItem(KEY)
  return value === 'light' || value === 'dark' ? value : 'system'
}

export function useTheme() {
  const [theme, setTheme] = useState(stored)

  useEffect(() => {
    if (theme === 'system') {
      localStorage.removeItem(KEY)
      delete document.documentElement.dataset.theme
    } else {
      localStorage.setItem(KEY, theme)
      document.documentElement.dataset.theme = theme
    }
  }, [theme])

  return { theme, nextTheme: () => setTheme(NEXT[theme]) }
}
