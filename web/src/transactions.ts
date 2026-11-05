import type { Transaction, TransactionType } from './api/client'
import { formatAmount } from './format'

export const TYPE_LABELS: Record<TransactionType, string> = {
  deposit: 'Deposit',
  withdrawal: 'Withdrawal',
  buy: 'Buy',
  sell: 'Sell',
  dividend: 'Dividend',
  fee: 'Fee',
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
  if (isTrade(transaction.type)) {
    return `${transaction.quantity} ${transaction.ticker} @ ${formatAmount(transaction.price ?? '')}`
  }
  return transaction.ticker ?? ''
}

export function signedAmount(cashEffect: string) {
  const positive = !cashEffect.startsWith('-')
  return `${positive ? '+' : '−'}${formatAmount(cashEffect.replace('-', ''))}`
}
