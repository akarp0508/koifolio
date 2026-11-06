import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { SplitSuggestion } from '../api/client'
import { t } from '../i18n'

export function SplitSuggestions({ onApplied }: { onApplied: () => void }) {
  const [suggestions, setSuggestions] = useState<SplitSuggestion[]>([])
  const [error, setError] = useState<ApiError | null>(null)

  const fetchSuggestions = useCallback(() =>
    api.splitSuggestions().then(setSuggestions).catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void fetchSuggestions()
  }, [fetchSuggestions])

  function act(action: Promise<void>, afterwards?: () => void) {
    setError(null)
    action
      .then(() => {
        afterwards?.()
        return fetchSuggestions()
      })
      .catch((e: ApiError) => setError(e))
  }

  if (suggestions.length === 0 && !error) {
    return null
  }

  return (
    <section className="panel suggestions">
      <header><h2>{t('Possible splits')}</h2></header>
      {error && <p className="error" role="alert">{error.message}</p>}
      {suggestions.map((suggestion) => (
        <div className="suggestion" key={suggestion.id}>
          <p>
            <strong>{suggestion.ticker}</strong> {t('looks like a {to} for {from} split around {date}.', { to: suggestion.splitTo ?? '', from: suggestion.splitFrom ?? '', date: suggestion.date ?? '' })}
            <span className="muted"> {t('Affects {names}. Check the real date before adding it.', { names: suggestion.accounts?.join(', ') ?? '' })}</span>
          </p>
          <div className="modal-actions">
            <button onClick={() => act(api.dismissSplit(suggestion.id ?? ''))}>{t('Dismiss')}</button>
            <button className="primary" onClick={() => act(api.applySplit(suggestion.id ?? ''), onApplied)}>{t('Add split')}</button>
          </div>
        </div>
      ))}
    </section>
  )
}
