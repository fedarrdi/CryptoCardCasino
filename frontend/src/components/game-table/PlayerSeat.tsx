import { UserRoundPlus } from 'lucide-react'
import type { SeatPosition, TablePlayer } from '../../data/table.ts'
import PlayingCard from './PlayingCard.tsx'

type PlayerSeatProps = {
  player: TablePlayer | null
  position: SeatPosition
}

function PlayerSeat({ player, position }: PlayerSeatProps) {
  if (player === null) {
    return (
      <div className={`game-table-seat ${position} is-open`} aria-label="Open player seat">
        <div className="open-seat-marker">
          <UserRoundPlus aria-hidden="true" />
          <span>Open seat</span>
        </div>
      </div>
    )
  }

  return (
    <div className={`game-table-seat ${position}`} aria-label={`${player.name}, ${player.cards} cards`}>
      <div className="opponent-cards" aria-hidden="true">
        <PlayingCard variant="back" size="small" />
        <PlayingCard variant="back" size="small" />
      </div>

      <div className="player-seat-panel">
        <span className={`player-seat-avatar avatar-${player.tone}`}>{player.initials}</span>
        <span className="player-seat-copy">
          <strong>{player.name}</strong>
          <small>
            <span className={`seat-status-dot status-${player.status.toLowerCase()}`} aria-hidden="true" />
            {player.status}
          </small>
        </span>
        <span className="player-card-count" aria-label={`${player.cards} cards`}>
          {player.cards}
        </span>
      </div>
    </div>
  )
}

export default PlayerSeat
