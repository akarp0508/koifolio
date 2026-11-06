const THIN_SPACE = ' '

/** Groups thousands in a decimal string. Works on the text, so the value never passes through a float. */
export function formatAmount(amount: string) {
  const [, sign, whole, fraction] = amount.match(/^(-?)(\d+)(\.\d+)?$/) ?? []
  if (whole === undefined) {
    return amount
  }
  return sign + whole.replace(/\B(?=(\d{3})+$)/g, THIN_SPACE) + (fraction ?? '')
}

/** A gain or loss, with its direction. Zero has none. */
export function formatGain(amount: string) {
  if (/^-?[0.]+$/.test(amount)) {
    return formatAmount(amount)
  }
  return amount.startsWith('-') ? `−${formatAmount(amount.slice(1))}` : `+${formatAmount(amount)}`
}

/** The class that colours a gain or loss. Zero stays neutral. */
export function gainClass(amount?: string) {
  if (amount === undefined || /^-?[0.]+$/.test(amount)) {
    return ''
  }
  return amount.startsWith('-') ? 'loss' : 'gain'
}

/** The newest of some ISO timestamps, shown as a time today and with the date otherwise. */
export function formatQuoteTime(times: (string | undefined)[], now = new Date()) {
  const latest = times.filter((time) => time !== undefined).sort().at(-1)
  if (latest === undefined) {
    return null
  }
  const date = new Date(latest)
  const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  return date.toDateString() === now.toDateString() ? time : `${date.toLocaleDateString()} ${time}`
}

/** Accepts what people type, like "5 000,50", and returns what the API expects. */
export function normalizeAmount(input: string) {
  return input.replace(/\s/g, '').replace(',', '.')
}
