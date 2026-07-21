import type { GameId } from './games.ts'

export type LobbyAvailability = 'Open' | 'Almost full' | 'Full'

export type GameLobby = {
  id: string
  stake: number
  players: number
  capacity: 4 | 6
  starts: string
  availability: LobbyAvailability
}

export const lobbiesByGame: Record<GameId, GameLobby[]> = {
  cheat: [
    { id: 'C-104', stake: 5, players: 3, capacity: 6, starts: 'When full', availability: 'Open' },
    { id: 'C-208', stake: 25, players: 5, capacity: 6, starts: '1 seat left', availability: 'Almost full' },
    { id: 'C-316', stake: 50, players: 2, capacity: 6, starts: 'When full', availability: 'Open' },
    { id: 'C-402', stake: 100, players: 6, capacity: 6, starts: 'In progress', availability: 'Full' },
  ],
  tago: [
    { id: 'T-103', stake: 5, players: 2, capacity: 4, starts: 'When full', availability: 'Open' },
    { id: 'T-214', stake: 25, players: 3, capacity: 4, starts: '1 seat left', availability: 'Almost full' },
    { id: 'T-327', stake: 50, players: 1, capacity: 4, starts: 'When full', availability: 'Open' },
    { id: 'T-418', stake: 100, players: 4, capacity: 4, starts: 'In progress', availability: 'Full' },
  ],
  durak: [
    { id: 'D-106', stake: 5, players: 4, capacity: 6, starts: 'When full', availability: 'Open' },
    { id: 'D-219', stake: 25, players: 5, capacity: 6, starts: '1 seat left', availability: 'Almost full' },
    { id: 'D-324', stake: 50, players: 3, capacity: 6, starts: 'When full', availability: 'Open' },
    { id: 'D-411', stake: 100, players: 6, capacity: 6, starts: 'In progress', availability: 'Full' },
  ],
}
