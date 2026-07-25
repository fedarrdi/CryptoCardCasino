import { Diamond } from 'lucide-react'
import type { ReactNode } from 'react'
import PlayerSeat from './PlayerSeat.tsx'
import PlayingCard from './PlayingCard.tsx'
import type {
  SeatPosition,
  TableCenterView,
  TablePlayerView,
  VisualCard,
} from './types.ts'

const SEATS_BY_OPPONENT_COUNT: readonly (readonly SeatPosition[])[] = [
  [],
  ['seat-top'],
  ['seat-top-left', 'seat-top-right'],
  ['seat-top-left', 'seat-top', 'seat-top-right'],
  ['seat-top-left', 'seat-top-right', 'seat-left-upper', 'seat-right-upper'],
  ['seat-top-left', 'seat-top', 'seat-top-right', 'seat-left-upper', 'seat-right-upper'],
  [
    'seat-top-left',
    'seat-top',
    'seat-top-right',
    'seat-left-upper',
    'seat-right-upper',
    'seat-left-lower',
  ],
  [
    'seat-top-left',
    'seat-top',
    'seat-top-right',
    'seat-left-upper',
    'seat-right-upper',
    'seat-left-lower',
    'seat-right-lower',
  ],
]

type TableArenaProps = {
  tableId: string
  players: TablePlayerView[]
  viewerId: string
  hand: VisualCard[]
  hiddenHandCardCount: number
  center: TableCenterView
  promptLabel: string
  prompt: string
  selectedCardIndexes: ReadonlySet<number>
  onCardSelect: ((index: number) => void) | null
  controls: ReactNode
  actionError: string | null
}

function TableArena({
  tableId,
  players,
  viewerId,
  hand,
  hiddenHandCardCount,
  center,
  promptLabel,
  prompt,
  selectedCardIndexes,
  onCardSelect,
  controls,
  actionError,
}: TableArenaProps) {
  const viewer = players.find((player) => player.id === viewerId)

  if (!viewer) {
    throw new Error('The current user is missing from the game state')
  }

  const opponents = players.filter((player) => player.id !== viewerId)
  const positions = SEATS_BY_OPPONENT_COUNT[opponents.length]
  const centerBackCount = Math.min(center.hiddenCardCount, 3)

  return (
    <main className="game-table-stage">
      <div className="table-room-pattern" aria-hidden="true" />

      <div className="table-surface" aria-hidden="true">
        <div className="table-felt">
          <span className="table-felt-brand">
            <Diamond fill="currentColor" />
            RARETABLE
          </span>
          <span className="table-rail-plaque">TABLE {tableId.slice(0, 8)}</span>
        </div>
      </div>

      {opponents.map((player, index) => (
        <PlayerSeat
          key={player.id}
          player={player}
          position={positions[index]}
          toneIndex={index}
        />
      ))}

      <div className="game-table-center">
        <div className="center-card-stack" aria-hidden="true">
          {Array.from({ length: centerBackCount }, (_, index) => (
            <PlayingCard variant="back" key={`center-back-${index}`} />
          ))}
          {center.cards.map((card, index) => (
            <PlayingCard variant="face" card={card} key={`center-face-${index}`} />
          ))}
        </div>

        <div className="table-state-panel">
          <span>{center.label}</span>
          <strong>{center.title}</strong>
          <small>{center.meta}</small>
        </div>
      </div>

      <div className="your-hand" aria-label="Your hand">
        <span className="your-hand-label">Your hand / {viewer.cardCount} cards</span>
        <div className="your-hand-cards">
          {hand.map((card: VisualCard, index) => (
            <button
              className={`your-hand-card ${selectedCardIndexes.has(index) ? 'is-selected' : ''}`}
              type="button"
              aria-label={`Select card ${index + 1}`}
              aria-pressed={selectedCardIndexes.has(index)}
              onClick={onCardSelect ? () => onCardSelect(index) : undefined}
              disabled={onCardSelect === null}
              key={index}
            >
              <PlayingCard variant="face" card={card} />
            </button>
          ))}
          {Array.from({ length: hiddenHandCardCount }, (_, index) => (
            <span className="your-hand-card is-hidden" key={`hidden-hand-${index}`}>
              <PlayingCard variant="back" />
            </span>
          ))}
        </div>
      </div>

      <div className={`self-player-seat ${viewer.currentTurn ? 'is-current' : ''}`}>
        <div className="self-player-panel">
          <span className="player-seat-avatar avatar-self">YOU</span>
          <span className="player-seat-copy">
            <strong>{viewer.name}</strong>
            <small>{viewer.currentTurn ? 'Your turn' : 'At the table'}</small>
          </span>
          <span className="player-card-count">{viewer.cardCount}</span>
        </div>
      </div>

      <div className="turn-prompt">
        <span>{promptLabel}</span>
        <strong>{prompt}</strong>
      </div>

      <div className="game-table-actions">{controls}</div>

      {actionError && <div className="table-action-error" role="alert">{actionError}</div>}
    </main>
  )
}

export default TableArena
