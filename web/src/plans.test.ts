import { describe, expect, it } from 'vitest'
import { signalClass, usedSectors } from './plans'

describe('signalClass', () => {
  it('reads the target as good and the stop as bad', () => {
    expect(signalClass('TARGET_REACHED')).toContain('gain')
    expect(signalClass('NEAR_TARGET')).toContain('gain')
    expect(signalClass('STOP_HIT')).toContain('loss')
    expect(signalClass('NEAR_STOP')).toContain('loss')
  })
})

describe('usedSectors', () => {
  it('lists each sector once, sorted, without blanks', () => {
    expect(usedSectors([{ sector: 'IT' }, { sector: '' }, { sector: 'Banks' }, { sector: 'IT' }, {}])).toEqual(['Banks', 'IT'])
  })
})
