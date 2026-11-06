import type { Rule } from './api/client'

/** Says what a rule asks for, in words. */
export function ruleLabel(rule: Pick<Rule, 'type' | 'limitPercent' | 'maxRisk'>) {
  return rule.type === 'max_position_weight'
    ? `Max ${rule.limitPercent}% in one company`
    : `At least ${rule.limitPercent}% at risk ${rule.maxRisk} or lower, cash included`
}
