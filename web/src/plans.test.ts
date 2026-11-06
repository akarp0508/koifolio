import { describe, expect, it } from 'vitest'
import type { Position } from './api/client'
import { allTags, parseTags, signalClass, usedSectors } from './plans'

describe('signalClass', () => {
  it('reads the target as good and the stop as bad', () => {
    expect(signalClass('TARGET_REACHED')).toContain('gain')
    expect(signalClass('NEAR_TARGET')).toContain('gain')
    expect(signalClass('STOP_HIT')).toContain('loss')
    expect(signalClass('NEAR_STOP')).toContain('loss')
  })
})

describe('parseTags', () => {
  it('splits on commas and drops blanks', () => {
    expect(parseTags(' dividend, long term ,,')).toEqual(['dividend', 'long term'])
    expect(parseTags('')).toEqual([])
  })
})

describe('allTags', () => {
  it('keeps the two kinds apart', () => {
    const positions = [
      { instrumentTags: ['dividend'], positionTags: ['core'] },
      { instrumentTags: ['dividend', 'bank'] },
    ] as Position[]

    expect(allTags(positions, 'instrumentTags')).toEqual(['bank', 'dividend'])
    expect(allTags(positions, 'positionTags')).toEqual(['core'])
  })
})

describe('usedSectors', () => {
  it('lists each sector once, sorted, without blanks', () => {
    expect(usedSectors([{ sector: 'IT' }, { sector: '' }, { sector: 'Banks' }, { sector: 'IT' }, {}])).toEqual(['Banks', 'IT'])
  })
})
