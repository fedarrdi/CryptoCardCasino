import type {
  Rank,
  StandardCard,
  Suit,
} from '../../api/games.ts'
import type { CardSuit, VisualCard } from './types.ts'

export const RANKS: readonly Rank[] = [
  'TWO',
  'THREE',
  'FOUR',
  'FIVE',
  'SIX',
  'SEVEN',
  'EIGHT',
  'NINE',
  'TEN',
  'JACK',
  'QUEEN',
  'KING',
  'ACE',
]

const RANK_LABELS: Record<Rank, string> = {
  TWO: '2',
  THREE: '3',
  FOUR: '4',
  FIVE: '5',
  SIX: '6',
  SEVEN: '7',
  EIGHT: '8',
  NINE: '9',
  TEN: '10',
  JACK: 'J',
  QUEEN: 'Q',
  KING: 'K',
  ACE: 'A',
}

const RANK_NAMES: Record<Rank, string> = {
  TWO: 'Two',
  THREE: 'Three',
  FOUR: 'Four',
  FIVE: 'Five',
  SIX: 'Six',
  SEVEN: 'Seven',
  EIGHT: 'Eight',
  NINE: 'Nine',
  TEN: 'Ten',
  JACK: 'Jack',
  QUEEN: 'Queen',
  KING: 'King',
  ACE: 'Ace',
}

const SUIT_NAMES: Record<Suit, CardSuit> = {
  CLUBS: 'clubs',
  DIAMONDS: 'diamonds',
  HEARTS: 'hearts',
  SPADES: 'spades',
}

export function toVisualCard(card: StandardCard): VisualCard {
  return {
    kind: 'standard',
    rank: RANK_LABELS[card.rank],
    suit: SUIT_NAMES[card.suit],
  }
}

export function rankName(rank: Rank): string {
  return RANK_NAMES[rank]
}

export function requirePlayerById<T extends { playerId: string }>(
  players: T[],
  playerId: string | null,
  role: string,
): T {
  if (playerId === null) {
    throw new Error(`${role} ID is missing from the game state`)
  }

  const player = players.find((candidate) => candidate.playerId === playerId)

  if (!player) {
    throw new Error(`${role} is missing from the player list`)
  }

  return player
}
