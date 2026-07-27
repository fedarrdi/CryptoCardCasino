export type AuthenticatedUser = {
  userId: string
  name: string
  walletAddress: string
}

export type LoginChallenge = {
  nonce: string
  message: string
  expiresAt: string
}

export type MarketCandle = {
  time: number
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export const BTC_CANDLE_INTERVALS = [
  '1h',
  '2h',
  '4h',
  '6h',
  '8h',
  '12h',
  '1d',
  '3d',
  '1w',
  '1M',
] as const

export type BtcCandleInterval = (typeof BTC_CANDLE_INTERVALS)[number]

export type BtcCandleHistory = {
  symbol: 'BTCUSDT'
  interval: BtcCandleInterval
  candles: MarketCandle[]
  hasMore: boolean
  nextBefore: number | null
}

export type BtcCandleUpdate = MarketCandle & {
  symbol: 'BTCUSDT'
  interval: BtcCandleInterval
  closed: boolean
}

export type PositionSide = 'LONG' | 'SHORT'

export type PaperTradingQuote = {
  symbol: string
  bidPrice: number
  askPrice: number
}

export type PaperTradingAccount = {
  initialBalance: number
  balance: number
  equity: number
  unrealizedPnl: number
  usedMargin: number
  availableMargin: number
}

type PaperPositionFields = {
  id: string
  clientOrderId: string
  symbol: string
  orderType: 'MARKET'
  marginMode: 'CROSS'
  side: PositionSide
  leverage: number
  marginUsd: number
  notionalUsd: number
  quantity: number
  entryPrice: number
  markPrice: number
  stopLoss: number | null
  takeProfit: number | null
  openedAt: string
}

export type OpenPaperPosition = PaperPositionFields & {
  status: 'OPEN'
  unrealizedPnl: number
  unrealizedRoePercent: number
  exitPrice: null
  realizedPnl: null
  closedAt: null
  closeReason: null
}

export type ClosedPaperTrade = PaperPositionFields & {
  status: 'CLOSED'
  unrealizedPnl: null
  unrealizedRoePercent: null
  exitPrice: number
  realizedPnl: number
  closedAt: string
  closeReason: 'USER' | 'STOP_LOSS' | 'TAKE_PROFIT'
}

export type PaperPosition = OpenPaperPosition | ClosedPaperTrade

export type PaperTradingPortfolio = {
  userId: string
  quote: PaperTradingQuote
  account: PaperTradingAccount
  openPositions: OpenPaperPosition[]
  closedTrades: ClosedPaperTrade[]
}

export type OpenPaperPositionRequest = {
  clientOrderId: string
  side: PositionSide
  leverage: number
  marginUsd: number
  stopLoss: number | null
  takeProfit: number | null
}

export type UpdateRiskControlsRequest = {
  stopLoss: number | null
  takeProfit: number | null
}

type ApiErrorBody = {
  code: string
  message: string
}

type CsrfToken = {
  headerName: string
  parameterName: string
  token: string
}

let sessionCsrf: CsrfToken | null = null
let csrfRequest: Promise<CsrfToken> | null = null
let csrfGeneration = 0

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
  }
}

export class SessionUserMismatchError extends Error {
  constructor() {
    super('The active wallet changed in another tab. Reconnect before trading.')
    this.name = 'SessionUserMismatchError'
  }
}

export class SessionSecurityChangedError extends Error {
  constructor() {
    super('Your session security token changed. Review the order and submit it again.')
    this.name = 'SessionSecurityChangedError'
  }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const body = (await response.json()) as ApiErrorBody

    if (response.status === 401) {
      clearSessionCsrf()
    }

    throw new ApiError(response.status, body)
  }

  return response
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await request(path, init)
  return response.json() as Promise<T>
}

export function getCurrentUser(signal?: AbortSignal): Promise<AuthenticatedUser> {
  return requestJson<AuthenticatedUser>('/api/auth/me', { signal })
}

export function clearSessionCsrf(): void {
  csrfGeneration += 1
  sessionCsrf = null
  csrfRequest = null
}

export function initializeSessionCsrf(): Promise<CsrfToken> {
  if (sessionCsrf !== null) {
    return Promise.resolve(sessionCsrf)
  }

  if (csrfRequest !== null) {
    return csrfRequest
  }

  const requestGeneration = csrfGeneration
  const currentRequest = requestJson<CsrfToken>('/api/auth/csrf')
    .then((csrf) => {
      if (requestGeneration !== csrfGeneration) {
        throw new Error('The wallet session changed while security was initialized.')
      }

      sessionCsrf = csrf
      return csrf
    })
    .finally(() => {
      if (csrfRequest === currentRequest) {
        csrfRequest = null
      }
    })

  csrfRequest = currentRequest
  return currentRequest
}

async function authenticatedMutationJson<T>(
  path: string,
  expectedUserId: string,
  init: Omit<RequestInit, 'method'> & { method: 'POST' | 'PATCH' },
): Promise<T> {
  await requireExpectedSessionUser(expectedUserId, init.signal)
  const csrf = await initializeSessionCsrf()
  const headers = new Headers(init.headers)
  headers.set(csrf.headerName, csrf.token)
  headers.set('X-Paper-Trading-User-Id', expectedUserId)

  try {
    return await requestJson<T>(path, {
      ...init,
      headers,
    })
  } catch (error) {
    if (
      error instanceof ApiError &&
      error.code === 'SESSION_USER_MISMATCH'
    ) {
      clearSessionCsrf()
      throw new SessionUserMismatchError()
    }

    if (error instanceof ApiError && error.status === 403) {
      clearSessionCsrf()
      await requireExpectedSessionUser(expectedUserId, init.signal)
      await initializeSessionCsrf()
      throw new SessionSecurityChangedError()
    }

    throw error
  }
}

async function requireExpectedSessionUser(
  expectedUserId: string,
  signal?: AbortSignal | null,
): Promise<void> {
  const currentUser = await getCurrentUser(signal ?? undefined)
  if (currentUser.userId !== expectedUserId) {
    clearSessionCsrf()
    throw new SessionUserMismatchError()
  }
}

export function createLoginChallenge(
  walletAddress: string,
  chainId: number,
): Promise<LoginChallenge> {
  return requestJson<LoginChallenge>('/api/auth/challenges', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ walletAddress, chainId }),
  })
}

export function createSession(
  nonce: string,
  signature: string,
): Promise<AuthenticatedUser> {
  return requestJson<AuthenticatedUser>('/api/auth/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nonce, signature }),
  })
}

export type BtcCandleHistoryRequest = {
  interval?: BtcCandleInterval
  before?: number
  limit?: number
  signal?: AbortSignal
}

export function getBtcCandles({
  interval,
  before,
  limit,
  signal,
}: BtcCandleHistoryRequest = {}): Promise<BtcCandleHistory> {
  const search = new URLSearchParams()

  if (interval !== undefined) {
    search.set('interval', interval)
  }

  if (before !== undefined) {
    search.set('before', String(before))
  }

  if (limit !== undefined) {
    search.set('limit', String(limit))
  }

  const query = search.size === 0 ? '' : `?${search.toString()}`
  return requestJson<BtcCandleHistory>(
    `/api/paper-trading/btc-candles${query}`,
    { signal },
  )
}

export function getPaperTradingPortfolio(
  closedTradeLimit = 50,
  signal?: AbortSignal,
): Promise<PaperTradingPortfolio> {
  const search = new URLSearchParams({
    closedTradeLimit: String(closedTradeLimit),
  })

  return requestJson<PaperTradingPortfolio>(
    `/api/paper-trading/portfolio?${search.toString()}`,
    { signal },
  )
}

export function openPaperPosition(
  position: OpenPaperPositionRequest,
  expectedUserId: string,
  signal?: AbortSignal,
): Promise<PaperTradingPortfolio> {
  return authenticatedMutationJson<PaperTradingPortfolio>(
    '/api/paper-trading/positions',
    expectedUserId,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(position),
      signal,
    },
  )
}

export function updatePaperPositionRiskControls(
  positionId: string,
  controls: UpdateRiskControlsRequest,
  expectedUserId: string,
  signal?: AbortSignal,
): Promise<PaperTradingPortfolio> {
  return authenticatedMutationJson<PaperTradingPortfolio>(
    `/api/paper-trading/positions/${encodeURIComponent(positionId)}/risk-controls`,
    expectedUserId,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(controls),
      signal,
    },
  )
}

export function closePaperPosition(
  positionId: string,
  expectedUserId: string,
  signal?: AbortSignal,
): Promise<PaperTradingPortfolio> {
  return authenticatedMutationJson<PaperTradingPortfolio>(
    `/api/paper-trading/positions/${encodeURIComponent(positionId)}/close`,
    expectedUserId,
    { method: 'POST', signal },
  )
}

export async function deleteSession(expectedUserId: string): Promise<void> {
  await requireExpectedSessionUser(expectedUserId)
  const csrf = await initializeSessionCsrf()
  try {
    await request('/api/auth/logout', {
      method: 'POST',
      headers: { [csrf.headerName]: csrf.token },
    })
    clearSessionCsrf()
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      clearSessionCsrf()
      await requireExpectedSessionUser(expectedUserId)
      await initializeSessionCsrf()
      throw new SessionSecurityChangedError()
    }
    throw error
  }
}
