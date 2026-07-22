import { useState } from 'react'
import type { TienLenCombinationType, TienLenGameState } from '../../../api/games.ts'
import { passTienLen, playTienLenCards } from '../../../api/games.ts'
import type { Game } from '../../../data/games.ts'
import GameTableShell from '../GameTableShell.tsx'
import {
  COMBINATION_LABELS,
  COMBINATION_TYPES,
  requirePlayerById,
  toVisualCard,
} from '../formatters.ts'
import type { PerformGameAction, TablePlayerView } from '../types.ts'

type TienLenTableViewProps = {
  game: Game
  tableId: string
  userId: string
  state: TienLenGameState
  actionPending: boolean
  actionError: string | null
  performAction: PerformGameAction
}

function TienLenTableView({
  game,
  tableId,
  userId,
  state,
  actionPending,
  actionError,
  performAction,
}: TienLenTableViewProps) {
  const [selectedCardIndexes, setSelectedCardIndexes] = useState<Set<number>>(new Set())
  const [combinationType, setCombinationType] = useState<TienLenCombinationType>('SINGLE')
  const isFinished = state.status === 'FINISHED'
  const isCurrentPlayer = state.currentPlayerId === userId
  const players: TablePlayerView[] = state.players.map((player) => ({
    id: player.playerId,
    name: player.name,
    cardCount: player.cardCount,
    currentTurn: player.currentTurn,
    folded: false,
    passed: player.passed,
    winner: player.playerId === state.winnerId,
    visibleCards: [],
    hiddenCardCount: player.cardCount,
  }))

  function toggleCard(index: number) {
    setSelectedCardIndexes((currentSelection) => {
      const nextSelection = new Set(currentSelection)

      if (nextSelection.has(index)) {
        nextSelection.delete(index)
      } else {
        nextSelection.add(index)
      }

      return nextSelection
    })
  }

  async function handlePlay() {
    const cardIndexes = [...selectedCardIndexes].sort((left, right) => left - right)
    const succeeded = await performAction(async () => ({
      gameId: 'tien-len',
      state: await playTienLenCards(tableId, userId, cardIndexes, combinationType),
    }))

    if (succeeded) {
      setSelectedCardIndexes(new Set())
    }
  }

  async function handlePass() {
    const succeeded = await performAction(async () => ({
      gameId: 'tien-len',
      state: await passTienLen(tableId, userId),
    }))

    if (succeeded) {
      setSelectedCardIndexes(new Set())
    }
  }

  function promptText(): string {
    if (isFinished) {
      return `${requirePlayerById(state.players, state.winnerId, 'Winner').name} emptied their hand`
    }

    if (isCurrentPlayer) {
      return 'Select a valid combination'
    }

    return `Waiting for ${requirePlayerById(state.players, state.currentPlayerId, 'Current player').name}`
  }

  const controls = isFinished ? (
    <span className="table-result">
      {requirePlayerById(state.players, state.winnerId, 'Winner').name} wins
    </span>
  ) : (
    <>
      <button
        className="table-action-secondary"
        type="button"
        disabled={!isCurrentPlayer || state.lastPlay === null || actionPending}
        onClick={handlePass}
      >
        Pass
      </button>
      <select
        className="table-action-select"
        aria-label="Combination type"
        value={combinationType}
        disabled={!isCurrentPlayer || actionPending}
        onChange={(event) => setCombinationType(event.target.value as TienLenCombinationType)}
      >
        {COMBINATION_TYPES.map((type) => (
          <option value={type} key={type}>{COMBINATION_LABELS[type]}</option>
        ))}
      </select>
      <button
        className="table-action-primary"
        type="button"
        disabled={!isCurrentPlayer || selectedCardIndexes.size === 0 || actionPending}
        onClick={handlePlay}
      >
        {actionPending ? 'Playing...' : 'Play cards'}
      </button>
    </>
  )

  return (
    <GameTableShell
      game={game}
      tableId={tableId}
      status={isFinished ? 'Finished' : 'In play'}
      players={players}
      viewerId={userId}
      hand={state.cards.map(toVisualCard)}
      center={{
        label: state.lastPlay ? 'Combination to beat' : 'Open trick',
        title: state.lastPlay ? COMBINATION_LABELS[state.lastPlay.type] : 'Lead any combination',
        meta: state.lastPlay
          ? `${state.lastPlay.cardCount} cards played by ${requirePlayerById(state.players, state.lastPlayerId, 'Last player').name}`
          : 'No cards on the table',
        cards: state.lastPlay ? state.lastPlay.cards.map(toVisualCard) : [],
        hiddenCardCount: 0,
      }}
      promptLabel={isCurrentPlayer ? 'Your turn' : isFinished ? 'Result' : 'Opponent turn'}
      prompt={promptText()}
      selectedCardIndexes={selectedCardIndexes}
      onCardSelect={isCurrentPlayer && !actionPending ? toggleCard : null}
      controls={controls}
      actionError={actionError}
    />
  )
}

export default TienLenTableView
