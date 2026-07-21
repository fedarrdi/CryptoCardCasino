import { Coins, Hash, Users } from 'lucide-react'
import type { Game } from '../../data/games.ts'
import type { GameLobby } from '../../data/lobbies.ts'
import type { TablePresentation } from '../../data/table.ts'

type TableSidebarProps = {
  game: Game
  lobby: GameLobby
  occupiedSeats: number
  presentation: TablePresentation
}

function TableSidebar({ game, lobby, occupiedSeats, presentation }: TableSidebarProps) {
  return (
    <aside className="game-table-sidebar" aria-label="Table information">
      <header className="table-feed-heading">
        <span>Table feed</span>
        <strong>{presentation.round}</strong>
      </header>

      <dl className="table-summary">
        <div>
          <Coins aria-hidden="true" />
          <dt>Stake</dt>
          <dd>{lobby.stake} USDC</dd>
        </div>
        <div>
          <Users aria-hidden="true" />
          <dt>Players</dt>
          <dd>
            {occupiedSeats} / {lobby.capacity}
          </dd>
        </div>
        <div>
          <Hash aria-hidden="true" />
          <dt>Table</dt>
          <dd>{lobby.id}</dd>
        </div>
      </dl>

      <section className="table-feed" aria-labelledby="table-feed-title">
        <h2 id="table-feed-title">Recent activity</h2>
        <ol>
          {presentation.events.map((event, index) => (
            <li key={`${event.time}-${event.label}`}>
              <span className={`table-feed-marker ${index === 0 ? 'is-current' : ''}`} aria-hidden="true" />
              <span>{event.label}</span>
              <time>{event.time}</time>
            </li>
          ))}
        </ol>
      </section>

      <footer className="table-sidebar-footer">
        <span>Public table</span>
        <strong>{game.eyebrow}</strong>
      </footer>
    </aside>
  )
}

export default TableSidebar
