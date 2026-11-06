import { describe, expect, it } from 'vitest'
import { inRange, rangeStart } from './history'

describe('rangeStart', () => {
  it('goes back whole months', () => {
    expect(rangeStart('1M', '2026-11-06')).toBe('2026-10-06')
    expect(rangeStart('6M', '2026-11-06')).toBe('2026-05-06')
    expect(rangeStart('1Y', '2026-11-06')).toBe('2025-11-06')
  })

  it('crosses a year', () => {
    expect(rangeStart('1M', '2026-01-15')).toBe('2025-12-15')
  })

  it('has no start for All', () => {
    expect(rangeStart('All', '2026-11-06')).toBeNull()
  })
})

describe('inRange', () => {
  const points = [{ date: '2025-01-01' }, { date: '2026-10-20' }, { date: '2026-11-05' }]

  it('keeps points from the start date on', () => {
    expect(inRange(points, '1M', '2026-11-06').map((point) => point.date)).toEqual(['2026-10-20', '2026-11-05'])
  })

  it('keeps everything for All', () => {
    expect(inRange(points, 'All', '2026-11-06')).toHaveLength(3)
  })
})
