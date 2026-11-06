import { useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { RestoredData, Settings, User } from '../api/client'
import { AgentTokens } from './AgentTokens'
import { ConfirmDialog } from './ConfirmDialog'
import { t } from '../i18n'

function saveFile(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}

export function Profile({ user }: { user: User }) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [restored, setRestored] = useState<RestoredData | null>(null)
  const [exporting, setExporting] = useState(false)
  const [settings, setSettings] = useState<Settings | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    api.settings().then(setSettings).catch((e: ApiError) => setError(e))
  }, [])

  function changeSettings(changes: Parameters<typeof api.updateSettings>[0]) {
    api.updateSettings(changes).then(setSettings).catch((e: ApiError) => setError(e))
  }

  async function exportData() {
    setExporting(true)
    setError(null)
    try {
      const { blob, name } = await api.exportData()
      saveFile(blob, name)
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setExporting(false)
    }
  }

  function chosen(files: FileList | null) {
    setRestored(null)
    setError(null)
    setFile(files?.[0] ?? null)
  }

  function closeConfirm() {
    setFile(null)
    if (input.current) {
      input.current.value = ''
    }
  }

  return (
    <>
      <header className="page-header">
        <h1>{user.username}</h1>
      </header>

      {error && <p className="error" role="alert">{error.message}</p>}

      <div className="panels">
        <section className="panel">
          <header><h2>{t('Settings')}</h2></header>
          {!settings && !error && <p className="muted">{t('Loading…')}</p>}
          {settings && (
            <>
              <label className="field">
                <span>{t('Base currency')}</span>
                <select value={settings.baseCurrency} onChange={(e) => changeSettings({ baseCurrency: e.target.value })}>
                  {settings.currencies.map((code) => <option key={code} value={code}>{code}</option>)}
                </select>
              </label>
              <label className="field">
                <span>{t('Trade FX rate')}</span>
                <select
                  value={settings.costFxRule}
                  onChange={(e) => changeSettings({ costFxRule: e.target.value as Settings['costFxRule'] })}
                >
                  <option value="previous_publication">{t('Day before (Polish tax)')}</option>
                  <option value="transaction_date">{t('Trade date')}</option>
                </select>
              </label>
              <p className="muted">{t("Which day's exchange rate converts a buy or sell to the base currency.")}</p>
            </>
          )}
        </section>
        <section className="panel">
          <header><h2>{t('Export')}</h2></header>
          <p className="muted">{t("Everything you've entered, as a zip of plain-text files you can read and keep: accounts, transactions, companies, plans, rules and settings. It holds your finances, so store it somewhere private.")}</p>
          <button className="primary" disabled={exporting} onClick={exportData}>{exporting ? t('Exporting…') : t('Download export')}</button>
        </section>
        <section className="panel">
          <header><h2>{t('Restore')}</h2></header>
          <p className="muted">{t('Replaces all your data with an export. If anything in the file is wrong, nothing changes. Prices come back on the next update, within about 15 minutes.')}</p>
          <input ref={input} type="file" accept=".zip,application/zip" aria-label={t('Export file')}
                 onChange={(e) => chosen(e.target.files)} />
          {restored && (
            <p>
              {t('Restored {accounts} accounts, {transactions} transactions, {companies} companies, {plans} plans and {rules} rules.', { accounts: restored.accounts, transactions: restored.transactions, companies: restored.companies, plans: restored.plans, rules: restored.rules })}
            </p>
          )}
        </section>
        <AgentTokens />
      </div>

      {file && (
        <ConfirmDialog
          title={t('Replace all your data?')}
          message={t("Everything in this app will be replaced by {name}. This can't be undone, so download an export first if you might want your current data.", { name: file.name })}
          action={t('Replace')}
          onConfirm={() => api.restoreData(file).then(setRestored)}
          onDone={closeConfirm}
          onClose={closeConfirm}
        />
      )}
    </>
  )
}
