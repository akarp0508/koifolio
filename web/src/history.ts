export const RANGES = ['1M', '6M', '1Y', 'All'] as const
export type Range = (typeof RANGES)[number]

const MONTHS: Record<Exclude<Range, 'All'>, number> = { '1M': 1, '6M': 6, '1Y': 12 }

/** The first date a range shows, as yyyy-mm-dd, or null for everything. */
export function rangeStart(range: Range, today: string) {
  if (range === 'All') {
    return null
  }
  const [year, month, day] = today.split('-').map(Number)
  const start = new Date(Date.UTC(year, month - 1 - MONTHS[range], day))
  return start.toISOString().slice(0, 10)
}

/** Keeps points from a range onwards. ISO dates sort as text. */
export function inRange<T extends { date?: string }>(points: T[], range: Range, today: string) {
  const start = rangeStart(range, today)
  return start === null ? points : points.filter((point) => (point.date ?? '') >= start)
}
