import { describe, expect, it } from 'vitest'
import { formatAmount, formatGain, formatQuoteTime, gainClass, normalizeAmount } from './format'

const THIN = ' '

describe('formatAmount', () => {
  it('groups thousands', () => {
    expect(formatAmount('5000.00')).toBe(`5${THIN}000.00`)
    expect(formatAmount('1234567.89')).toBe(`1${THIN}234${THIN}567.89`)
  })

  it('leaves short amounts alone', () => {
    expect(formatAmount('300.00')).toBe('300.00')
    expect(formatAmount('0.10')).toBe('0.10')
  })

  it('handles negatives and no decimals', () => {
    expect(formatAmount('-1000.50')).toBe(`-1${THIN}000.50`)
    expect(formatAmount('1000')).toBe(`1${THIN}000`)
  })

  it('does not lose precision on huge values', () => {
    expect(formatAmount('123456789012345.67')).toBe(`123${THIN}456${THIN}789${THIN}012${THIN}345.67`)
  })
})

describe('formatGain', () => {
  it('shows the direction', () => {
    expect(formatGain('250.00')).toBe('+250.00')
    expect(formatGain('-1000.00')).toBe(`−1${THIN}000.00`)
  })

  it('leaves zero without a sign', () => {
    expect(formatGain('0.00')).toBe('0.00')
    expect(formatGain('0')).toBe('0')
  })
})

describe('gainClass', () => {
  it('follows the sign', () => {
    expect(gainClass('12.50')).toBe('gain')
    expect(gainClass('-0.01')).toBe('loss')
  })

  it('stays neutral at zero or without a value', () => {
    expect(gainClass('0.00')).toBe('')
    expect(gainClass('-0.00')).toBe('')
    expect(gainClass(undefined)).toBe('')
  })
})

describe('formatQuoteTime', () => {
  const now = new Date(2026, 10, 5, 18, 0)

  it('is nothing without prices', () => {
    expect(formatQuoteTime([undefined], now)).toBeNull()
  })

  it('shows only the time for today, using the newest', () => {
    const older = new Date(2026, 10, 5, 10, 0).toISOString()
    const newer = new Date(2026, 10, 5, 17, 0).toISOString()
    const expected = new Date(2026, 10, 5, 17, 0).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    expect(formatQuoteTime([older, undefined, newer], now)).toBe(expected)
  })

  it('adds the date for an earlier day', () => {
    const friday = new Date(2026, 10, 2, 17, 0)
    expect(formatQuoteTime([friday.toISOString()], now)).toContain(friday.toLocaleDateString())
  })
})

describe('normalizeAmount', () => {
  it('accepts a decimal comma and spaces', () => {
    expect(normalizeAmount('5 000,50')).toBe('5000.50')
  })

  it('leaves plain numbers alone', () => {
    expect(normalizeAmount('-12.5')).toBe('-12.5')
  })
})
