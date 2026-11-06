import { describe, expect, it } from 'vitest'
import type { Transaction } from './api/client'
import { defaultAffectsBalance, details, instrumentLabel, signedAmount, tickerOptions, today, transactionAmount } from './transactions'

const THIN = ' '

function instrument(ticker: string, name: string) {
  return { id: ticker, ticker, name, sector: '', thesis: '', analysis: '' }
}

const base = {
  id: '1',
  accountId: 'a',
  date: '2026-11-05',
  currency: 'PLN',
  cashEffect: '0.00',
  affectsBalance: true,
  oversold: false,
}

describe('defaultAffectsBalance', () => {
  it('is off for history before the balance was set', () => {
    expect(defaultAffectsBalance('2024-03-12', '2026-11-05')).toBe(false)
  })

  it('is on from that day onwards', () => {
    expect(defaultAffectsBalance('2026-11-05', '2026-11-05')).toBe(true)
    expect(defaultAffectsBalance('2026-11-06', '2026-11-05')).toBe(true)
  })

  it('compares across years', () => {
    expect(defaultAffectsBalance('2025-12-31', '2026-01-01')).toBe(false)
  })
})

describe('details', () => {
  it('describes a trade', () => {
    const buy: Transaction = { ...base, type: 'buy', ticker: 'PAS', quantity: '10', price: '1200.50' }
    expect(details(buy)).toBe(`10 PAS @ 1${THIN}200.50`)
  })

  it('shows the ticker of a dividend', () => {
    expect(details({ ...base, type: 'dividend', ticker: 'PAS', amount: '50.00' })).toBe('PAS')
  })

  it('describes a split', () => {
    expect(details({ ...base, type: 'split', ticker: 'DNP', splitFrom: '1', splitTo: '10' })).toBe('DNP 10 for 1')
  })

  it('is empty for cash', () => {
    expect(details({ ...base, type: 'deposit', amount: '50.00' })).toBe('')
  })
})

describe('instrumentLabel', () => {
  it('adds the name when there is one', () => {
    expect(instrumentLabel(instrument('PAS', 'Passus'))).toBe('PAS — Passus')
    expect(instrumentLabel(instrument('PAS', ''))).toBe('PAS')
  })
})

describe('tickerOptions', () => {
  const instruments = [instrument('PAS', 'Passus'), instrument('XTB', '')]

  it('lists the instruments', () => {
    expect(tickerOptions(instruments, '').map((option) => option.value)).toEqual(['PAS', 'XTB'])
    expect(tickerOptions(instruments, 'XTB').map((option) => option.value)).toEqual(['PAS', 'XTB'])
  })

  it('keeps a ticker that is no longer on the list', () => {
    expect(tickerOptions(instruments, 'OLD').map((option) => option.value)).toEqual(['OLD', 'PAS', 'XTB'])
  })
})

describe('transactionAmount', () => {
  it('shows money with its currency', () => {
    expect(transactionAmount({ ...base, type: 'deposit', cashEffect: '50.00' })).toBe('+50.00 PLN')
  })

  it('has none for a split', () => {
    expect(transactionAmount({ ...base, type: 'split' })).toBe('—')
  })
})

describe('signedAmount', () => {
  it('shows the direction', () => {
    expect(signedAmount('50.00')).toBe('+50.00')
    expect(signedAmount('-1200.00')).toBe(`−1${THIN}200.00`)
  })
})

describe('today', () => {
  it('uses local date parts, not UTC', () => {
    expect(today(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
  })
})
