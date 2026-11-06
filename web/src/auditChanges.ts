export type FieldChange = { field: string; before?: string; after?: string }

function text(value: unknown) {
  if (value === undefined || value === null) {
    return undefined
  }
  return typeof value === 'string' ? value : JSON.stringify(value)
}

/** The fields that differ between two sides of a change. Something new or removed lists all of its fields. */
export function fieldChanges(before: unknown, after: unknown): FieldChange[] {
  const old = (before ?? {}) as Record<string, unknown>
  const current = (after ?? {}) as Record<string, unknown>
  return [...new Set([...Object.keys(old), ...Object.keys(current)])]
    .filter((field) => JSON.stringify(old[field]) !== JSON.stringify(current[field]))
    .map((field) => ({ field, before: text(old[field]), after: text(current[field]) }))
}
