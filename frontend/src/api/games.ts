import type { GameId } from '../data/games.ts'
import { apiRequest } from './client.ts'

export type Suit = 'CLUBS' | 'DIAMONDS' | 'HEARTS' | 'SPADES'
export type Rank =
  | 'TWO'
  | 'THREE'
  | 'FOUR'
  | 'FIVE'
  | 'SIX'
  | 'SEVEN'
  | 'EIGHT'
  | 'NINE'
  | 'TEN'
  | 'JACK'
  | 'QUEEN'
  | 'KING'
  | 'ACE'

export type StandardCard = {
  suit: Suit
  rank: Rank
}

export type CheatPlayerState = {
  playerId: string
  name: string
  cardCount: number
  currentTurn: boolean
}

export type CheatGameState = {
  cards: StandardCard[]
  players: CheatPlayerState[]
  currentPlayerId: string
  status: 'IN_PROGRESS' | 'FINISHED'
  pileSize: number
  lastPlayerId: string | null
  lastDeclaredRank: Rank | null
  winnerId: string | null
}

export type LoadedGameState = {
  gameId: 'cheat'
  state: CheatGameState
}

function gamePath(tableId: string): string {
  return `/api/tables/${encodeURIComponent(tableId)}/cheat`
}

export async function getGameState(
  gameId: GameId,
  tableId: string,
): Promise<LoadedGameState> {
  return {
    gameId,
    state: await apiRequest<CheatGameState>(`${gamePath(tableId)}/state`),
  }
}

export function playCheatCards(
  tableId: string,
  cardIndexes: number[],
  declaredRank: Rank,
): Promise<CheatGameState> {
  return apiRequest<CheatGameState>(`${gamePath(tableId)}/actions/play`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ cardIndexes, declaredRank }),
  })
}

export function callCheatBluff(tableId: string): Promise<CheatGameState> {
  return apiRequest<CheatGameState>(
    `${gamePath(tableId)}/actions/call-bluff`,
    { method: 'POST' },
  )
}
