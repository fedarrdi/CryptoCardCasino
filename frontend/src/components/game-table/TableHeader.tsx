import { ArrowLeft, Diamond, LoaderCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Game } from '../../data/games.ts'

type TableHeaderProps = {
  game: Game
  tableId: string
  status: string
  leavePending: boolean
  onLeave: () => void
}

function TableHeader({
  game,
  tableId,
  status,
  leavePending,
  onLeave,
}: TableHeaderProps) {
  return (
    <header className="game-table-topbar">
      <div className="game-table-topbar-left">
        <button
          className="game-table-icon-button game-table-leave-button"
          type="button"
          disabled={leavePending}
          onClick={onLeave}
          aria-label={`Leave table and return to ${game.name}`}
          title="Leave table"
        >
          {leavePending ? <LoaderCircle className="is-spinning" /> : <ArrowLeft />}
        </button>

        <Link className="game-table-brand" to="/" aria-label="RareTable home">
          <span className="game-table-brand-mark" aria-hidden="true">
            <Diamond fill="currentColor" />
          </span>
          <span>RARETABLE</span>
        </Link>
      </div>

      <div className="game-table-room-title">
        <strong>{game.name}</strong>
        <span title={tableId}>Table {tableId.slice(0, 8)}</span>
      </div>

      <div className="game-table-tools">
        <span className="game-table-round">{status}</span>
      </div>
    </header>
  )
}

export default TableHeader
