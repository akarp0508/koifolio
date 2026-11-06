import { describe, expect, it } from 'vitest'
import { ruleLabel } from './rules'

describe('ruleLabel', () => {
  it('words a maximum', () => {
    expect(ruleLabel({ type: 'max_position_weight', limitPercent: '20' })).toBe('Max 20% in one company')
  })

  it('words a low-risk minimum', () => {
    expect(ruleLabel({ type: 'min_low_risk_weight', limitPercent: '50', maxRisk: 2 }))
      .toBe('At least 50% at risk 2 or lower, cash included')
  })
})
