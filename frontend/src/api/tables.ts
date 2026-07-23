import type { BackendGameType } from '../data/games.ts'
import { apiRequest, apiRequestWithoutResponse } from './client.ts'

export type CreateTableResponse = {
  tableId: string
}

export type TableStatus = 'WAITING' | 'IN_GAME' | 'CLOSED'

export type TableSummary = {
  tableId: string
  gameType: BackendGameType
  status: TableStatus
  playersJoined: number
  playersToStart: number
}

export function getAllTables(signal?: AbortSignal): Promise<TableSummary[]> {
  return apiRequest<TableSummary[]>('/api/tables/get-all-tables', { signal })
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

export function joinTable(tableId: string, userId: string): Promise<void> {
  return apiRequestWithoutResponse(
    `/api/tables/${encodeURIComponent(tableId)}/users/${encodeURIComponent(userId)}`,
    {
      method: 'PUT',
    },
  )
}
