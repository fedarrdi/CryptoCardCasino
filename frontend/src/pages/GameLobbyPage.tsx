import { useState } from 'react'
import { ArrowLeft, Clock3, Coins, Gamepad2, Users } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { games } from '../data/games.ts'
import { lobbiesByGame } from '../data/lobbies.ts'

type StakeFilter = 'all' | number

function GameLobbyPage() {
  const { gameId } = useParams()
  const [selectedStake, setSelectedStake] = useState<StakeFilter>('all')
  const game = games.find((candidate) => candidate.id === gameId)

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

  const lobbies = lobbiesByGame[game.id]
  const stakes = lobbies.map((lobby) => lobby.stake)
  const visibleLobbies =
    selectedStake === 'all'
      ? lobbies
      : lobbies.filter((lobby) => lobby.stake === selectedStake)

  return (
    <div className="game-lobby-page">
      <Link className="back-link" to="/#games">
        <ArrowLeft size={17} />
        All games
      </Link>

      <section className="game-page-feature" aria-labelledby="game-page-title">
        <img src={game.image} alt={`${game.name} card game artwork`} />
        <div className="game-page-feature-scrim" aria-hidden="true" />
        <div className="game-page-feature-content">
          <span className="section-kicker">Game lobby</span>
          <h1 id="game-page-title">{game.name}</h1>
          <p>{game.description}</p>
          <div className="game-page-tags">
            <span>
              <Gamepad2 size={16} />
              {game.eyebrow}
            </span>
            <span>
              <Users size={16} />
              Player versus player
            </span>
          </div>
        </div>
      </section>

      <section className="lobby-picker" aria-labelledby="lobby-picker-title">
        <div className="lobby-picker-heading">
          <div>
            <span className="section-kicker">Available tables</span>
            <h2 id="lobby-picker-title">Choose your stake</h2>
          </div>

          <div className="stake-filter" aria-label="Filter lobbies by stake">
            {(['all', ...stakes] as StakeFilter[]).map((stake) => (
              <button
                key={stake}
                className={selectedStake === stake ? 'is-active' : ''}
                type="button"
                aria-pressed={selectedStake === stake}
                onClick={() => setSelectedStake(stake)}
              >
                {stake === 'all' ? 'All stakes' : `${stake} USDC`}
              </button>
            ))}
          </div>
        </div>

        <div className="lobby-list">
          <div className="lobby-list-header" aria-hidden="true">
            <span>Lobby</span>
            <span>Stake</span>
            <span>Players</span>
            <span>Starts</span>
            <span>Status</span>
            <span />
          </div>

          {visibleLobbies.map((lobby) => (
            <article className="lobby-row" key={lobby.id}>
              <div className="lobby-identity">
                <strong>Table {lobby.id}</strong>
                <span>Public lobby</span>
              </div>
              <div className="lobby-cell" data-label="Stake">
                <Coins size={17} />
                <strong>{lobby.stake} USDC</strong>
              </div>
              <div className="lobby-cell" data-label="Players">
                <Users size={17} />
                <span>
                  {lobby.players} / {lobby.capacity}
                </span>
              </div>
              <div className="lobby-cell" data-label="Starts">
                <Clock3 size={17} />
                <span>{lobby.starts}</span>
              </div>
              <span
                className={`lobby-availability lobby-availability-${lobby.availability
                  .toLowerCase()
                  .replace(' ', '-')}`}
              >
                {lobby.availability}
              </span>
              <button
                className="join-lobby-button"
                type="button"
                disabled={lobby.availability === 'Full'}
              >
                {lobby.availability === 'Full' ? 'Full' : 'Join lobby'}
              </button>
            </article>
          ))}
        </div>
      </section>
    </div>
  )
}

export default GameLobbyPage
