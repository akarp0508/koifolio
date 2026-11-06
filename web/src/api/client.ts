import type { components } from './schema'

type Schemas = components['schemas']

// The spec marks every response field optional, but the server always sends them
export type Account = Required<Schemas['AccountResponse']>
export type User = Required<Schemas['UserResponse']>
export type Token = Required<Schemas['Token']>
export type NewAccount = Schemas['CreateAccountRequest']
export type AccountChanges = Schemas['UpdateAccountRequest']
// risk is left out when it isn't set
export type Instrument = Schemas['InstrumentResponse'] & Required<Pick<Schemas['InstrumentResponse'],
  'id' | 'ticker' | 'name' | 'sector' | 'thesis' | 'analysis'>>
export type InstrumentInput = Schemas['UpdateInstrumentRequest']
export type DashboardSlice = Schemas['SliceResponse']
export type Dashboard = Schemas['DashboardResponse'] & Required<Pick<Schemas['DashboardResponse'],
  'currency' | 'value' | 'unrealizedGain' | 'realizedGain' | 'dividends' | 'dividendsWithheld' | 'bySector' | 'byRisk' | 'attention' | 'unpriced' | 'unvalidated'>>
export type Settings = Required<Schemas['SettingsResponse']>
export type RestoredData = Required<Schemas['RestoredResponse']>
export type Rule = Schemas['RuleResponse'] & Required<Pick<Schemas['RuleResponse'], 'id' | 'type' | 'limitPercent'>>
export type RuleInput = Schemas['RuleRequest']
export type AgentToken = Schemas['AgentTokenResponse'] & Required<Pick<Schemas['AgentTokenResponse'], 'id' | 'name'>>
export type AgentScope = NonNullable<Schemas['AgentTokenResponse']['scopes']>[number]
export type AuditEntry = Schemas['AuditEntryResponse']
export type NewAgentToken = Required<Pick<Schemas['CreatedAgentTokenResponse'], 'secret'>> & { token: AgentToken }
export type RuleResult = Omit<Required<Schemas['RuleCheckResponse']>, 'actual' | 'rule'> & { actual?: string; rule: Rule }
export type TaxEstimate = Schemas['TaxResponse']
export type SplitSuggestion = Schemas['SuggestionResponse']
export type PortfolioHistory = Required<Schemas['PortfolioHistoryResponse']>
export type PortfolioPoint = NonNullable<Schemas['PortfolioHistoryResponse']['points']>[number]
export type PeriodChange = NonNullable<Schemas['PortfolioHistoryResponse']['changes']>[number]
export type DashboardFilter = { account?: string; instrument?: string; instrumentTags?: string[]; positionTags?: string[] }
export type PositionDetails = Schemas['DetailsResponse']
export type PlanInput = Schemas['PlanRequest']
export type CostMethod = NonNullable<Schemas['AccountResponse']['costMethod']>
// averageCost and the price fields are left out when they don't apply
export type Position = Schemas['PositionResponse'] & Required<Pick<Schemas['PositionResponse'],
  'accountId' | 'ticker' | 'currency' | 'quantity' | 'cost' | 'realizedGain' | 'closed' | 'incomplete' | 'currencyMismatch'>>
export type PositionHistory = Required<Schemas['HistoryResponse']>
export type HistoryPoint = PositionHistory['points'][number]
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

async function send(method: string, path: string, body?: unknown): Promise<Response> {
  const token = localStorage.getItem(TOKEN_KEY)
  const headers: Record<string, string> = {}
  const upload = body instanceof FormData
  if (body !== undefined && !upload) {
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
      body: body === undefined || upload ? body as FormData | undefined : JSON.stringify(body),
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
  return response
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await send(method, path, body)
  return response.status === 204 ? (undefined as T) : response.json()
}

function filterQuery(filter: DashboardFilter) {
  const query = new URLSearchParams()
  if (filter.account) {
    query.set('account', filter.account)
  }
  if (filter.instrument) {
    query.set('instrument', filter.instrument)
  }
  filter.instrumentTags?.forEach((tag) => query.append('instrumentTag', tag))
  filter.positionTags?.forEach((tag) => query.append('positionTag', tag))
  return query
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
  getInstrument: (id: string) => request<Instrument>('GET', `/instruments/${id}`),
  createInstrument: (input: InstrumentInput & { ticker: string }) => request<Instrument>('POST', '/instruments', input),
  updateInstrument: (id: string, input: InstrumentInput) => request<Instrument>('PUT', `/instruments/${id}`, input),
  positionDetails: (accountId: string, ticker: string) =>
    request<PositionDetails>('GET', `/positions/${accountId}/${encodeURIComponent(ticker)}/details`),
  savePlan: (accountId: string, ticker: string, input: PlanInput) =>
    request<PositionDetails>('PUT', `/positions/${accountId}/${encodeURIComponent(ticker)}/plan`, input),
  deleteInstrument: (id: string) => request<void>('DELETE', `/instruments/${id}`),
  listPositions: () => request<Position[]>('GET', '/positions?closed=true'),
  positionHistory: (accountId: string, ticker: string) =>
    request<PositionHistory>('GET', `/positions/${accountId}/${encodeURIComponent(ticker)}/history`),
  dashboard: (filter: DashboardFilter) => request<Dashboard>('GET', `/dashboard?${filterQuery(filter)}`),
  portfolioHistory: (filter: DashboardFilter) =>
    request<PortfolioHistory>('GET', `/dashboard/history?${filterQuery(filter)}`),
  splitSuggestions: () => request<SplitSuggestion[]>('GET', '/split-suggestions'),
  applySplit: (id: string) => request<void>('POST', `/split-suggestions/${id}/apply`),
  dismissSplit: (id: string) => request<void>('POST', `/split-suggestions/${id}/dismiss`),
  tax: (year?: number) => request<TaxEstimate>('GET', year ? `/tax?year=${year}` : '/tax'),
  listAgentTokens: () => request<AgentToken[]>('GET', '/agent-tokens'),
  createAgentToken: (input: Schemas['AgentTokenRequest']) => request<NewAgentToken>('POST', '/agent-tokens', input),
  revokeAgentToken: (id: string) => request<void>('DELETE', `/agent-tokens/${id}`),
  listRules: () => request<Rule[]>('GET', '/rules'),
  createRule: (input: RuleInput) => request<Rule>('POST', '/rules', input),
  updateRule: (id: string, input: RuleInput) => request<Rule>('PUT', `/rules/${id}`, input),
  deleteRule: (id: string) => request<void>('DELETE', `/rules/${id}`),
  checkRules: () => request<RuleResult[]>('GET', '/rules/check'),
  async exportData() {
    const response = await send('GET', '/export')
    const name = response.headers.get('Content-Disposition')?.match(/filename="?([^"]+)"?/)?.[1] ?? 'koifolio-export.zip'
    return { blob: await response.blob(), name }
  },
  restoreData(file: File) {
    const form = new FormData()
    form.append('file', file)
    return request<RestoredData>('POST', '/import?mode=replace', form)
  },
  settings: () => request<Settings>('GET', '/settings'),
  updateSettings: (changes: Schemas['SettingsRequest']) => request<Settings>('PATCH', '/settings', changes),
  refreshPrices: () => request<void>('POST', '/prices/refresh'),
  listTransactions: () => request<Transaction[]>('GET', '/transactions'),
  listUnvalidated: () => request<Transaction[]>('GET', '/transactions?unvalidated=true'),
  approveTransaction: (id: string) => request<Transaction>('POST', `/transactions/${id}/approve`),
  approveAllTransactions: () => request<{ approved?: number }>('POST', '/transactions/approve-all'),
  rejectTransaction: (id: string) => request<void>('POST', `/transactions/${id}/reject`),
  auditLog: () => request<AuditEntry[]>('GET', '/audit-log'),
  createTransaction: (input: TransactionInput) => request<Transaction>('POST', '/transactions', input),
  updateTransaction: (id: string, input: TransactionInput) =>
    request<Transaction>('PUT', `/transactions/${id}`, input),
  deleteTransaction: (id: string) => request<void>('DELETE', `/transactions/${id}`),
}
