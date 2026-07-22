export type GameId = 'cheat' | 'tago' | 'tien-len'
export type BackendGameType = 'CHEAT' | 'TAGO' | 'TIEN_LEN'
export type GameAccent = 'red' | 'blue' | 'green'

export type Game = {
  id: GameId
  backendType: BackendGameType
  name: string
  eyebrow: string
  description: string
  image: string
  accent: GameAccent
  minimumPlayers: number
  maximumPlayers: number
}

export const games: Game[] = [
  {
    id: 'cheat',
    backendType: 'CHEAT',
    name: 'Cheat',
    eyebrow: 'Bluffing and deduction',
    description: 'Make the claim. Read the table. Call the bluff.',
    image: '/images/cheat-table.jpg',
    accent: 'red',
    minimumPlayers: 2,
    maximumPlayers: 6,
  },
  {
    id: 'tago',
    backendType: 'TAGO',
    name: 'TAGO',
    eyebrow: 'Rule of Pair',
    description: 'Build the pair. Count the point. Win the round.',
    image: '/images/tago-table.jpg',
    accent: 'blue',
    minimumPlayers: 2,
    maximumPlayers: 8,
  },
  {
    id: 'tien-len',
    backendType: 'TIEN_LEN',
    name: 'Tien Len',
    eyebrow: 'Vietnamese shedding game',
    description: 'Build combinations, beat the table, and empty your hand first.',
    image: '/images/tien-len-table.jpg',
    accent: 'green',
    minimumPlayers: 2,
    maximumPlayers: 4,
  },
]

export function findGame(gameId: string | undefined): Game | undefined {
  return games.find((game) => game.id === gameId)
}
