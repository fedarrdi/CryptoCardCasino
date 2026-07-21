import {
  ArrowLeft,
  Diamond,
  Maximize2,
  Settings,
  Volume2,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Game } from '../../data/games.ts'
import type { GameLobby } from '../../data/lobbies.ts'

type TableHeaderProps = {
  game: Game
  lobby: GameLobby
  round: string
}

function TableHeader({ game, lobby, round }: TableHeaderProps) {
  return (
    <header className="game-table-topbar">
      <div className="game-table-topbar-left">
        <Link
          className="game-table-icon-button"
          to={`/games/${game.id}`}
          aria-label={`Leave table and return to ${game.name} lobbies`}
          title="Leave table"
        >
          <ArrowLeft />
        </Link>

        <Link className="game-table-brand" to="/" aria-label="RareTable home">
          <span className="game-table-brand-mark" aria-hidden="true">
            <Diamond fill="currentColor" />
          </span>
          <span>RARETABLE</span>
        </Link>
      </div>

      <div className="game-table-room-title">
        <strong>{game.name}</strong>
        <span>
          Table {lobby.id} / {lobby.stake} USDC
        </span>
      </div>

      <div className="game-table-tools">
        <span className="game-table-round">{round}</span>
        <button className="game-table-icon-button" type="button" aria-label="Sound" title="Sound">
          <Volume2 />
        </button>
        <button className="game-table-icon-button" type="button" aria-label="Settings" title="Settings">
          <Settings />
        </button>
        <button
          className="game-table-icon-button"
          type="button"
          aria-label="Fullscreen"
          title="Fullscreen"
        >
          <Maximize2 />
        </button>
      </div>
    </header>
  )
}

export default TableHeader
