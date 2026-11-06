import type { PositionDetails } from '../api/client'
import { formatAmount, formatGain } from '../format'
import { RISK_LABELS, SIGNAL_LABELS, signalClass } from '../plans'
import { Markdown } from './Markdown'
import { Tags } from './Tags'
import { t } from '../i18n'

function Level({ label, price, distance, currency }: {
  label: string
  price?: string
  distance?: string
  currency?: string
}) {
  if (!price) {
    return null
  }
  return (
    <p>
      {label}: {formatAmount(price)} {currency}
      {distance && <span className="muted"> ({formatGain(distance)}%)</span>}
    </p>
  )
}

export function PlanSection({ accountName, details, priceCurrency, onEdit }: {
  accountName: string
  details: PositionDetails
  priceCurrency?: string
  onEdit: () => void
}) {
  const empty = !details.plan && !details.risk && !details.targetPrice && !details.stopPrice && !details.positionTags?.length
  return (
    <section className="panel">
      <header>
        <h2>{t('My plan ({name})', { name: accountName })}</h2>
        <button onClick={onEdit}>{t('Edit')}</button>
      </header>
      {empty && <p className="muted">{t('No plan yet. Why do you hold it here, and what would make you sell?')}</p>}
      {details.signal && <p className={signalClass(details.signal)}>{SIGNAL_LABELS[details.signal]}</p>}
      <Tags tags={details.positionTags} />
      {details.risk !== undefined && (
        <p>
          Risk: {RISK_LABELS[details.risk]}
          {details.companyRisk !== undefined && <span className="muted"> (company: {details.companyRisk})</span>}
        </p>
      )}
      <Level label={t('Target')} price={details.targetPrice} distance={details.targetDistance} currency={priceCurrency} />
      <Level label={t('Stop')} price={details.stopPrice} distance={details.stopDistance} currency={priceCurrency} />
      {details.plan && <Markdown text={details.plan} />}
    </section>
  )
}
