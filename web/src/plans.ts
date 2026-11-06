import type { Position } from './api/client'
import { t } from './i18n'

type Signal = NonNullable<Position['signal']>

export const SIGNAL_LABELS: Record<Signal, string> = {
  NEAR_TARGET: t('near target'),
  TARGET_REACHED: t('target reached'),
  NEAR_STOP: t('near stop'),
  STOP_HIT: t('stop hit'),
}

export function signalClass(signal: Signal) {
  return signal === 'NEAR_TARGET' || signal === 'TARGET_REACHED' ? 'signal gain' : 'signal loss'
}

export const RISK_LABELS: Record<number, string> = {
  1: t('1 — very low'),
  2: t('2 — low'),
  3: t('3 — medium'),
  4: t('4 — high'),
  5: t('5 — very high'),
}

/** Sectors already used, for suggestions, so breakdowns stay tidy. */
export function usedSectors(instruments: { sector?: string }[]) {
  const sectors = instruments.map((instrument) => instrument.sector ?? '').filter((sector) => sector !== '')
  return [...new Set(sectors)].sort()
}

/** Reads what's typed into a tags field: separated by commas. The server cleans them up. */
export function parseTags(text: string) {
  return text.split(',').map((tag) => tag.trim()).filter((tag) => tag !== '')
}

/** Every tag of one kind across positions, for the filters. */
export function allTags(positions: Position[], kind: 'instrumentTags' | 'positionTags') {
  return [...new Set(positions.flatMap((position) => position[kind] ?? []))].sort()
}
