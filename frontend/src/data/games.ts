export type GameId = 'cheat' | 'tago' | 'durak'
export type GameAccent = 'red' | 'blue' | 'green'

export type Game = {
  id: GameId
  name: string
  eyebrow: string
  description: string
  image: string
  starts: string
  accent: GameAccent
}

export const games: Game[] = [
  {
    id: 'cheat',
    name: 'Cheat',
    eyebrow: '6-player bluffing',
    description: 'Make the claim. Read the table. Call the bluff.',
    image: '/images/cheat-table.jpg',
    starts: '18 min',
    accent: 'red',
  },
  {
    id: 'tago',
    name: 'TAGO',
    eyebrow: 'Rule of Pair',
    description: 'Build the pair. Count the point. Win the round.',
    image: '/images/tago-table.jpg',
    starts: '42 min',
    accent: 'blue',
  },
  {
    id: 'durak',
    name: 'Durak',
    eyebrow: 'Last card loses',
    description: 'Play your hand and avoid being the last player holding cards.',
    image: '/images/durak-table.jpg',
    starts: '1 hr',
    accent: 'green',
  },
]
