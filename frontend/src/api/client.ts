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

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)

  if (!response.ok) {
    const errorBody = (await response.json()) as ApiErrorBody
    throw new ApiRequestError(response.status, errorBody)
  }

  return response.json() as Promise<T>
}

export function isGameWaitingError(error: unknown): boolean {
  return (
    error instanceof ApiRequestError &&
    error.status === 409 &&
    error.code === 'INVALID_GAME_STATE' &&
    error.message === 'Game has not started'
  )
}
