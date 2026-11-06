import type { Instrument, Transaction, TransactionType } from './api/client'
import { formatAmount } from './format'
import { t } from './i18n'

export const TYPE_LABELS: Record<TransactionType, string> = {
  deposit: t('Deposit'),
  withdrawal: t('Withdrawal'),
  buy: t('Buy'),
  sell: t('Sell'),
  dividend: t('Dividend'),
  interest: t('Interest'),
  fee: t('Fee'),
  split: t('Split'),
}

export const TYPES = Object.keys(TYPE_LABELS) as TransactionType[]

export function isTrade(type: TransactionType) {
  return type === 'buy' || type === 'sell'
}

/** Today in the user's time zone, as yyyy-mm-dd. */
export function today(now = new Date()) {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** History dated before the balance was last typed in is already part of that balance. ISO dates sort as text. */
export function defaultAffectsBalance(date: string, balanceSetOn: string) {
  return date >= balanceSetOn
}

export function details(transaction: Transaction) {
  if (transaction.type === 'split') {
    return t('{ticker} {to} for {from}', { ticker: transaction.ticker ?? '', to: transaction.splitTo ?? '', from: transaction.splitFrom ?? '' })
  }
  if (isTrade(transaction.type)) {
    return `${transaction.quantity} ${transaction.ticker} @ ${formatAmount(transaction.price ?? '')}`
  }
  return transaction.ticker ?? ''
}

export function instrumentLabel(instrument: Instrument) {
  return instrument.name ? `${instrument.ticker} — ${instrument.name}` : instrument.ticker
}

/** The list to pick from, keeping a ticker that was removed from it but is still on the transaction. */
export function tickerOptions(instruments: Instrument[], current: string) {
  const listed = instruments.map((instrument) => ({ value: instrument.ticker, label: instrumentLabel(instrument) }))
  const onList = current === '' || instruments.some((instrument) => instrument.ticker === current)
  return onList ? listed : [{ value: current, label: current }, ...listed]
}

/** A split moves no money, so it has no amount to show. */
export function transactionAmount(transaction: Transaction) {
  return transaction.type === 'split' ? '—' : `${signedAmount(transaction.cashEffect)} ${transaction.currency}`
}

export function signedAmount(cashEffect: string) {
  const positive = !cashEffect.startsWith('-')
  return `${positive ? '+' : '−'}${formatAmount(cashEffect.replace('-', ''))}`
}
