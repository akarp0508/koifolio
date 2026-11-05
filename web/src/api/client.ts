import type { components } from './schema'

type Schemas = components['schemas']

// The spec marks every response field optional, but the server always sends them
export type Account = Required<Schemas['AccountResponse']>
export type User = Required<Schemas['UserResponse']>
export type Token = Required<Schemas['Token']>
export type NewAccount = Schemas['CreateAccountRequest']
export type AccountChanges = Schemas['UpdateAccountRequest']
export type Instrument = Required<Schemas['InstrumentResponse']>
export type TransactionInput = Schemas['TransactionRequest']
export type TransactionType = TransactionInput['type']
// Fields a type doesn't use are left out of the response, so only these are always there
export type Transaction = Schemas['TransactionResponse'] & Required<Pick<Schemas['TransactionResponse'],
  'id' | 'accountId' | 'type' | 'date' | 'currency' | 'cashEffect' | 'affectsBalance' | 'oversold'>>

const TOKEN_KEY = 'koifolio.token'

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, code?: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

let onUnauthorized = () => {}

/** Called when the server rejects the stored token, so the app can return to the login page. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export function hasToken() {
  return localStorage.getItem(TOKEN_KEY) !== null
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

async function toApiError(response: Response) {
  const problem = await response.json().catch(() => null)
  const fieldErrors = Object.fromEntries(
    (problem?.errors ?? []).map((error: { field: string; message: string }) => [error.field, error.message]),
  )
  const message = problem?.detail ?? `The server answered with an error (${response.status})`
  return new ApiError(response.status, message, problem?.code, fieldErrors)
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = localStorage.getItem(TOKEN_KEY)
  const headers: Record<string, string> = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, "Can't reach the server")
  }

  if (response.status === 401 && token) {
    clearToken()
    onUnauthorized()
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

export const api = {
  async login(username: string, password: string) {
    const token = await request<Token>('POST', '/auth/login', { username, password })
    localStorage.setItem(TOKEN_KEY, token.accessToken)
  },
  register: (username: string, password: string) =>
    request<User>('POST', '/auth/register', { username, password }),
  me: () => request<User>('GET', '/auth/me'),
  listAccounts: () => request<Account[]>('GET', '/accounts'),
  createAccount: (account: NewAccount) => request<Account>('POST', '/accounts', account),
  updateAccount: (id: string, changes: AccountChanges) => request<Account>('PATCH', `/accounts/${id}`, changes),
  deleteAccount: (id: string) => request<void>('DELETE', `/accounts/${id}`),
  listInstruments: () => request<Instrument[]>('GET', '/instruments'),
  createInstrument: (ticker: string, name: string) => request<Instrument>('POST', '/instruments', { ticker, name }),
  renameInstrument: (id: string, name: string) => request<Instrument>('PATCH', `/instruments/${id}`, { name }),
  deleteInstrument: (id: string) => request<void>('DELETE', `/instruments/${id}`),
  listTransactions: () => request<Transaction[]>('GET', '/transactions'),
  createTransaction: (input: TransactionInput) => request<Transaction>('POST', '/transactions', input),
  updateTransaction: (id: string, input: TransactionInput) =>
    request<Transaction>('PUT', `/transactions/${id}`, input),
  deleteTransaction: (id: string) => request<void>('DELETE', `/transactions/${id}`),
}
