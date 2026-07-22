import type { BackendGameType } from '../data/games.ts'
import { apiRequest } from './client.ts'

export type CreateTableResponse = {
  tableId: string
}

export type JoinTableResponse = {
  userId: string
  name: string
}

export function createTable(
  gameType: BackendGameType,
  playersToStart: number,
): Promise<CreateTableResponse> {
  return apiRequest<CreateTableResponse>('/api/tables', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ gameType, playersToStart }),
  })
}

export function joinTable(tableId: string, userId: string): Promise<JoinTableResponse> {
  return apiRequest<JoinTableResponse>(`/api/tables/${encodeURIComponent(tableId)}/join`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ userId }),
  })
}
