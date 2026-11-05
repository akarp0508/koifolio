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

/** Accepts what people type, like "5 000,50", and returns what the API expects. */
export function normalizeAmount(input: string) {
  return input.replace(/\s/g, '').replace(',', '.')
}
