import { useState } from 'react'
import type { CheatGameState, Rank } from '../../../api/games.ts'
import { callCheatBluff, playCheatCards } from '../../../api/games.ts'
import type { Game } from '../../../data/games.ts'
import GameTableShell from '../GameTableShell.tsx'
import { RANKS, rankName, requirePlayerById, toVisualCard } from '../formatters.ts'
import type { PerformGameAction, TablePlayerView } from '../types.ts'

type CheatTableViewProps = {
  game: Game
  tableId: string
  userId: string
  state: CheatGameState
  actionPending: boolean
  actionError: string | null
  performAction: PerformGameAction
  onLeave: () => void
}

function CheatTableView({
  game,
  tableId,
  userId,
  state,
  actionPending,
  actionError,
  performAction,
  onLeave,
}: CheatTableViewProps) {
  const [selectedCardIndexes, setSelectedCardIndexes] = useState<Set<number>>(new Set())
  const [declaredRank, setDeclaredRank] = useState<Rank>('ACE')
  const isFinished = state.status === 'FINISHED'
  const isCurrentPlayer = !isFinished && state.currentPlayerId === userId
  const players: TablePlayerView[] = state.players.map((player) => ({
    id: player.playerId,
    name: player.name,
    cardCount: player.cardCount,
    currentTurn: !isFinished && player.currentTurn,
    folded: false,
    passed: false,
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
      gameId: 'cheat',
      state: await playCheatCards(tableId, userId, cardIndexes, declaredRank),
    }))

    if (succeeded) {
      setSelectedCardIndexes(new Set())
    }
  }

  async function handleCallBluff() {
    const succeeded = await performAction(async () => ({
      gameId: 'cheat',
      state: await callCheatBluff(tableId, userId),
    }))

    if (succeeded) {
      setSelectedCardIndexes(new Set())
    }
  }

  function promptText(): string {
    if (isFinished) {
      return `${requirePlayerById(state.players, state.winnerId, 'Winner').name} won the game`
    }

    if (isCurrentPlayer) {
      return 'Select cards and declare a rank'
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
        disabled={!isCurrentPlayer || state.lastPlayerId === null || actionPending}
        onClick={handleCallBluff}
      >
        Call bluff
      </button>
      <select
        className="table-action-select"
        aria-label="Declared rank"
        value={declaredRank}
        disabled={!isCurrentPlayer || actionPending}
        onChange={(event) => setDeclaredRank(event.target.value as Rank)}
      >
        {RANKS.map((rank) => (
          <option value={rank} key={rank}>{rankName(rank)}</option>
        ))}
      </select>
      <button
        className="table-action-primary"
        type="button"
        disabled={!isCurrentPlayer || selectedCardIndexes.size === 0 || actionPending}
        onClick={handlePlay}
      >
        {actionPending ? 'Playing...' : 'Place cards'}
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
        label: 'Current claim',
        title: state.lastDeclaredRank ? `${rankName(state.lastDeclaredRank)} declared` : 'No claim yet',
        meta: `${state.pileSize} ${state.pileSize === 1 ? 'card' : 'cards'} in the pile`,
        cards: [],
        hiddenCardCount: state.pileSize,
      }}
      promptLabel={isCurrentPlayer ? 'Your turn' : isFinished ? 'Result' : 'Opponent turn'}
      prompt={promptText()}
      selectedCardIndexes={selectedCardIndexes}
      onCardSelect={isCurrentPlayer && !actionPending ? toggleCard : null}
      controls={controls}
      actionError={actionError}
      leavePending={actionPending}
      onLeave={onLeave}
    />
  )
}

export default CheatTableView
