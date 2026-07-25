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

export type TagoCardValue =
  | 'ZERO'
  | 'HALF'
  | 'ONE'
  | 'TWO'
  | 'THREE'
  | 'FOUR'
  | 'FIVE'
  | 'SIX'
  | 'SEVEN'
  | 'EIGHT'

export type TagoCard = {
  value: TagoCardValue
}

export type TagoHandScore = {
  type: 'CLOSEST' | 'VALUE' | 'THREE_OF_A_KIND' | 'COPY_CAT' | 'ZERO'
  bestValues: number[]
  distanceFromPoint: number
}

export type TagoPlayerState = {
  playerId: string
  name: string
  visibleCards: TagoCard[]
  hiddenCard: TagoCard | null
  folded: boolean
  currentTurn: boolean
  score: TagoHandScore | null
}

export type TagoGameStatus =
  | 'NOT_STARTED'
  | 'FIRST_BETTING_ROUND'
  | 'POINT_VALUE_SELECTION'
  | 'SECOND_BETTING_ROUND'
  | 'THIRD_BETTING_ROUND'
  | 'FINISHED'

export type TagoGameState = {
  status: TagoGameStatus
  currentPlayerId: string | null
  firstPlayerId: string
  pointCards: TagoCard[]
  pointValue: number | null
  pointValueOptions: number[]
  players: TagoPlayerState[]
  winnerIds: string[]
  remainingDeckSize: number
}

export type TienLenCombinationType =
  | 'SINGLE'
  | 'PAIR'
  | 'TRIPLE'
  | 'STRAIGHT'
  | 'FLUSH'
  | 'FULL_HOUSE'
  | 'FOUR_OF_A_KIND'

export type TienLenCombination = {
  type: TienLenCombinationType
  cards: StandardCard[]
  highestCard: StandardCard
  rankValue: number
  cardCount: number
}

export type TienLenPlayerState = {
  playerId: string
  name: string
  cardCount: number
  currentTurn: boolean
  passed: boolean
}

export type TienLenGameState = {
  cards: StandardCard[]
  players: TienLenPlayerState[]
  currentPlayerId: string | null
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'FINISHED'
  lastPlay: TienLenCombination | null
  lastPlayerId: string | null
  winnerId: string | null
}

export type LoadedGameState =
  | { gameId: 'cheat'; state: CheatGameState }
  | { gameId: 'tago'; state: TagoGameState }
  | { gameId: 'tien-len'; state: TienLenGameState }

function gamePath(tableId: string, endpoint: string): string {
  return `/api/tables/${encodeURIComponent(tableId)}/${endpoint}`
}

export async function getGameState(
  gameId: GameId,
  tableId: string,
): Promise<LoadedGameState> {
  switch (gameId) {
    case 'cheat':
      return {
        gameId,
        state: await apiRequest<CheatGameState>(`${gamePath(tableId, 'cheat')}/state`),
      }
    case 'tago':
      return {
        gameId,
        state: await apiRequest<TagoGameState>(`${gamePath(tableId, 'tago')}/state`),
      }
    case 'tien-len':
      return {
        gameId,
        state: await apiRequest<TienLenGameState>(
          `${gamePath(tableId, 'tien-len')}/state`,
        ),
      }
  }
}

export function playCheatCards(
  tableId: string,
  cardIndexes: number[],
  declaredRank: Rank,
): Promise<CheatGameState> {
  return apiRequest<CheatGameState>(`${gamePath(tableId, 'cheat')}/actions/play`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ cardIndexes, declaredRank }),
  })
}

export function callCheatBluff(tableId: string): Promise<CheatGameState> {
  return apiRequest<CheatGameState>(
    `${gamePath(tableId, 'cheat')}/actions/call-bluff`,
    { method: 'POST' },
  )
}

export function completeTagoBettingTurn(tableId: string): Promise<TagoGameState> {
  return apiRequest<TagoGameState>(
    `${gamePath(tableId, 'tago')}/actions/complete-betting-turn`,
    { method: 'POST' },
  )
}

export function foldTago(tableId: string): Promise<TagoGameState> {
  return apiRequest<TagoGameState>(`${gamePath(tableId, 'tago')}/actions/fold`, {
    method: 'POST',
  })
}

export function chooseTagoPointValue(
  tableId: string,
  value: number,
): Promise<TagoGameState> {
  return apiRequest<TagoGameState>(
    `${gamePath(tableId, 'tago')}/actions/point-value`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ value }),
    },
  )
}

export function playTienLenCards(
  tableId: string,
  cardIndexes: number[],
  combinationType: TienLenCombinationType,
): Promise<TienLenGameState> {
  return apiRequest<TienLenGameState>(
    `${gamePath(tableId, 'tien-len')}/actions/play`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ cardIndexes, combinationType }),
    },
  )
}

export function passTienLen(tableId: string): Promise<TienLenGameState> {
  return apiRequest<TienLenGameState>(
    `${gamePath(tableId, 'tien-len')}/actions/pass`,
    { method: 'POST' },
  )
}
