import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Dashboard, Instrument, PortfolioHistory, Position, PositionDetails, PositionHistory, Transaction } from '../api/client'
import { decimal, formatAmount, formatGain, gainClass } from '../format'
import { RANGES, RANGE_LABELS, inRange } from '../history'
import type { Range } from '../history'
import { today } from '../transactions'
import { CompanySection } from './CompanySection'
import { ConfirmDialog } from './ConfirmDialog'
import type { TradeRequest } from './Holdings'
import { InstrumentDialog } from './InstrumentDialog'
import { PlanDialog } from './PlanDialog'
import { PlanSection } from './PlanSection'
import { PortfolioChart } from './PortfolioChart'
import { PositionChart } from './PositionChart'
import { TransactionDialog } from './TransactionDialog'
import { TransactionTable } from './TransactionTable'
import { t } from '../i18n'

type Holding = NonNullable<Dashboard['holdings']>[number]

type Company = {
  instrument: Instrument
  positions: Position[]
  accounts: Account[]
  holding?: Holding
  currency: string
  combined: PortfolioHistory
  transactions: Transaction[]
  plans: Record<string, PositionDetails>
}

function loadCompany(id: string): Promise<Company> {
  return Promise.all([api.getInstrument(id), api.listPositions(), api.listAccounts(), api.dashboard({ instrument: id }),
    api.portfolioHistory({ instrument: id }), api.listTransactions()])
    .then(async ([instrument, positions, accounts, dashboard, combined, transactions]) => {
      const held = positions.filter((position) => position.instrumentId === id)
      const plans = await Promise.all(held.map((position) => api.positionDetails(position.accountId, instrument.ticker)))
      return {
        instrument,
        positions: held,
        accounts,
        holding: dashboard.holdings?.[0],
        currency: dashboard.currency,
        combined,
        transactions: transactions.filter((transaction) => transaction.ticker === instrument.ticker),
        plans: Object.fromEntries(held.map((position, index) => [position.accountId, plans[index]])),
      }
    })
}

function Figure({ label, value, percent, gain }: { label: string; value: string; percent?: string; gain?: string }) {
  return (
    <div className={gain === undefined ? undefined : gainClass(gain)}>
      <span>{label}</span>
      <strong>{value}</strong>
      {percent && <span>{formatGain(percent)}%</span>}
    </div>
  )
}

function CombinedSummary({ holding, currency }: { holding: Holding; currency: string }) {
  return (
    <div className="summary">
      {holding.value && <Figure label={t('Value')} value={`${formatAmount(holding.value)} ${currency}`} />}
      {holding.unrealizedGain && (
        <Figure label={t('P/L')} gain={holding.unrealizedGain} percent={holding.unrealizedPercent}
                value={`${formatGain(holding.unrealizedGain)} ${currency}`} />
      )}
      {holding.realizedGain && (
        <Figure label={t('Realized')} gain={holding.realizedGain} value={`${formatGain(holding.realizedGain)} ${currency}`} />
      )}
      {gainClass(holding.dividends) && (
        <Figure label={t('Dividends')} gain={holding.dividends} value={`${formatGain(holding.dividends ?? '')} ${currency}`} />
      )}
      {holding.weight && <Figure label={t('Weight')} value={`${decimal(holding.weight ?? '')}%`} />}
    </div>
  )
}

function AccountSummary({ position }: { position: Position }) {
  const { currency, inBase } = position
  return (
    <>
      <div className="summary">
        {position.value && <Figure label={t('Value')} value={`${formatAmount(position.value)} ${currency}`} />}
        {position.unrealizedGain && (
          <Figure label={t('P/L')} gain={position.unrealizedGain} percent={position.unrealizedPercent}
                  value={`${formatGain(position.unrealizedGain)} ${currency}`} />
        )}
        <Figure label={t('Realized')} gain={position.realizedGain} value={`${formatGain(position.realizedGain)} ${currency}`} />
        {gainClass(position.dividends) && (
          <Figure label={t('Dividends')} gain={position.dividends} value={`${formatGain(position.dividends ?? '')} ${currency}`} />
        )}
        {position.weight && <Figure label={t('Weight')} value={`${decimal(position.weight ?? '')}%`} />}
        <Figure label={t('Quantity')} value={formatAmount(position.quantity)} />
        {position.averageCost && <Figure label={t('Avg cost')} value={`${formatAmount(position.averageCost)} ${currency}`} />}
        {position.price && <Figure label={t('Price')} value={`${formatAmount(position.price)} ${position.priceCurrency}`} />}
      </div>
      {inBase?.gain && (
        <p className="muted">
          {t('In {currency}: {value}, P/L', { currency: inBase.currency ?? '', value: formatAmount(inBase.value ?? '') })}{' '}
          <span className={gainClass(inBase.gain)}>{formatGain(inBase.gain)}</span>
          {inBase.currencyGain && gainClass(inBase.currencyGain) && (
            <>, {t('of which currency')} <span className={gainClass(inBase.currencyGain)}>{formatGain(inBase.currencyGain)}</span></>
          )}
        </p>
      )}
    </>
  )
}

/** Everything about one company. Without an account chosen, several accounts show combined in the base currency. */
export function CompanyDetail({ id, accountId }: { id: string; accountId?: string }) {
  const [company, setCompany] = useState<Company | null>(null)
  const [history, setHistory] = useState<PositionHistory | null>(null)
  const [range, setRange] = useState<Range>('All')
  const [editingPlan, setEditingPlan] = useState<string | null>(null)
  const [editingCompany, setEditingCompany] = useState(false)
  const [removing, setRemoving] = useState(false)
  const [trade, setTrade] = useState<Partial<TradeRequest> | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  const fetchCompany = useCallback(() =>
    loadCompany(id)
      .then((loaded) => {
        setCompany(loaded)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [id])

  useEffect(() => {
    void fetchCompany()
  }, [fetchCompany])

  const positions = company?.positions ?? []
  const position = positions.find((item) => item.accountId === accountId) ?? (positions.length === 1 ? positions[0] : undefined)
  const selected = position?.accountId
  const ticker = company?.instrument.ticker

  const fetchHistory = useCallback(() => {
    if (selected && ticker) {
      api.positionHistory(selected, ticker).then(setHistory).catch((e: ApiError) => setError(e))
    }
  }, [selected, ticker])

  useEffect(() => {
    fetchHistory()
  }, [fetchHistory])

  function saved() {
    setEditingPlan(null)
    setEditingCompany(false)
    setTrade(null)
    void fetchCompany()
    fetchHistory()
  }

  const backLink = <a className="back" href="#portfolio">← Portfolio</a>
  if (!company) {
    return (
      <>
        {backLink}
        {error
          ? <p className="error" role="alert">{error.message} <button className="link" onClick={fetchCompany}>{t('Try again')}</button></p>
          : <p className="muted">{t('Loading…')}</p>}
      </>
    )
  }

  const { instrument, accounts, holding, currency, combined, transactions, plans } = company
  const accountName = (id: string) => accounts.find((account) => account.id === id)?.name ?? ''
  const shownHistory = history?.accountId === selected ? history : null
  const positionPoints = shownHistory ? inRange(shownHistory.points, range, today()) : []
  const combinedPoints = inRange(combined.points, range, today())
  const shownPlans = selected ? [selected] : positions.map((item) => item.accountId)
  const shownTransactions = transactions.filter((transaction) => !selected || transaction.accountId === selected)
  const tradeAccount = selected ?? positions.find((item) => !item.closed)?.accountId
  const canSell = position ? !position.closed : tradeAccount !== undefined

  return (
    <>
      {backLink}
      <header className="page-header">
        <h1>{instrument.ticker}{instrument.name && <span className="muted"> — {instrument.name}</span>}</h1>
        <div className="header-actions wrap">
          {accounts.length > 0 && (
            <>
              <button className="buy" onClick={() => setTrade({ type: 'buy', ticker: instrument.ticker, accountId: tradeAccount })}>{t('Buy')}</button>
              {canSell && (
                <button className="sell" onClick={() => setTrade({ type: 'sell', ticker: instrument.ticker, accountId: tradeAccount })}>{t('Sell')}</button>
              )}
              <button onClick={() => setTrade({ ticker: instrument.ticker, accountId: tradeAccount })}>{t('+ Transaction')}</button>
            </>
          )}
          {positions.length === 0 && <button onClick={() => setRemoving(true)}>{t('Remove')}</button>}
        </div>
      </header>

      {error && (
        <p className="error" role="alert">
          {error.message} <button className="link" onClick={fetchCompany}>{t('Try again')}</button>
        </p>
      )}

      {positions.length > 1 && (
        <nav className="tabs">
          <a href={`#companies/${id}`} aria-current={selected ? undefined : 'page'}>{t('All')}</a>
          {positions.map((item) => (
            <a key={item.accountId} href={`#companies/${id}/${item.accountId}`}
               aria-current={item.accountId === selected ? 'page' : undefined}>{accountName(item.accountId)}</a>
          ))}
        </nav>
      )}

      {positions.length === 0 && <p className="muted">{t("You don't hold this. Use Buy to add your first trade.")}</p>}
      {position && <AccountSummary position={position} />}
      {!position && holding && <CombinedSummary holding={holding} currency={currency} />}

      {positions.length > 0 && (
        <section className="panel chart-panel">
          <header>
            <h2>{t('Value over time')}</h2>
            <div className="ranges">
              {RANGES.map((option) => (
                <button key={option} aria-pressed={option === range} onClick={() => setRange(option)}>{RANGE_LABELS[option]}</button>
              ))}
            </div>
          </header>
          {position && shownHistory && !shownHistory.covered && (
            <p className="muted">{t("No price history: the price source doesn't cover this ticker.")}</p>
          )}
          {position && shownHistory?.covered && shownHistory.points.length === 0 && (
            <p className="muted">{t('Price history is being fetched. It can take a few minutes for a new position.')}</p>
          )}
          {position && shownHistory?.currencyMismatch && shownHistory.points.length > 0 && (
            <p className="muted">{t("Showing the share price: it isn't in the account's currency, so value isn't available yet.")}</p>
          )}
          {position && shownHistory && positionPoints.length > 0 && (
            <PositionChart points={positionPoints} currency={shownHistory.currency} priceOnly={shownHistory.currencyMismatch} />
          )}
          {position && shownHistory && shownHistory.points.length > 0 && positionPoints.length === 0 && (
            <p className="muted">{t('Nothing in this range.')}</p>
          )}
          {!position && (combinedPoints.length > 0
            ? <PortfolioChart points={combinedPoints} currency={currency} />
            : <p className="muted">{t('Nothing to show in this range yet.')}</p>)}
        </section>
      )}

      <div className="panels">
        <CompanySection
          title={t('Company')}
          sector={instrument.sector}
          risk={instrument.risk}
          thesis={instrument.thesis}
          analysis={instrument.analysis}
          tags={instrument.tags}
          onEdit={() => setEditingCompany(true)}
        />
        {shownPlans.filter((plan) => plans[plan]).map((plan) => (
          <PlanSection
            key={plan}
            accountName={accountName(plan)}
            details={plans[plan]}
            priceCurrency={positions.find((item) => item.accountId === plan)?.priceCurrency}
            onEdit={() => setEditingPlan(plan)}
          />
        ))}
      </div>

      {shownTransactions.length > 0 && (
        <>
          <h2 className="section-title">{t('Trades')}</h2>
          <TransactionTable transactions={shownTransactions} accounts={accounts} onChanged={saved} />
        </>
      )}

      {editingPlan && (
        <PlanDialog
          accountId={editingPlan}
          ticker={instrument.ticker}
          details={plans[editingPlan]}
          priceCurrency={positions.find((item) => item.accountId === editingPlan)?.priceCurrency}
          onSaved={saved}
          onClose={() => setEditingPlan(null)}
        />
      )}
      {editingCompany && (
        <InstrumentDialog instrument={instrument} onSaved={saved} onClose={() => setEditingCompany(false)} />
      )}
      {removing && (
        <ConfirmDialog
          title={t('Remove {name}?', { name: instrument.ticker })}
          message={t('It leaves your list, and using it again adds it back.')}
          onConfirm={() => api.deleteInstrument(instrument.id)}
          onDone={() => { window.location.hash = '#portfolio' }}
          onClose={() => setRemoving(false)}
        />
      )}
      {trade && (
        <TransactionDialog
          transaction={null}
          accounts={accounts}
          initialAccountId={trade.accountId ?? accounts[0].id}
          initialType={trade.type}
          initialTicker={trade.ticker}
          onSaved={saved}
          onClose={() => setTrade(null)}
        />
      )}
    </>
  )
}
