const THIN_SPACE = ' '

/** Groups thousands in a decimal string. Works on the text, so the value never passes through a float. */
export function formatAmount(amount: string) {
  const [, sign, whole, fraction] = amount.match(/^(-?)(\d+)(\.\d+)?$/) ?? []
  if (whole === undefined) {
    return amount
  }
  return sign + whole.replace(/\B(?=(\d{3})+$)/g, THIN_SPACE) + (fraction ?? '')
}

/** Accepts what people type, like "5 000,50", and returns what the API expects. */
export function normalizeAmount(input: string) {
  return input.replace(/\s/g, '').replace(',', '.')
}
