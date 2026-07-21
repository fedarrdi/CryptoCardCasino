import type { GameId } from './games.ts'

export type CardSuit = 'clubs' | 'diamonds' | 'hearts' | 'spades'

export type CardFace = {
  rank: string
  suit: CardSuit
}

export type SeatPosition =
  | 'seat-upper-left'
  | 'seat-top'
  | 'seat-upper-right'
  | 'seat-middle-left'
  | 'seat-middle-right'

export type AvatarTone = 'rose' | 'blue' | 'gold' | 'green' | 'violet'
export type PlayerStatus = 'Ready' | 'Waiting'

export type TablePlayer = {
  name: string
  initials: string
  cards: number
  status: PlayerStatus
  tone: AvatarTone
}

export type CenterCard =
  | {
      id: string
      kind: 'back'
      placement: 'left' | 'right'
    }
  | {
      id: string
      kind: 'face'
      card: CardFace
      placement: 'left' | 'right'
    }

export type TableEvent = {
  label: string
  time: string
}

export type TablePresentation = {
  round: string
  centerLabel: string
  centerValue: string
  centerMeta: string
  turnPrompt: string
  secondaryAction: string
  primaryAction: string
  centerCards: readonly CenterCard[]
  events: readonly TableEvent[]
}

export const PLAYER_HAND: readonly CardFace[] = [
  { rank: '6', suit: 'diamonds' },
  { rank: '8', suit: 'clubs' },
  { rank: 'Q', suit: 'hearts' },
  { rank: 'K', suit: 'spades' },
  { rank: 'A', suit: 'diamonds' },
]

export const TABLE_PLAYERS: readonly TablePlayer[] = [
  { name: 'Mira', initials: 'MI', cards: 5, status: 'Ready', tone: 'rose' },
  { name: 'Viktor', initials: 'VK', cards: 4, status: 'Ready', tone: 'blue' },
  { name: 'Nadia', initials: 'NA', cards: 6, status: 'Ready', tone: 'gold' },
  { name: 'Stefan', initials: 'ST', cards: 3, status: 'Ready', tone: 'green' },
  { name: 'Ivo', initials: 'IV', cards: 7, status: 'Waiting', tone: 'violet' },
]

export const SEATS_BY_CAPACITY: Record<4 | 6, readonly SeatPosition[]> = {
  4: ['seat-upper-left', 'seat-top', 'seat-upper-right'],
  6: [
    'seat-upper-left',
    'seat-top',
    'seat-upper-right',
    'seat-middle-left',
    'seat-middle-right',
  ],
}

export const TABLE_PRESENTATIONS: Record<GameId, TablePresentation> = {
  cheat: {
    round: 'Round 02',
    centerLabel: 'Current claim',
    centerValue: 'Three queens',
    centerMeta: '12 cards in the pile',
    turnPrompt: 'Choose cards for your claim',
    secondaryAction: 'Call bluff',
    primaryAction: 'Place claim',
    centerCards: [
      { id: 'cheat-back', kind: 'back', placement: 'left' },
      {
        id: 'cheat-queen',
        kind: 'face',
        card: { rank: 'Q', suit: 'hearts' },
        placement: 'right',
      },
    ],
    events: [
      { label: 'Mira placed three cards', time: 'Now' },
      { label: 'Viktor passed the turn', time: '8s' },
      { label: 'Round 02 started', time: '24s' },
      { label: 'You joined the table', time: '41s' },
    ],
  },
  tago: {
    round: 'Round 03',
    centerLabel: 'Rule of Pair',
    centerValue: 'Pair of eights',
    centerMeta: '4 round points',
    turnPrompt: 'Choose a pair to score',
    secondaryAction: 'Pass',
    primaryAction: 'Play pair',
    centerCards: [
      {
        id: 'tago-eight-clubs',
        kind: 'face',
        card: { rank: '8', suit: 'clubs' },
        placement: 'left',
      },
      {
        id: 'tago-eight-diamonds',
        kind: 'face',
        card: { rank: '8', suit: 'diamonds' },
        placement: 'right',
      },
    ],
    events: [
      { label: 'Nadia completed a pair', time: 'Now' },
      { label: 'Four points awarded', time: '6s' },
      { label: 'Round 03 started', time: '31s' },
      { label: 'You joined the table', time: '48s' },
    ],
  },
  durak: {
    round: 'Bout 01',
    centerLabel: 'Trump suit',
    centerValue: 'Hearts',
    centerMeta: '18 cards in the deck',
    turnPrompt: 'Beat the attacking card',
    secondaryAction: 'Take cards',
    primaryAction: 'Defend',
    centerCards: [
      { id: 'durak-deck', kind: 'back', placement: 'left' },
      {
        id: 'durak-trump',
        kind: 'face',
        card: { rank: '10', suit: 'hearts' },
        placement: 'right',
      },
    ],
    events: [
      { label: 'Stefan attacked with a queen', time: 'Now' },
      { label: 'Trump card revealed', time: '7s' },
      { label: 'Bout 01 started', time: '29s' },
      { label: 'You joined the table', time: '46s' },
    ],
  },
}
