import { useEffect, useState, type FormEvent } from 'react'
import { ArrowLeft, Hash, LogIn, Plus, Users } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createTable, joinTable } from '../api/tables.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { findGame } from '../data/games.ts'

type PendingAction = 'create' | 'join' | null

function GameLobbyPage() {
  const { gameId } = useParams()
  const game = findGame(gameId)
  const navigate = useNavigate()
  const { user } = useAuth()
  const [playersToStart, setPlayersToStart] = useState(2)
  const [tableId, setTableId] = useState('')
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (game) {
      setPlayersToStart(game.minimumPlayers)
    }
  }, [game])

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

    setPendingAction('create')
    setError(null)

    try {
      const createdTable = await createTable(selectedGame.backendType, playersToStart)
      await joinTable(createdTable.tableId, user.userId)
      navigate(`/games/${selectedGame.id}/tables/${createdTable.tableId}`)
    } catch (caughtError) {
      if (!(caughtError instanceof Error)) {
        throw caughtError
      }

      setError(caughtError.message)
    } finally {
      setPendingAction(null)
    }
  }

  async function handleJoinTable(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (user === null) {
      return
    }

    const normalizedTableId = tableId.trim()
    setPendingAction('join')
    setError(null)

    try {
      await joinTable(normalizedTableId, user.userId)
      navigate(`/games/${selectedGame.id}/tables/${normalizedTableId}`)
    } catch (caughtError) {
      if (!(caughtError instanceof Error)) {
        throw caughtError
      }

      setError(caughtError.message)
    } finally {
      setPendingAction(null)
    }
  }

  const playerCounts = Array.from(
    { length: selectedGame.maximumPlayers - selectedGame.minimumPlayers + 1 },
    (_, index) => selectedGame.minimumPlayers + index,
  )

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
          <span className="section-kicker">Private table</span>
          <h1 id="game-page-title">{selectedGame.name}</h1>
          <p>{selectedGame.description}</p>
          <div className="game-page-tags">
            <span>
              <Users size={16} />
              {selectedGame.minimumPlayers}-{selectedGame.maximumPlayers} players
            </span>
            <span>
              <Hash size={16} />
              Join with table ID
            </span>
          </div>
        </div>
      </section>

      <section className="table-entry-section" aria-labelledby="table-entry-title">
        <div className="section-heading">
          <div>
            <span className="section-kicker">Take a seat</span>
            <h2 id="table-entry-title">Open or join a table</h2>
          </div>
          <span className={`session-indicator ${user ? 'is-ready' : ''}`}>
            {user ? `Playing as ${user.name}` : 'Test login required'}
          </span>
        </div>

        <div className="table-entry-grid">
          <form className="table-entry-panel" onSubmit={handleCreateTable}>
            <div className="table-entry-panel-heading">
              <span className="table-entry-icon" aria-hidden="true">
                <Plus />
              </span>
              <div>
                <h3>Create table</h3>
                <p>Choose how many players are needed before the game starts.</p>
              </div>
            </div>

            <fieldset className="player-count-fieldset" disabled={pendingAction !== null}>
              <legend>Players</legend>
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
              className="table-entry-submit"
              type="submit"
              disabled={user === null || pendingAction !== null}
            >
              <Plus size={17} />
              {pendingAction === 'create' ? 'Opening table...' : 'Create and join'}
            </button>
          </form>

          <form className="table-entry-panel" onSubmit={handleJoinTable}>
            <div className="table-entry-panel-heading">
              <span className="table-entry-icon" aria-hidden="true">
                <LogIn />
              </span>
              <div>
                <h3>Join table</h3>
                <p>Enter the table ID shared by the player who created it.</p>
              </div>
            </div>

            <label className="table-id-field">
              <span>Table ID</span>
              <input
                value={tableId}
                placeholder="00000000-0000-0000-0000-000000000000"
                spellCheck={false}
                onChange={(event) => setTableId(event.target.value)}
                disabled={pendingAction !== null}
              />
            </label>

            <button
              className="table-entry-submit"
              type="submit"
              disabled={user === null || pendingAction !== null || !tableId.trim()}
            >
              <LogIn size={17} />
              {pendingAction === 'join' ? 'Joining table...' : 'Join table'}
            </button>
          </form>
        </div>

        {error && <div className="form-error" role="alert">{error}</div>}
      </section>
    </div>
  )
}

export default GameLobbyPage
