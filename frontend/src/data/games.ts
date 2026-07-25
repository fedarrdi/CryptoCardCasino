export type GameId = 'cheat'
export type BackendGameType = 'CHEAT'
export type GameAccent = 'red'

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
]

export function findGame(gameId: string | undefined): Game | undefined {
  return games.find((game) => game.id === gameId)
}
