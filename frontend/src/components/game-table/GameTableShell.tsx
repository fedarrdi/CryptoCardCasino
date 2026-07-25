import type { Game } from '../../data/games.ts'
import TableArena from './TableArena.tsx'
import TableHeader from './TableHeader.tsx'
import TableSidebar from './TableSidebar.tsx'
import type { TableCenterView, TablePlayerView, VisualCard } from './types.ts'
import type { ReactNode } from 'react'

type GameTableShellProps = {
  game: Game
  tableId: string
  status: string
  players: TablePlayerView[]
  viewerId: string
  hand: VisualCard[]
  hiddenHandCardCount?: number
  center: TableCenterView
  promptLabel: string
  prompt: string
  selectedCardIndexes: ReadonlySet<number>
  onCardSelect: ((index: number) => void) | null
  controls: ReactNode
  actionError: string | null
  leavePending: boolean
  onLeave: () => void
}

function GameTableShell({
  game,
  tableId,
  status,
  players,
  viewerId,
  hand,
  hiddenHandCardCount = 0,
  center,
  promptLabel,
  prompt,
  selectedCardIndexes,
  onCardSelect,
  controls,
  actionError,
  leavePending,
  onLeave,
}: GameTableShellProps) {
  return (
    <div className={`game-table-screen table-theme-${game.id}`}>
      <TableHeader
        game={game}
        tableId={tableId}
        status={status}
        leavePending={leavePending}
        onLeave={onLeave}
      />
      <div className="game-table-room">
        <TableArena
          tableId={tableId}
          players={players}
          viewerId={viewerId}
          hand={hand}
          hiddenHandCardCount={hiddenHandCardCount}
          center={center}
          promptLabel={promptLabel}
          prompt={prompt}
          selectedCardIndexes={selectedCardIndexes}
          onCardSelect={onCardSelect}
          controls={controls}
          actionError={actionError}
        />
        <TableSidebar game={game} tableId={tableId} status={status} players={players} />
      </div>
    </div>
  )
}

export default GameTableShell
