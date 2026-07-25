import type {
  Rank,
  StandardCard,
  Suit,
  TagoCard,
  TagoCardValue,
  TienLenCombinationType,
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

export const COMBINATION_TYPES: readonly TienLenCombinationType[] = [
  'SINGLE',
  'PAIR',
  'TRIPLE',
  'STRAIGHT',
  'FLUSH',
  'FULL_HOUSE',
  'FOUR_OF_A_KIND',
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

const TAGO_VALUE_LABELS: Record<TagoCardValue, string> = {
  ZERO: '0',
  HALF: '1/2',
  ONE: '1',
  TWO: '2',
  THREE: '3',
  FOUR: '4',
  FIVE: '5',
  SIX: '6',
  SEVEN: '7',
  EIGHT: '8',
}

export const COMBINATION_LABELS: Record<TienLenCombinationType, string> = {
  SINGLE: 'Single',
  PAIR: 'Pair',
  TRIPLE: 'Triple',
  STRAIGHT: 'Straight',
  FLUSH: 'Flush',
  FULL_HOUSE: 'Full house',
  FOUR_OF_A_KIND: 'Four of a kind',
}

export function toVisualCard(card: StandardCard): VisualCard {
  return {
    kind: 'standard',
    rank: RANK_LABELS[card.rank],
    suit: SUIT_NAMES[card.suit],
  }
}

export function toTagoVisualCard(card: TagoCard): VisualCard {
  return {
    kind: 'tago',
    value: TAGO_VALUE_LABELS[card.value],
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
