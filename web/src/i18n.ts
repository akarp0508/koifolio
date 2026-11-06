import { pl } from './i18n.pl'

export type Language = 'en' | 'pl'

const KEY = 'koifolio.language'

function detect(): Language {
  const stored = typeof localStorage === 'undefined' ? null : localStorage.getItem(KEY)
  if (stored === 'en' || stored === 'pl') {
    return stored
  }
  return typeof navigator !== 'undefined' && navigator.language.toLowerCase().startsWith('pl') ? 'pl' : 'en'
}

export const language: Language = detect()

/** Labels are read once at load, some of them in module constants, so a change takes a reload. */
export function setLanguage(next: Language) {
  localStorage.setItem(KEY, next)
  window.location.reload()
}

/** English text is the key, so anything without a translation still reads. {name} takes a value from params. */
export function t(text: string, params: Record<string, string | number> = {}) {
  const template = language === 'pl' ? pl[text] ?? text : text
  return template.replace(/\{(\w+)\}/g, (match, name: string) => (name in params ? String(params[name]) : match))
}

/** Polish has three plural forms: 1 plik, 2 pliki, 5 plików. */
export function plural(count: number, one: string, few: string, many: string) {
  if (language !== 'pl') {
    return count === 1 ? one : few
  }
  if (count === 1) {
    return one
  }
  const tens = count % 100
  const units = count % 10
  return units >= 2 && units <= 4 && (tens < 12 || tens > 14) ? few : many
}
