import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Dashboard, DashboardSlice, Instrument, PeriodChange, PortfolioHistory, Position, RuleResult, TaxEstimate } from '../api/client'
import { formatAmount, formatGain, formatQuoteTime, gainClass } from '../format'
import { RANGES, RANGE_LABELS, inRange } from '../history'
import type { Range } from '../history'
import { SIGNAL_LABELS, allTags, signalClass } from '../plans'
import { ruleLabel } from '../rules'
import { today } from '../transactions'
import { Holdings } from './Holdings'
import { InstrumentDialog } from './InstrumentDialog'
import type { TradeRequest } from './Holdings'
import { SplitSuggestions } from './SplitSuggestions'
import { TagFilter } from './TagFilter'
import { TransactionDialog } from './TransactionDialog'
import { plural, t } from '../i18n'

// Holds the chart library, so the rest of the page opens without it
const PortfolioChart = lazy(() =>
  import('./PortfolioChart').then((module) => ({ default: module.PortfolioChart })))

const ALL = ''

const PERIOD_LABELS: Record<NonNullable<PeriodChange['period']>, string> = {
  DAY: t('1D'),
  WEEK: t('1W'),
  MONTH: t('1M'),
  YEAR_TO_DATE: t('YTD'),
  YEAR: t('1Y'),
}

function gainNote(dashboard: Dashboard) {
  const percent = dashboard.unrealizedPercent && `${formatGain(dashboard.unrealizedPercent)}%`
  const currency = dashboard.currencyGain && gainClass(dashboard.currencyGain)
    ? t('currency {amount}', { amount: formatGain(dashboard.currencyGain) })
    : undefined
  return [percent, currency].filter(Boolean).join(' · ') || undefined
}

function taxNote(tax: TaxEstimate) {
  const unset = tax.unsetAccounts?.length ?? 0
  const sold = tax.totalIfSoldToday && t('{amount} if sold today', { amount: formatGain(tax.totalIfSoldToday) })
  const dividends = gainClass(tax.totalDividendTax) ? t('{amount} on dividends', { amount: formatAmount(tax.totalDividendTax ?? '') }) : undefined
  return [sold, dividends, unset > 0 ? t('{count} without a rate', { count: unset }) : undefined].filter(Boolean).join(' · ') || undefined
}

function Tile({ label, children, note, tone = '' }: {
  label: string
  children: ReactNode
  note?: string
  tone?: string
}) {
  return (
    <div className={`tile ${tone}`}>
      <span>{label}</span>
      <strong>{children}</strong>
      {note && <small>{note}</small>}
    </div>
  )
}

function Periods({ changes, currency }: { changes: PeriodChange[]; currency: string }) {
  return (
    <div className="periods">
      {Object.entries(PERIOD_LABELS).map(([period, label]) => {
        const change = changes.find((item) => item.period === period)
        return (
          <div key={period} className={`period ${gainClass(change?.change)}`}>
            <span>{label}</span>
            <strong>{change ? `${formatGain(change.change ?? '')} ${currency}` : '—'}</strong>
            {change?.percent && <small>{formatGain(change.percent)}%</small>}
          </div>
        )
      })}
    </div>
  )
}

function Bars({ title, slices, currency }: { title: string; slices: DashboardSlice[]; currency: string }) {
  return (
    <section className="panel">
      <header><h2>{title}</h2></header>
      {slices.length === 0 && <p className="muted">{t('Nothing to show.')}</p>}
      {slices.map((slice) => (
        <div className="bar-row" key={slice.label ?? 'none'} title={`${formatAmount(slice.value ?? '')} ${currency}`}>
          <span className={slice.label ? undefined : 'muted'}>{slice.label === 'Bonds' ? t('Bonds') : slice.label ?? t('Not set')}</span>
          <div className="bar"><div style={{ width: `${slice.percent}%` }} /></div>
          <span className="number">{slice.percent}%</span>
        </div>
      ))}
    </section>
  )
}

export function Portfolio() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [instruments, setInstruments] = useState<Instrument[]>([])
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [account, setAccount] = useState(ALL)
  const [instrumentTags, setInstrumentTags] = useState<string[]>([])
  const [positionTags, setPositionTags] = useState<string[]>([])
  const [history, setHistory] = useState<PortfolioHistory | null>(null)
  const [tax, setTax] = useState<TaxEstimate | null>(null)
  const [ruleResults, setRuleResults] = useState<RuleResult[]>([])
  const [range, setRange] = useState<Range>('1Y')
  const [error, setError] = useState<ApiError | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  // null while closed; an empty request is a transaction for anything
  const [trade, setTrade] = useState<Partial<TradeRequest> | null>(null)
  const [addingCompany, setAddingCompany] = useState(false)

  const fetchDashboard = useCallback(() => {
    const filter = { account: account || undefined, instrumentTags, positionTags }
    return Promise.all([api.dashboard(filter), api.portfolioHistory(filter)])
      .then(([data, historyData]) => {
        setDashboard(data)
        setHistory(historyData)
        setError(null)
      })
      .catch((e: ApiError) => setError(e))
  }, [account, instrumentTags, positionTags])

  useEffect(() => {
    void fetchDashboard()
  }, [fetchDashboard])

  const fetchOverview = useCallback(() =>
    Promise.all([api.listAccounts(), api.listPositions(), api.tax(), api.listInstruments(), api.checkRules()])
      .then(([accountList, positionList, taxData, instrumentList, ruleList]) => {
        setTax(taxData)
        setRuleResults(ruleList)
        setInstruments(instrumentList)
        setAccounts(accountList)
        setPositions(positionList)
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void fetchOverview()
  }, [fetchOverview])

  function traded() {
    setTrade(null)
    setAddingCompany(false)
    void Promise.all([fetchOverview(), fetchDashboard()])
  }

  async function refresh() {
    setRefreshing(true)
    setError(null)
    try {
      await api.refreshPrices()
      await Promise.all([fetchOverview(), fetchDashboard()])
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setRefreshing(false)
    }
  }

  const currency = dashboard?.currency ?? ''
  const toReview = (dashboard?.unvalidated ?? 0) + (dashboard?.pendingChanges ?? 0)
  const filteredByTags = instrumentTags.length > 0 || positionTags.length > 0
  const points = history ? inRange(history.points, range, today()) : []
  // A filter hides holdings, so only the whole list can tell what's really not held
  const filtered = account !== ALL || filteredByTags
  const watched = filtered ? [] : instruments.filter((instrument) =>
    !dashboard?.holdings?.some((holding) => holding.instrumentId === instrument.id))
  const failedRules = ruleResults.filter((result) => !result.passed)
  const quoteTime = formatQuoteTime(positions.map((position) => position.quotedAt))

  return (
    <>
      <header className="page-header">
        <h1>{t('Portfolio')}</h1>
        <div className="header-actions wrap">
          {quoteTime && <span className="muted">{t('Prices as of {time}', { time: quoteTime })}</span>}
          <button disabled={refreshing} onClick={refresh}>{refreshing ? t('Refreshing…') : t('Refresh')}</button>
          <button onClick={() => setAddingCompany(true)}>{t('+ Company')}</button>
          <button className="primary" disabled={accounts.length === 0} onClick={() => setTrade({})}>{t('+ Transaction')}</button>
        </div>
      </header>

      <SplitSuggestions onApplied={fetchDashboard} />

      <div className="filter wrap">
        <label className="filter-check">
          {t('Account')}
          <select value={account} onChange={(e) => setAccount(e.target.value)}>
            <option value={ALL}>{t('All')}</option>
            {accounts.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
          </select>
        </label>
        <TagFilter label={t('Company tags')} tags={allTags(positions, 'instrumentTags')} selected={instrumentTags}
                   onChange={setInstrumentTags} />
        <TagFilter label={t('Position tags')} tags={allTags(positions, 'positionTags')} selected={positionTags}
                   onChange={setPositionTags} />
      </div>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchDashboard}>{t('Try again')}</button>
        </p>
      )}
      {!dashboard && !error && <p className="muted">{t('Loading…')}</p>}

      {dashboard && (
        <>
          <div className="tiles">
            <Tile label={t('Value')}>{formatAmount(dashboard.value)} {currency}</Tile>
            <Tile label={t('P/L')} tone={gainClass(dashboard.unrealizedGain)} note={gainNote(dashboard)}>
              {formatGain(dashboard.unrealizedGain)} {currency}
            </Tile>
            <Tile label={t('Realized')} tone={gainClass(dashboard.realizedGain)}>{formatGain(dashboard.realizedGain)} {currency}</Tile>
            <Tile
              label={t('Dividends')}
              tone={gainClass(dashboard.dividends)}
              note={gainClass(dashboard.dividendsWithheld) ? t('withheld {amount}', { amount: formatAmount(dashboard.dividendsWithheld) }) : undefined}
            >
              {formatGain(dashboard.dividends)} {currency}
            </Tile>
            {gainClass(dashboard.bondsValue) && (
              <Tile label={t('Bonds')} note={t('interest {amount}', { amount: formatGain(dashboard.bondsGain) })}>
                <a className="tile-link" href="#bonds">{formatAmount(dashboard.bondsValue)} {currency}</a>
              </Tile>
            )}
            {tax && (
              <Tile label={t('Est. tax {year}', { year: tax.year ?? '' })} note={taxNote(tax)}>
                <a className="tile-link" href="#tax">{formatAmount(tax.totalTax ?? '')} {tax.currency}</a>
              </Tile>
            )}
            <Tile label={t('Cash')} note={filteredByTags ? t('not filtered by tags') : undefined}>
              {dashboard.cash ? `${formatAmount(dashboard.cash)} ${currency}` : '—'}
            </Tile>
          </div>

          {(dashboard.attention.length > 0 || failedRules.length > 0 || toReview > 0) && (
            <ul className="attention" aria-label={t('Needs attention')}>
              {failedRules.map(({ rule, actual }) => (
                <li key={rule.id}>
                  <a href="#rules">{ruleLabel(rule)}</a>
                  <span className="signal loss">now {actual}%</span>
                </li>
              ))}
              {toReview > 0 && (
                <li>
                  <a href="#review">{t('{count} {changes} to review', { count: toReview, changes: plural(toReview, t('agent change'), t('agent changes'), t('agent changes (many)')) })}</a>
                </li>
              )}
              {dashboard.attention.map((item) => (
                <li key={`${item.accountId}-${item.ticker}`}>
                  <a href={`#companies/${item.instrumentId}/${item.accountId}`}>{item.ticker}</a>
                  <span className="muted">{item.accountName}</span>
                  {item.signal && <span className={signalClass(item.signal)}>{SIGNAL_LABELS[item.signal]}</span>}
                  {item.distance && <span className="muted">{formatGain(item.distance)}%</span>}
                </li>
              ))}
            </ul>
          )}

          <div className="overview">
            <div>
              {history && history.points.length > 0 && (
                <section className="panel chart-panel">
                  <header>
                    <h2>{t('Value over time')}</h2>
                    <div className="ranges">
                      {RANGES.map((option) => (
                        <button key={option} aria-pressed={option === range} onClick={() => setRange(option)}>{RANGE_LABELS[option]}</button>
                      ))}
                    </div>
                  </header>
                  {points.length > 0 ? (
                    <Suspense fallback={<p className="muted">{t('Loading…')}</p>}>
                      <PortfolioChart points={points} currency={currency} />
                    </Suspense>
                  ) : <p className="muted">{t('Nothing in this range.')}</p>}
                </section>
              )}
              {history && <Periods changes={history.changes} currency={currency} />}
            </div>
            <div className="overview-side">
              <Bars title={t('By sector')} slices={dashboard.bySector} currency={currency} />
              <Bars title={t('By risk')} slices={dashboard.byRisk} currency={currency} />
            </div>
          </div>

          {dashboard.unpriced > 0 && (
            <p className="muted">
              {dashboard.unpriced === 1 ? t("1 position has no price or exchange rate, so it isn't counted.") : t("{count} positions have no price or exchange rate, so they aren't counted.", { count: dashboard.unpriced })}
            </p>
          )}

          <Holdings holdings={dashboard.holdings ?? []} watched={watched} currency={currency} onTrade={setTrade} />
        </>
      )}

      {addingCompany && <InstrumentDialog instrument={null} onSaved={traded} onClose={() => setAddingCompany(false)} />}
      {trade && (
        <TransactionDialog
          transaction={null}
          accounts={accounts}
          initialAccountId={trade.accountId ?? (account || accounts[0].id)}
          initialType={trade.type}
          initialTicker={trade.ticker}
          onSaved={traded}
          onClose={() => setTrade(null)}
        />
      )}
    </>
  )
}
