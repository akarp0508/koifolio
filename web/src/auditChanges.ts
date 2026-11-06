export type FieldChange = { field: string; before?: string; after?: string }

const LIMIT = 300

function text(value: unknown) {
  if (value === undefined || value === null) {
    return undefined
  }
  const shown = typeof value === 'string' ? value : JSON.stringify(value)
  return shown.length > LIMIT ? `${shown.slice(0, LIMIT)}…` : shown
}

/** The fields that differ between two sides of a log entry. Something new or removed lists all of its fields. */
export function fieldChanges(before: unknown, after: unknown): FieldChange[] {
  const old = (before ?? {}) as Record<string, unknown>
  const current = (after ?? {}) as Record<string, unknown>
  return [...new Set([...Object.keys(old), ...Object.keys(current)])]
    .filter((field) => JSON.stringify(old[field]) !== JSON.stringify(current[field]))
    .map((field) => ({ field, before: text(old[field]), after: text(current[field]) }))
}
