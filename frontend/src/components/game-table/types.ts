import type { ReactNode } from 'react'

export type CardSuit = 'clubs' | 'diamonds' | 'hearts' | 'spades'

export type VisualCard =
  | {
      kind: 'standard'
      rank: string
      suit: CardSuit
    }
  | {
      kind: 'tago'
      value: string
    }

export type SeatPosition =
  | 'seat-top-left'
  | 'seat-top'
  | 'seat-top-right'
  | 'seat-left-upper'
  | 'seat-right-upper'
  | 'seat-left-lower'
  | 'seat-right-lower'

export type TablePlayerView = {
  id: string
  name: string
  cardCount: number
  currentTurn: boolean
  folded: boolean
  passed: boolean
  winner: boolean
  visibleCards: VisualCard[]
  hiddenCardCount: number
}

export type TableCenterView = {
  label: string
  title: string
  meta: string
  cards: VisualCard[]
  hiddenCardCount: number
}

export type TableControls = ReactNode

export type PerformGameAction = (
  operation: () => Promise<import('../../api/games.ts').LoadedGameState>,
) => Promise<boolean>
