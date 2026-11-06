/// <reference types="node" />
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { pl } from './i18n.pl'

function sources(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) {
      return sources(path)
    }
    return /\.tsx?$/.test(entry.name) && !/\.test\.|\.d\.ts|i18n/.test(entry.name) ? [path] : []
  })
}

/** Every text passed to t(), including both sides of a ternary inside it. */
function keys() {
  const found = new Set<string>()
  const string = String.raw`(?:'((?:[^'\\]|\\.)*)'|"((?:[^"\\]|\\.)*)")`
  const call = new RegExp(String.raw`\bt\(\s*(?:[^()'"]*\?\s*)?${string}(?:\s*:\s*${string})?`, 'g')
  for (const path of sources(join(import.meta.dirname))) {
    for (const match of readFileSync(path, 'utf8').matchAll(call)) {
      match.slice(1).filter(Boolean).forEach((text) => found.add(text))
    }
  }
  return [...found]
}

const placeholders = (text: string) => (text.match(/\{\w+\}/g) ?? []).sort()

describe('the Polish dictionary', () => {
  it('finds the texts in the source', () => {
    expect(keys().length).toBeGreaterThan(300)
  })

  it('has every text', () => {
    expect(keys().filter((text) => !(text in pl))).toEqual([])
  })

  it('keeps every value in the sentence', () => {
    const broken = Object.entries(pl).filter(([english, polish]) => placeholders(english).join() !== placeholders(polish).join())
    expect(broken).toEqual([])
  })
})

describe('in Polish', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  async function polish() {
    vi.resetModules()
    vi.stubGlobal('navigator', { language: 'pl-PL' })
    return { ...(await import('./i18n')), ...(await import('./format')) }
  }

  it('translates and fills in values', async () => {
    const { t } = await polish()

    expect(t('Portfolio')).toBe('Portfel')
    expect(t('Remove {name}?', { name: 'PAS' })).toBe('Usunąć PAS?')
  })

  it('leaves unknown text, such as dynamic server messages, as it is', async () => {
    const { t } = await polish()

    expect(t('Something new')).toBe('Something new')
  })

  it('picks the right plural', async () => {
    const { plural } = await polish()
    const form = (count: number) => plural(count, 'zmiana', 'zmiany', 'zmian')

    expect([1, 2, 4, 5, 11, 12, 14, 21, 22, 25, 112].map(form)).toEqual(
      ['zmiana', 'zmiany', 'zmiany', 'zmian', 'zmian', 'zmian', 'zmian', 'zmian', 'zmiany', 'zmian', 'zmian'])
  })

  it('writes decimals with a comma', async () => {
    const { formatAmount, formatGain } = await polish()

    expect(formatAmount('1234.56')).toBe('1\u202f234,56')
    expect(formatGain('-3.50')).toBe('−3,50')
  })
})
