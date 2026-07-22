import { CircleDot, Hash, Users } from 'lucide-react'
import type { Game } from '../../data/games.ts'
import type { TablePlayerView } from './types.ts'

type TableSidebarProps = {
  game: Game
  tableId: string
  status: string
  players: TablePlayerView[]
}

function TableSidebar({ game, tableId, status, players }: TableSidebarProps) {
  const currentPlayer = players.find((player) => player.currentTurn)

  return (
    <aside className="game-table-sidebar" aria-label="Table information">
      <header className="table-feed-heading">
        <span>Table state</span>
        <strong>{status}</strong>
      </header>

      <dl className="table-summary">
        <div>
          <CircleDot aria-hidden="true" />
          <dt>Turn</dt>
          <dd>{currentPlayer?.name ?? 'Complete'}</dd>
        </div>
        <div>
          <Users aria-hidden="true" />
          <dt>Players</dt>
          <dd>{players.length}</dd>
        </div>
        <div>
          <Hash aria-hidden="true" />
          <dt>Table</dt>
          <dd title={tableId}>{tableId.slice(0, 8)}</dd>
        </div>
      </dl>

      <section className="table-feed player-roster" aria-labelledby="player-roster-title">
        <h2 id="player-roster-title">Players</h2>
        <ol>
          {players.map((player) => (
            <li key={player.id}>
              <span
                className={`table-feed-marker ${player.currentTurn ? 'is-current' : ''}`}
                aria-hidden="true"
              />
              <span>{player.name}</span>
              <time>{player.cardCount} cards</time>
            </li>
          ))}
        </ol>
      </section>

      <footer className="table-sidebar-footer">
        <span>Game</span>
        <strong>{game.eyebrow}</strong>
      </footer>
    </aside>
  )
}

export default TableSidebar
