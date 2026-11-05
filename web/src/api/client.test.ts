import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, hasToken, setUnauthorizedHandler } from './client'

function respond(status: number, body?: unknown) {
  vi.stubGlobal('fetch', vi.fn(async () =>
    new Response(body === undefined ? null : JSON.stringify(body), { status })))
}

function lastHeaders() {
  const call = vi.mocked(fetch).mock.calls.at(-1)
  return call?.[1]?.headers as Record<string, string>
}

beforeEach(() => {
  const store = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => store.set(key, value),
    removeItem: (key: string) => store.delete(key),
  })
  setUnauthorizedHandler(() => {})
})

describe('api client', () => {
  it('stores the token on login and sends it afterwards', async () => {
    respond(200, { accessToken: 'abc', expiresIn: 60 })
    await api.login('anna', 'password')
    expect(hasToken()).toBe(true)

    respond(200, [])
    await api.listAccounts()
    expect(lastHeaders().Authorization).toBe('Bearer abc')
  })

  it('turns a problem response into an ApiError with field errors', async () => {
    respond(400, {
      detail: 'password must be at least 8 characters',
      code: 'validation_failed',
      errors: [{ field: 'password', message: 'must be at least 8 characters' }],
    })

    const error = await api.register('anna', 'short').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.message).toBe('password must be at least 8 characters')
    expect(error.code).toBe('validation_failed')
    expect(error.fieldErrors.password).toBe('must be at least 8 characters')
  })

  it('reports a body without details', async () => {
    respond(500)
    await expect(api.listAccounts()).rejects.toThrow('The server answered with an error (500)')
  })

  it('reports an unreachable server', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('failed') }))
    await expect(api.listAccounts()).rejects.toThrow("Can't reach the server")
  })

  it('logs out when the server rejects the stored token', async () => {
    respond(200, { accessToken: 'abc', expiresIn: 60 })
    await api.login('anna', 'password')
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)

    respond(401)
    await api.listAccounts().catch(() => {})

    expect(hasToken()).toBe(false)
    expect(unauthorized).toHaveBeenCalledOnce()
  })

  it('does not log out on a wrong password', async () => {
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)

    respond(401, { detail: 'Wrong username or password', code: 'invalid_credentials' })
    await expect(api.login('anna', 'nope')).rejects.toThrow('Wrong username or password')

    expect(unauthorized).not.toHaveBeenCalled()
  })

  it('handles an empty 204 response', async () => {
    respond(204)
    await expect(api.deleteAccount('1')).resolves.toBeUndefined()
  })
})
