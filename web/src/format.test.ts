import { describe, expect, it } from 'vitest'
import { formatAmount, normalizeAmount } from './format'

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

describe('normalizeAmount', () => {
  it('accepts a decimal comma and spaces', () => {
    expect(normalizeAmount('5 000,50')).toBe('5000.50')
  })

  it('leaves plain numbers alone', () => {
    expect(normalizeAmount('-12.5')).toBe('-12.5')
  })
})
