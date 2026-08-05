export type ApiErrorBody = {
  code: string
  message: string
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = body.code
  }
}

export type CsrfCredentials = {
  headerName: string
  token: string
}

type RequestOptions = {
  skipCsrf?: boolean
}

let csrfCredentials: CsrfCredentials | null = null
let authenticationRequiredHandler: (() => void) | null = null

export function setCsrfCredentials(credentials: CsrfCredentials) {
  csrfCredentials = credentials
}

export function clearCsrfCredentials() {
  csrfCredentials = null
}

export function setAuthenticationRequiredHandler(handler: (() => void) | null) {
  authenticationRequiredHandler = handler
}

function isMutation(method: string): boolean {
  return method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS'
}

async function request(
  path: string,
  init?: RequestInit,
  options?: RequestOptions,
): Promise<Response> {
  const method = (init?.method ?? 'GET').toUpperCase()
  const headers = new Headers(init?.headers)

  if (isMutation(method) && options?.skipCsrf !== true) {
    if (csrfCredentials === null) {
      throw new Error('CSRF protection is not initialized for this session')
    }

    headers.set(csrfCredentials.headerName, csrfCredentials.token)
  }

  const response = await fetch(path, {
    ...init,
    method,
    headers,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const errorBody = (await response.json()) as ApiErrorBody

    if (response.status === 401) {
      clearCsrfCredentials()
      authenticationRequiredHandler?.()
    }

    throw new ApiRequestError(response.status, errorBody)
  }

  return response
}

export async function apiRequest<T>(
  path: string,
  init?: RequestInit,
  options?: RequestOptions,
): Promise<T> {
  const response = await request(path, init, options)
  return response.json() as Promise<T>
}

export async function apiRequestWithoutResponse(
  path: string,
  init?: RequestInit,
  options?: RequestOptions,
): Promise<void> {
  await request(path, init, options)
}
