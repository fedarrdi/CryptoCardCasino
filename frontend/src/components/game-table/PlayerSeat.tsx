import type { SeatPosition, TablePlayerView } from './types.ts'
import PlayingCard from './PlayingCard.tsx'

type PlayerSeatProps = {
  player: TablePlayerView
  position: SeatPosition
  toneIndex: number
}

const AVATAR_TONES = ['rose', 'blue', 'gold', 'green', 'violet', 'teal', 'steel'] as const

function initials(name: string): string {
  return name
    .split(' ')
    .map((part) => part.charAt(0))
    .join('')
    .slice(0, 2)
    .toUpperCase()
}

function playerStatus(player: TablePlayerView): string {
  if (player.winner) {
    return 'Winner'
  }

  if (player.folded) {
    return 'Folded'
  }

  if (player.passed) {
    return 'Passed'
  }

  return player.currentTurn ? 'Playing' : 'Waiting'
}

function PlayerSeat({ player, position, toneIndex }: PlayerSeatProps) {
  const hiddenPreviewCount = player.visibleCards.length > 0
    ? Math.min(player.hiddenCardCount, 1)
    : Math.min(player.hiddenCardCount, 2)
  const status = playerStatus(player)
  const tone = AVATAR_TONES[toneIndex]

  return (
    <div
      className={`game-table-seat ${position} ${player.currentTurn ? 'is-current' : ''}`}
      aria-label={`${player.name}, ${player.cardCount} cards, ${status}`}
    >
      <div className="opponent-cards" aria-hidden="true">
        {player.visibleCards.map((card, index) => (
          <PlayingCard variant="face" card={card} size="small" key={index} />
        ))}
        {Array.from({ length: hiddenPreviewCount }, (_, index) => (
          <PlayingCard variant="back" size="small" key={`hidden-${index}`} />
        ))}
      </div>

      <div className="player-seat-panel">
        <span className={`player-seat-avatar avatar-${tone}`}>{initials(player.name)}</span>
        <span className="player-seat-copy">
          <strong>{player.name}</strong>
          <small>
            <span className={`seat-status-dot status-${status.toLowerCase()}`} aria-hidden="true" />
            {status}
          </small>
        </span>
        <span className="player-card-count" aria-label={`${player.cardCount} cards`}>
          {player.cardCount}
        </span>
      </div>
    </div>
  )
}

export default PlayerSeat
