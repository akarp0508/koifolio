import { describe, expect, it } from 'vitest'
import { fieldChanges } from './auditChanges'

describe('fieldChanges', () => {
  it('lists only what differs', () => {
    expect(fieldChanges({ name: 'Main', balance: 500 }, { name: 'Main', balance: 600 }))
      .toEqual([{ field: 'balance', before: '500', after: '600' }])
  })

  it('lists every field of something new', () => {
    expect(fieldChanges(undefined, { ticker: 'PAS', risk: 2 })).toEqual([
      { field: 'ticker', before: undefined, after: 'PAS' },
      { field: 'risk', before: undefined, after: '2' },
    ])
  })

  it('lists every field of something removed', () => {
    expect(fieldChanges({ ticker: 'PAS' }, null)).toEqual([{ field: 'ticker', before: 'PAS', after: undefined }])
  })

  it('shows a field that appeared or went away', () => {
    expect(fieldChanges({ a: '1' }, { b: '2' })).toEqual([
      { field: 'a', before: '1', after: undefined },
      { field: 'b', before: undefined, after: '2' },
    ])
  })

  it('keeps long values whole', () => {
    expect(fieldChanges(undefined, { analysis: 'x'.repeat(400) })[0].after).toHaveLength(400)
  })

  it('shows lists as text', () => {
    expect(fieldChanges(undefined, { tags: ['core'] })[0].after).toBe('["core"]')
  })
})
