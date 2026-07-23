import { useEffect, useState } from 'react'
import { ArrowLeft, Copy, LoaderCircle } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  getGameState,
  type LoadedGameState,
} from '../api/games.ts'
import { isGameWaitingError, isTableNotFoundError } from '../api/client.ts'
import { leaveTable } from '../api/tables.ts'
import { useAuth } from '../auth/AuthContext.ts'
import CheatTableView from '../components/game-table/games/CheatTableView.tsx'
import TagoTableView from '../components/game-table/games/TagoTableView.tsx'
import TienLenTableView from '../components/game-table/games/TienLenTableView.tsx'
import '../components/game-table/game-table.css'
import { findGame } from '../data/games.ts'

type PagePhase = 'loading' | 'waiting' | 'ready' | 'error'

type TableMessageProps = {
  title: string
  message: string
  linkLabel: string
  to: string
}

function TableMessage({ title, message, linkLabel, to }: TableMessageProps) {
  return (
    <main className="game-table-message">
      <span className="game-table-message-mark" aria-hidden="true" />
      <h1>{title}</h1>
      <p>{message}</p>
      <Link to={to}>
        <ArrowLeft size={17} />
        {linkLabel}
      </Link>
    </main>
  )
}

function WaitingTable({ gameName, tableId, isLeaving, leaveError, onLeave }: {
  gameName: string
  tableId: string
  isLeaving: boolean
  leaveError: string | null
  onLeave: () => void
}) {
  const [copied, setCopied] = useState(false)

  async function copyTableId() {
    await navigator.clipboard.writeText(tableId)
    setCopied(true)
  }

  return (
    <main className="waiting-table-page">
      <button
        className="waiting-table-back"
        type="button"
        disabled={isLeaving}
        onClick={onLeave}
      >
        {isLeaving ? (
          <LoaderCircle className="is-spinning" size={17} />
        ) : (
          <ArrowLeft size={17} />
        )}
        {isLeaving ? 'Leaving' : 'Leave waiting room'}
      </button>

      <section className="waiting-table-panel">
        <span className="waiting-pulse" aria-hidden="true" />
        <span className="waiting-kicker">{gameName}</span>
        <h1>Waiting for players</h1>
        <p>The game starts automatically when the table is full.</p>

        <div className="table-share-field">
          <span>Table ID</span>
          <code>{tableId}</code>
          <button type="button" onClick={copyTableId}>
            <Copy size={17} />
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>

        <div className="waiting-status">
          <LoaderCircle aria-hidden="true" />
          Checking table status
        </div>

        {leaveError && <div className="waiting-leave-error" role="alert">{leaveError}</div>}
      </section>
    </main>
  )
}

function GameTablePage() {
  const { gameId, tableId } = useParams()
  const game = findGame(gameId)
  const navigate = useNavigate()
  const { user } = useAuth()
  const [phase, setPhase] = useState<PagePhase>('loading')
  const [loadedState, setLoadedState] = useState<LoadedGameState | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionPending, setActionPending] = useState(false)

  useEffect(() => {
    if (!game || !tableId || user === null || actionPending) {
      return
    }

    const activeGame = game
    const activeTableId = tableId
    const activeUser = user

    let cancelled = false
    let pollTimer: number | undefined

    async function pollGameState() {
      let continuePolling = true

      try {
        const nextState = await getGameState(activeGame.id, activeTableId, activeUser.userId)

        if (!cancelled) {
          setLoadedState(nextState)
          setLoadError(null)
          setPhase('ready')
        }
      } catch (error) {
        if (cancelled) {
          return
        }

        if (isTableNotFoundError(error)) {
          continuePolling = false
          navigate(`/games/${activeGame.id}`, {
            replace: true,
            state: {
              tableClosedMessage: 'The table was closed because its creator left.',
            },
          })
        } else if (isGameWaitingError(error)) {
          setPhase('waiting')
          setLoadError(null)
        } else {
          if (!(error instanceof Error)) {
            throw error
          }

          continuePolling = false
          setLoadError(error.message)
          setPhase('error')
        }
      } finally {
        if (!cancelled && continuePolling) {
          pollTimer = window.setTimeout(pollGameState, 1500)
        }
      }
    }

    void pollGameState()

    return () => {
      cancelled = true

      if (pollTimer !== undefined) {
        window.clearTimeout(pollTimer)
      }
    }
  }, [actionPending, game, navigate, tableId, user])

  async function performAction(
    operation: () => Promise<LoadedGameState>,
  ): Promise<boolean> {
    setActionPending(true)
    setActionError(null)

    try {
      const nextState = await operation()
      setLoadedState(nextState)
      setPhase('ready')
      return true
    } catch (error) {
      if (!(error instanceof Error)) {
        throw error
      }

      setActionError(error.message)
      return false
    } finally {
      setActionPending(false)
    }
  }

  async function handleLeaveWaitingTable(
    tableIdToLeave: string,
    userId: string,
    destination: string,
  ) {
    setActionPending(true)
    setActionError(null)

    try {
      await leaveTable(tableIdToLeave, userId)
      navigate(destination)
    } catch (error) {
      if (!(error instanceof Error)) {
        throw error
      }

      setActionError(error.message)
    } finally {
      setActionPending(false)
    }
  }

  if (!game) {
    return (
      <TableMessage
        title="Game not found"
        message="This game is not implemented by the backend."
        linkLabel="Return to games"
        to="/#games"
      />
    )
  }

  if (!tableId) {
    return (
      <TableMessage
        title="Table not found"
        message="The table ID is missing from the address."
        linkLabel={`Return to ${game.name}`}
        to={`/games/${game.id}`}
      />
    )
  }

  if (user === null) {
    return (
      <TableMessage
        title="Test login required"
        message="Sign in before opening a game table."
        linkLabel={`Return to ${game.name}`}
        to={`/games/${game.id}`}
      />
    )
  }

  if (phase === 'loading') {
    return (
      <main className="game-table-loading">
        <LoaderCircle aria-hidden="true" />
        <span>Loading table</span>
      </main>
    )
  }

  if (phase === 'waiting') {
    return (
      <WaitingTable
        gameName={game.name}
        tableId={tableId}
        isLeaving={actionPending}
        leaveError={actionError}
        onLeave={() => void handleLeaveWaitingTable(
          tableId,
          user.userId,
          `/games/${game.id}`,
        )}
      />
    )
  }

  if (phase === 'error') {
    if (loadError === null) {
      throw new Error('Error phase is missing its backend error message')
    }

    return (
      <TableMessage
        title="Unable to open table"
        message={loadError}
        linkLabel={`Return to ${game.name}`}
        to={`/games/${game.id}`}
      />
    )
  }

  if (loadedState === null) {
    throw new Error('Ready table is missing its game state')
  }

  switch (loadedState.gameId) {
    case 'cheat':
      return (
        <CheatTableView
          game={game}
          tableId={tableId}
          userId={user.userId}
          state={loadedState.state}
          actionPending={actionPending}
          actionError={actionError}
          performAction={performAction}
        />
      )
    case 'tago':
      return (
        <TagoTableView
          game={game}
          tableId={tableId}
          userId={user.userId}
          state={loadedState.state}
          actionPending={actionPending}
          actionError={actionError}
          performAction={performAction}
        />
      )
    case 'tien-len':
      return (
        <TienLenTableView
          game={game}
          tableId={tableId}
          userId={user.userId}
          state={loadedState.state}
          actionPending={actionPending}
          actionError={actionError}
          performAction={performAction}
        />
      )
  }
}

export default GameTablePage
