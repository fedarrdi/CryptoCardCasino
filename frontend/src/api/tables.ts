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
  return apiRequest<TableSummary[]>('/api/tables', { signal })
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

export function joinTable(tableId: string): Promise<void> {
  return apiRequestWithoutResponse(
    `/api/tables/${encodeURIComponent(tableId)}/join`,
    {
      method: 'PUT',
    },
  )
}

export function leaveTable(tableId: string): Promise<void> {
  return apiRequestWithoutResponse(
    `/api/tables/${encodeURIComponent(tableId)}/leave`,
    {
      method: 'DELETE',
    },
  )
}
