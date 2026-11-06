import { useEffect, useState } from 'react'
import { api } from './api/client'

const CHANGED = 'koifolio:review-changed'
const REFRESH_MS = 30_000

/** Tells the sidebar to look again, for when the page just decided something. */
export function reviewChanged() {
  window.dispatchEvent(new Event(CHANGED))
}

/** How much agents have left to review. Agents work on their own, so it keeps checking. */
export function useReviewCount(loggedIn: boolean, hash: string) {
  const [waiting, setWaiting] = useState(0)

  useEffect(() => {
    if (!loggedIn) {
      return
    }
    // A missed check only leaves the count stale until the next one
    const check = () => api.reviewCount().then(({ waiting: count }) => setWaiting(count ?? 0)).catch(() => {})
    void check()
    const timer = window.setInterval(check, REFRESH_MS)
    window.addEventListener(CHANGED, check)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener(CHANGED, check)
    }
  }, [loggedIn, hash])

  return loggedIn ? waiting : 0
}
