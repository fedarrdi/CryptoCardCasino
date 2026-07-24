import {
  apiRequest,
  apiRequestWithoutResponse,
  type CsrfCredentials,
} from './client.ts'

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

type CsrfTokenResponse = CsrfCredentials & {
  parameterName: string
}

export function createLoginChallenge(
  walletAddress: string,
  chainId: number,
): Promise<LoginChallenge> {
  return apiRequest<LoginChallenge>(
    '/api/auth/challenges',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ walletAddress, chainId }),
    },
    { skipCsrf: true },
  )
}

export function createAuthenticatedSession(
  nonce: string,
  signature: string,
): Promise<AuthenticatedUser> {
  return apiRequest<AuthenticatedUser>(
    '/api/auth/sessions',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ nonce, signature }),
    },
    { skipCsrf: true },
  )
}

export function getAuthenticatedUser(signal?: AbortSignal): Promise<AuthenticatedUser> {
  return apiRequest<AuthenticatedUser>('/api/auth/me', { signal })
}

export async function getCsrfCredentials(signal?: AbortSignal): Promise<CsrfCredentials> {
  const response = await apiRequest<CsrfTokenResponse>('/api/auth/csrf', { signal })
  return {
    headerName: response.headerName,
    token: response.token,
  }
}

export function deleteAuthenticatedSession(): Promise<void> {
  return apiRequestWithoutResponse('/api/auth/logout', { method: 'POST' })
}
