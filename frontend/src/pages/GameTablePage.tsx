import { Link, useParams } from 'react-router-dom'
import TableArena from '../components/game-table/TableArena.tsx'
import TableHeader from '../components/game-table/TableHeader.tsx'
import TableSidebar from '../components/game-table/TableSidebar.tsx'
import '../components/game-table/game-table.css'
import { games } from '../data/games.ts'
import { lobbiesByGame } from '../data/lobbies.ts'
import {
  SEATS_BY_CAPACITY,
  TABLE_PLAYERS,
  TABLE_PRESENTATIONS,
} from '../data/table.ts'

type TableNotFoundProps = {
  title: string
  linkLabel: string
  to: string
}

function TableNotFound({ title, linkLabel, to }: TableNotFoundProps) {
  return (
    <main className="game-table-not-found">
      <h1>{title}</h1>
      <Link to={to}>{linkLabel}</Link>
    </main>
  )
}

function GameTablePage() {
  const { gameId, lobbyId } = useParams()
  const game = games.find((candidate) => candidate.id === gameId)

  if (!game) {
    return <TableNotFound title="Game not found" linkLabel="Return to games" to="/#games" />
  }

  const lobby = lobbiesByGame[game.id].find((candidate) => candidate.id === lobbyId)

  if (!lobby) {
    return (
      <TableNotFound
        title="Lobby not found"
        linkLabel={`Return to ${game.name} lobbies`}
        to={`/games/${game.id}`}
      />
    )
  }

  const positions = SEATS_BY_CAPACITY[lobby.capacity]
  const players = TABLE_PLAYERS.slice(0, Math.min(lobby.players, positions.length))
  const presentation = TABLE_PRESENTATIONS[game.id]

  return (
    <div className={`game-table-screen table-theme-${game.id}`}>
      <TableHeader game={game} lobby={lobby} round={presentation.round} />
      <div className="game-table-room">
        <TableArena
          lobby={lobby}
          players={players}
          positions={positions}
          presentation={presentation}
        />
        <TableSidebar
          game={game}
          lobby={lobby}
          occupiedSeats={players.length + 1}
          presentation={presentation}
        />
      </div>
    </div>
  )
}

export default GameTablePage
