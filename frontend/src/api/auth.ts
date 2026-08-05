export type AuthenticatedUser = {
  walletAddress: string
}

export type LoginChallenge = {
  nonce: string
  message: string
}

type ApiErrorBody = {
  message: string
}

type CsrfCredentials = {
  headerName: string
  token: string
}

export class ApiRequestError extends Error {
  readonly status: number

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

let csrfCredentials: CsrfCredentials | null = null

export function clearCsrfCredentials() {
  csrfCredentials = null
}

async function request(
  path: string,
  init?: RequestInit,
  requiresCsrf = false,
): Promise<Response> {
  const headers = new Headers(init?.headers)

  if (requiresCsrf) {
    if (csrfCredentials === null) {
      throw new Error('CSRF protection is not initialized for this session')
    }

    headers.set(csrfCredentials.headerName, csrfCredentials.token)
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const errorBody = (await response.json()) as ApiErrorBody

    if (response.status === 401) {
      clearCsrfCredentials()
    }

    throw new ApiRequestError(response.status, errorBody)
  }

  return response
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await request(path, init)
  return response.json() as Promise<T>
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

export function createAuthenticatedSession(
  nonce: string,
  signature: string,
): Promise<AuthenticatedUser> {
  return requestJson<AuthenticatedUser>('/api/auth/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nonce, signature }),
  })
}

export function getAuthenticatedUser(signal?: AbortSignal): Promise<AuthenticatedUser> {
  return requestJson<AuthenticatedUser>('/api/auth/me', { signal })
}

export async function initializeCsrf(signal?: AbortSignal): Promise<void> {
  const response = await requestJson<CsrfCredentials>('/api/auth/csrf', { signal })
  csrfCredentials = {
    headerName: response.headerName,
    token: response.token,
  }
}

export async function deleteAuthenticatedSession(): Promise<void> {
  await request('/api/auth/logout', { method: 'POST' }, true)
  clearCsrfCredentials()
}
