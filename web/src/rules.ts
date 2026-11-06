import type { Rule } from './api/client'
import { t } from './i18n'

/** Says what a rule asks for, in words. */
export function ruleLabel(rule: Pick<Rule, 'type' | 'limitPercent' | 'maxRisk'>) {
  return rule.type === 'max_position_weight'
    ? t('Max {limit}% in one company', { limit: rule.limitPercent })
    : t('At least {limit}% at risk {risk} or lower, cash included', { limit: rule.limitPercent, risk: rule.maxRisk ?? '' })
}
