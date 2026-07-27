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

export type BtcPrice = {
  symbol: string
  price: number
}

export type MarketCandle = {
  time: number
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export type BtcCandleHistory = {
  symbol: 'BTCUSDT'
  interval: '1h'
  candles: MarketCandle[]
}

export type BtcCandleUpdate = MarketCandle & {
  symbol: 'BTCUSDT'
  interval: '1h'
  closed: boolean
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

async function request(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw new ApiError(response.status, (await response.json()) as ApiErrorBody)
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

export function getBtcPrice(): Promise<BtcPrice> {
  return requestJson<BtcPrice>('/api/paper-trading/btc-price')
}

export function getBtcCandles(signal?: AbortSignal): Promise<BtcCandleHistory> {
  return requestJson<BtcCandleHistory>('/api/paper-trading/btc-candles', {
    signal,
  })
}

export async function deleteSession(): Promise<void> {
  const csrf = await requestJson<CsrfToken>('/api/auth/csrf')
  await request('/api/auth/logout', {
    method: 'POST',
    headers: { [csrf.headerName]: csrf.token },
  })
}
