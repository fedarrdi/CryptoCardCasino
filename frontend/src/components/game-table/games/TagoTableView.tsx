import type { TagoGameState, TagoGameStatus } from '../../../api/games.ts'
import {
  chooseTagoPointValue,
  completeTagoBettingTurn,
  foldTago,
} from '../../../api/games.ts'
import type { Game } from '../../../data/games.ts'
import GameTableShell from '../GameTableShell.tsx'
import { requirePlayerById, toTagoVisualCard } from '../formatters.ts'
import type { PerformGameAction, TablePlayerView } from '../types.ts'

type TagoTableViewProps = {
  game: Game
  tableId: string
  userId: string
  state: TagoGameState
  actionPending: boolean
  actionError: string | null
  performAction: PerformGameAction
  onLeave: () => void
}

const STATUS_LABELS: Record<TagoGameStatus, string> = {
  NOT_STARTED: 'Not started',
  FIRST_BETTING_ROUND: 'First betting round',
  POINT_VALUE_SELECTION: 'Choose Point value',
  SECOND_BETTING_ROUND: 'Second betting round',
  THIRD_BETTING_ROUND: 'Final betting round',
  FINISHED: 'Finished',
}

function TagoTableView({
  game,
  tableId,
  userId,
  state,
  actionPending,
  actionError,
  performAction,
  onLeave,
}: TagoTableViewProps) {
  const isFinished = state.status === 'FINISHED'
  const isCurrentPlayer = state.currentPlayerId === userId
  const viewer = requirePlayerById(state.players, userId, 'Current user')

  const winners = state.players
    .filter((player) => state.winnerIds.includes(player.playerId))
    .map((player) => player.name)

  if (isFinished && winners.length === 0) {
    throw new Error('Finished TAGO state does not contain a winner')
  }
  const players: TablePlayerView[] = state.players.map((player) => ({
    id: player.playerId,
    name: player.name,
    cardCount: player.visibleCards.length + 1,
    currentTurn: player.currentTurn,
    folded: player.folded,
    passed: false,
    winner: state.winnerIds.includes(player.playerId),
    visibleCards: [
      ...player.visibleCards.map(toTagoVisualCard),
      ...(player.hiddenCard ? [toTagoVisualCard(player.hiddenCard)] : []),
    ],
    hiddenCardCount: player.hiddenCard ? 0 : 1,
  }))

  async function runBettingTurn() {
    await performAction(async () => ({
      gameId: 'tago',
      state: await completeTagoBettingTurn(tableId),
    }))
  }

  async function runFold() {
    await performAction(async () => ({
      gameId: 'tago',
      state: await foldTago(tableId),
    }))
  }

  async function selectPointValue(value: number) {
    await performAction(async () => ({
      gameId: 'tago',
      state: await chooseTagoPointValue(tableId, value),
    }))
  }

  function promptText(): string {
    if (isFinished) {
      return `${winners.join(', ')} won the round`
    }

    if (isCurrentPlayer) {
      return state.status === 'POINT_VALUE_SELECTION'
        ? 'Choose the Point value'
        : 'Complete your betting turn or fold'
    }

    return `Waiting for ${requirePlayerById(state.players, state.currentPlayerId, 'Current player').name}`
  }

  const bettingRound =
    state.status === 'FIRST_BETTING_ROUND' ||
    state.status === 'SECOND_BETTING_ROUND' ||
    state.status === 'THIRD_BETTING_ROUND'

  let controls = null

  if (isFinished) {
    controls = (
      <span className="table-result">
        {winners.join(', ')} {winners.length === 1 ? 'wins' : 'win'}
      </span>
    )
  } else if (isCurrentPlayer && state.status === 'POINT_VALUE_SELECTION') {
    controls = (
      <>
        <button
          className="table-action-secondary"
          type="button"
          disabled={actionPending}
          onClick={runFold}
        >
          Fold
        </button>
        <div className="point-value-controls" aria-label="Choose Point value">
          {state.pointValueOptions.map((value) => (
            <button
              className="table-action-primary"
              type="button"
              disabled={actionPending}
              onClick={() => selectPointValue(value)}
              key={value}
            >
              {value}
            </button>
          ))}
        </div>
      </>
    )
  } else if (isCurrentPlayer && bettingRound) {
    controls = (
      <>
        <button
          className="table-action-secondary"
          type="button"
          disabled={actionPending}
          onClick={runFold}
        >
          Fold
        </button>
        <button
          className="table-action-primary"
          type="button"
          disabled={actionPending}
          onClick={runBettingTurn}
        >
          {actionPending ? 'Submitting...' : 'Complete turn'}
        </button>
      </>
    )
  }

  const hand = [
    ...viewer.visibleCards.map(toTagoVisualCard),
    ...(viewer.hiddenCard ? [toTagoVisualCard(viewer.hiddenCard)] : []),
  ]

  return (
    <GameTableShell
      game={game}
      tableId={tableId}
      status={STATUS_LABELS[state.status]}
      players={players}
      viewerId={userId}
      hand={hand}
      hiddenHandCardCount={viewer.hiddenCard ? 0 : 1}
      center={{
        label: 'The Point',
        title: state.pointValue === null ? 'Value unresolved' : `Value ${state.pointValue}`,
        meta: `${state.remainingDeckSize} cards remain in the deck`,
        cards: state.pointCards.map(toTagoVisualCard),
        hiddenCardCount: 3 - state.pointCards.length,
      }}
      promptLabel={isCurrentPlayer ? 'Your turn' : isFinished ? 'Result' : 'Opponent turn'}
      prompt={promptText()}
      selectedCardIndexes={new Set<number>()}
      onCardSelect={null}
      controls={controls}
      actionError={actionError}
      leavePending={actionPending}
      onLeave={onLeave}
    />
  )
}

export default TagoTableView
