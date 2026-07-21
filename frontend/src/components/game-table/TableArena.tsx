import { Diamond } from 'lucide-react'
import type { GameLobby } from '../../data/lobbies.ts'
import {
  PLAYER_HAND,
  type CenterCard,
  type SeatPosition,
  type TablePlayer,
  type TablePresentation,
} from '../../data/table.ts'
import PlayerSeat from './PlayerSeat.tsx'
import PlayingCard from './PlayingCard.tsx'

type TableArenaProps = {
  lobby: GameLobby
  players: readonly TablePlayer[]
  positions: readonly SeatPosition[]
  presentation: TablePresentation
}

function CenterPlayingCard({ item }: { item: CenterCard }) {
  return (
    <span className={`center-playing-card is-${item.placement}`}>
      {item.kind === 'back' ? (
        <PlayingCard variant="back" />
      ) : (
        <PlayingCard variant="face" card={item.card} />
      )}
    </span>
  )
}

function TableArena({ lobby, players, positions, presentation }: TableArenaProps) {
  return (
    <main className="game-table-stage">
      <div className="table-room-pattern" aria-hidden="true" />

      <div className="table-surface" aria-hidden="true">
        <div className="table-felt">
          <span className="table-felt-brand">
            <Diamond fill="currentColor" />
            RARETABLE
          </span>
          <span className="table-rail-plaque">
            {lobby.id} / {lobby.stake} USDC
          </span>
        </div>
      </div>

      {positions.map((position, index) => (
        <PlayerSeat
          key={position}
          player={players[index] ?? null}
          position={position}
        />
      ))}

      <div className="game-table-center">
        <span className="table-round-label">{presentation.round}</span>

        <div className="center-card-stack" aria-hidden="true">
          {presentation.centerCards.map((item) => (
            <CenterPlayingCard item={item} key={item.id} />
          ))}
          <span className="table-chip-stack">
            <i />
            <i />
            <i />
          </span>
        </div>

        <div className="table-state-panel">
          <span>{presentation.centerLabel}</span>
          <strong>{presentation.centerValue}</strong>
          <small>{presentation.centerMeta}</small>
        </div>
      </div>

      <div className="your-hand" aria-label="Your hand">
        <span className="your-hand-label">Your hand / {PLAYER_HAND.length} cards</span>
        <div className="your-hand-cards">
          {PLAYER_HAND.map((card, index) => (
            <button
              className={`your-hand-card hand-position-${index + 1}`}
              type="button"
              aria-label={`Select ${card.rank} of ${card.suit}`}
              key={`${card.rank}-${card.suit}`}
            >
              <PlayingCard variant="face" card={card} />
            </button>
          ))}
        </div>
      </div>

      <div className="self-player-seat">
        <div className="self-player-panel">
          <span className="player-seat-avatar avatar-self">YOU</span>
          <span className="player-seat-copy">
            <strong>You</strong>
            <small>Your turn</small>
          </span>
          <span className="turn-clock" aria-label="18 seconds remaining">
            18
          </span>
        </div>
      </div>

      <div className="turn-prompt">
        <span>Your turn</span>
        <strong>{presentation.turnPrompt}</strong>
      </div>

      <div className="game-table-actions">
        <button className="table-action-secondary" type="button">
          {presentation.secondaryAction}
        </button>
        <button className="table-action-primary" type="button">
          {presentation.primaryAction}
        </button>
      </div>
    </main>
  )
}

export default TableArena
