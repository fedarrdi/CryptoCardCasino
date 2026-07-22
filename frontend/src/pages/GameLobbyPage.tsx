import { useEffect, useState, type FormEvent } from 'react'
import {
  ArrowLeft,
  LoaderCircle,
  LogIn,
  Plus,
  RefreshCw,
  Users,
} from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  createTable,
  getAllTables,
  joinTable,
  type TableStatus,
  type TableSummary,
} from '../api/tables.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { findGame } from '../data/games.ts'

type PendingAction =
  | { type: 'create' }
  | { type: 'join'; tableId: string }
  | null

type TableListPhase = 'loading' | 'ready' | 'error'

const TABLE_STATUS_LABELS: Record<TableStatus, string> = {
  WAITING: 'Open',
  IN_GAME: 'In progress',
  CLOSED: 'Closed',
}

const TABLE_STATUS_ORDER: Record<TableStatus, number> = {
  WAITING: 0,
  IN_GAME: 1,
  CLOSED: 2,
}

const TABLE_STATUS_CLASS_NAMES: Record<TableStatus, string> = {
  WAITING: 'waiting',
  IN_GAME: 'in-game',
  CLOSED: 'closed',
}

function shortTableId(tableId: string): string {
  return tableId.slice(0, 8).toUpperCase()
}

function GameLobbyPage() {
  const { gameId } = useParams()
  const game = findGame(gameId)
  const navigate = useNavigate()
  const { user } = useAuth()
  const [playersToStart, setPlayersToStart] = useState(2)
  const [tables, setTables] = useState<TableSummary[]>([])
  const [tableListPhase, setTableListPhase] = useState<TableListPhase>('loading')
  const [tableListError, setTableListError] = useState<string | null>(null)
  const [refreshRequest, setRefreshRequest] = useState(0)
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (game) {
      setPlayersToStart(game.minimumPlayers)
    }
  }, [game])

  useEffect(() => {
    if (!game) {
      return
    }

    const backendGameType = game.backendType
    const abortController = new AbortController()
    let pollingTimer: number | undefined

    setTables([])
    setTableListError(null)
    setTableListPhase('loading')

    async function loadTables() {
      try {
        const tableSummaries = await getAllTables(abortController.signal)
        const matchingTables = tableSummaries
          .filter((table) => table.gameType === backendGameType)
          .sort((left, right) => {
            const statusDifference =
              TABLE_STATUS_ORDER[left.status] - TABLE_STATUS_ORDER[right.status]
            return statusDifference || left.tableId.localeCompare(right.tableId)
          })

        setTables(matchingTables)
        setTableListError(null)
        setTableListPhase('ready')
      } catch (caughtError) {
        if (abortController.signal.aborted) {
          return
        }

        if (!(caughtError instanceof Error)) {
          throw caughtError
        }

        setTableListError(caughtError.message)
        setTableListPhase('error')
      } finally {
        if (!abortController.signal.aborted) {
          pollingTimer = window.setTimeout(loadTables, 4000)
        }
      }
    }

    void loadTables()

    return () => {
      abortController.abort()

      if (pollingTimer !== undefined) {
        window.clearTimeout(pollingTimer)
      }
    }
  }, [game, refreshRequest])

  if (!game) {
    return (
      <section className="not-found-page">
        <span className="section-kicker">Game not found</span>
        <h1>This cardroom is not available.</h1>
        <p>Choose one of the games currently listed in the main lobby.</p>
        <Link className="primary-action" to="/#games">
          Back to games
        </Link>
      </section>
    )
  }

  const selectedGame = game

  async function handleCreateTable(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (user === null) {
      return
    }

    setPendingAction({ type: 'create' })
    setActionError(null)

    try {
      const createdTable = await createTable(selectedGame.backendType, playersToStart)
      await joinTable(createdTable.tableId, user.userId)
      navigate(`/games/${selectedGame.id}/tables/${createdTable.tableId}`)
    } catch (caughtError) {
      if (!(caughtError instanceof Error)) {
        throw caughtError
      }

      setActionError(caughtError.message)
    } finally {
      setPendingAction(null)
    }
  }

  async function handleJoinTable(tableId: string) {
    if (user === null) {
      return
    }

    setPendingAction({ type: 'join', tableId })
    setActionError(null)

    try {
      await joinTable(tableId, user.userId)
      navigate(`/games/${selectedGame.id}/tables/${tableId}`)
    } catch (caughtError) {
      if (!(caughtError instanceof Error)) {
        throw caughtError
      }

      setActionError(caughtError.message)
    } finally {
      setPendingAction(null)
    }
  }

  const playerCounts = Array.from(
    { length: selectedGame.maximumPlayers - selectedGame.minimumPlayers + 1 },
    (_, index) => selectedGame.minimumPlayers + index,
  )
  const waitingTableCount = tables.filter((table) => table.status === 'WAITING').length

  return (
    <div className="game-lobby-page">
      <Link className="back-link" to="/#games">
        <ArrowLeft size={17} />
        All games
      </Link>

      <section className="game-page-feature" aria-labelledby="game-page-title">
        <img src={selectedGame.image} alt={`${selectedGame.name} card game artwork`} />
        <div className="game-page-feature-scrim" aria-hidden="true" />
        <div className="game-page-feature-content">
          <span className="section-kicker">Cardroom</span>
          <h1 id="game-page-title">{selectedGame.name}</h1>
          <p>{selectedGame.description}</p>
          <div className="game-page-tags">
            <span>
              <Users size={16} />
              {selectedGame.minimumPlayers}-{selectedGame.maximumPlayers} players
            </span>
            <span>
              <LogIn size={16} />
              {tableListPhase === 'ready'
                ? `${waitingTableCount} open ${waitingTableCount === 1 ? 'table' : 'tables'}`
                : 'Checking open tables'}
            </span>
          </div>
        </div>
      </section>

      <section className="table-browser-section" aria-labelledby="table-browser-title">
        <div className="table-browser-header">
          <div>
            <span className="section-kicker">Take a seat</span>
            <h2 id="table-browser-title">{selectedGame.name} tables</h2>
          </div>

          <div className="table-browser-tools">
            <form className="table-create-control" onSubmit={handleCreateTable}>
              <fieldset disabled={pendingAction !== null}>
                <legend>Seats</legend>
                <div className="player-count-control">
                  {playerCounts.map((playerCount) => (
                    <button
                      className={playersToStart === playerCount ? 'is-active' : ''}
                      type="button"
                      aria-pressed={playersToStart === playerCount}
                      onClick={() => setPlayersToStart(playerCount)}
                      key={playerCount}
                    >
                      {playerCount}
                    </button>
                  ))}
                </div>
              </fieldset>

              <button
                className="table-create-button"
                type="submit"
                disabled={user === null || pendingAction !== null}
              >
                {pendingAction?.type === 'create' ? (
                  <LoaderCircle className="is-spinning" size={16} />
                ) : (
                  <Plus size={16} />
                )}
                New table
              </button>
            </form>

            <button
              className="table-refresh-button"
              type="button"
              aria-label="Refresh tables"
              title="Refresh tables"
              onClick={() => setRefreshRequest((request) => request + 1)}
              disabled={tableListPhase === 'loading'}
            >
              <RefreshCw className={tableListPhase === 'loading' ? 'is-spinning' : ''} size={17} />
            </button>
          </div>
        </div>

        {!user && <div className="table-login-notice">Use Test login to create or join a table.</div>}

        <div className="table-list" aria-live="polite">
          {tableListPhase === 'loading' && (
            <div className="table-list-state">
              <LoaderCircle className="is-spinning" aria-hidden="true" />
              <span>Loading tables</span>
            </div>
          )}

          {tableListPhase === 'error' && tableListError && (
            <div className="table-list-state is-error" role="alert">
              <strong>Unable to load tables</strong>
              <span>{tableListError}</span>
            </div>
          )}

          {tableListPhase === 'ready' && tables.length === 0 && (
            <div className="table-list-state is-empty">
              <span className="empty-table-mark" aria-hidden="true" />
              <strong>No {selectedGame.name} tables yet</strong>
              <span>Be the first player at the table.</span>
            </div>
          )}

          {tableListPhase === 'ready' && tables.map((table) => {
            const isWaiting = table.status === 'WAITING'
            const isJoining =
              pendingAction?.type === 'join' && pendingAction.tableId === table.tableId
            const statusClassName = TABLE_STATUS_CLASS_NAMES[table.status]
            const seatsFilled = Math.min(
              100,
              Math.round((table.playersJoined / table.playersToStart) * 100),
            )

            return (
              <button
                className={`table-list-row table-status-${statusClassName}`}
                type="button"
                aria-label={isWaiting ? `Join table ${shortTableId(table.tableId)}` : undefined}
                onClick={() => void handleJoinTable(table.tableId)}
                disabled={!isWaiting || user === null || pendingAction !== null}
                key={table.tableId}
              >
                <span className="table-list-identity">
                  <span className="table-list-number">Table {shortTableId(table.tableId)}</span>
                  <span className="table-list-id">{table.tableId}</span>
                </span>

                <span className="table-list-seats">
                  <span>
                    <Users size={16} />
                    {table.playersJoined} / {table.playersToStart} players
                  </span>
                  <span className="table-seat-meter" aria-hidden="true">
                    <span style={{ width: `${seatsFilled}%` }} />
                  </span>
                </span>

                <span className={`table-list-status is-${statusClassName}`}>
                  {TABLE_STATUS_LABELS[table.status]}
                </span>

                <span className="table-list-action">
                  {isJoining ? (
                    <>
                      <LoaderCircle className="is-spinning" size={16} />
                      Joining
                    </>
                  ) : isWaiting ? (
                    <>
                      {user ? 'Join' : 'Sign in'}
                      <LogIn size={16} />
                    </>
                  ) : null}
                </span>
              </button>
            )
          })}
        </div>

        {actionError && <div className="form-error" role="alert">{actionError}</div>}
      </section>
    </div>
  )
}

export default GameLobbyPage
