import { useState } from 'react'
import { ArrowRight, CalendarDays, ChevronRight, Clock3 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { games, type GameId } from '../data/games.ts'

type GameFilter = 'all' | GameId

const tournaments = [
  {
    gameId: 'cheat' as const,
    game: 'Cheat',
    name: 'Monday Bluff Club',
    format: '6-player table',
    entry: '25 USDC',
    time: '18 min',
    seats: '4 / 6',
  },
  {
    gameId: 'tago' as const,
    game: 'TAGO',
    name: 'Rule of Pair Open',
    format: '16-player bracket',
    entry: '40 USDC',
    time: '42 min',
    seats: '8 / 16',
  },
  {
    gameId: 'cheat' as const,
    game: 'Cheat',
    name: 'Night Table',
    format: '6-player table',
    entry: '100 USDC',
    time: '2 hr',
    seats: '2 / 6',
  },
]

function HomePage() {
  const [activeFilter, setActiveFilter] = useState<GameFilter>('all')

  const visibleGames =
    activeFilter === 'all'
      ? games
      : games.filter((game) => game.id === activeFilter)

  return (
    <>
      <section className="lobby-feature" id="lobby" aria-labelledby="lobby-title">
        <img
          className="lobby-feature-image"
          src="/images/cheat-table.jpg"
          alt="A face-down card lifted from a dramatic playing-card pile"
        />
        <div className="lobby-feature-scrim" aria-hidden="true" />
        <div className="lobby-feature-content">
          <span className="feature-kicker">
            <span className="status-dot" aria-hidden="true" />
            Tournament lobby is open
          </span>
          <h1 id="lobby-title">Card games worth bringing back.</h1>
          <p>
            Compete in player-versus-player tournaments for Cheat, TAGO,
            Durak, and the games you cannot find at an ordinary cardroom.
          </p>
          <div className="feature-actions">
            <a className="primary-action" href="#games">
              Explore games
              <ArrowRight size={18} />
            </a>
            <a className="secondary-action" href="#tournaments">
              View tournaments
            </a>
          </div>
        </div>
      </section>

      <section className="content-section" id="games" aria-labelledby="games-title">
        <div className="section-heading">
          <div>
            <span className="section-kicker">Choose your table</span>
            <h2 id="games-title">Games in the lobby</h2>
          </div>
          <div className="game-filters" aria-label="Filter games">
            {(['all', 'cheat', 'tago', 'durak'] as GameFilter[]).map((filter) => (
              <button
                key={filter}
                className={activeFilter === filter ? 'is-active' : ''}
                type="button"
                aria-pressed={activeFilter === filter}
                onClick={() => setActiveFilter(filter)}
              >
                {filter === 'all' ? 'All games' : filter.toUpperCase()}
              </button>
            ))}
          </div>
        </div>

        <div className="game-grid">
          {visibleGames.map((game) => (
            <Link
              className="game-card"
              key={game.id}
              to={`/games/${game.id}`}
              aria-label={`Open ${game.name} lobbies`}
            >
              <div className="game-card-artwork">
                <img src={game.image} alt={`${game.name} card game artwork`} />
                <span className={`game-status game-status-${game.accent}`}>
                  <span className="status-dot" aria-hidden="true" />
                  Table forming
                </span>
                <span className="game-start-time">
                  <Clock3 size={15} />
                  {game.starts}
                </span>
              </div>

              <div className="game-card-content">
                <div className="game-card-title-row">
                  <div>
                    <span className="game-eyebrow">{game.eyebrow}</span>
                    <h3>{game.name}</h3>
                  </div>
                </div>

                <p className="game-description">{game.description}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section
        className="content-section tournament-section"
        id="tournaments"
        aria-labelledby="tournaments-title"
      >
        <div className="section-heading">
          <div>
            <span className="section-kicker">Upcoming</span>
            <h2 id="tournaments-title">Next tournaments</h2>
          </div>
          <CalendarDays className="section-icon" size={22} />
        </div>

        <div className="tournament-list">
          <div className="tournament-list-header" aria-hidden="true">
            <span>Tournament</span>
            <span>Format</span>
            <span>Entry</span>
            <span>Starts</span>
            <span>Seats</span>
            <span />
          </div>

          {tournaments.map((tournament) => (
            <article className="tournament-row" key={tournament.name}>
              <div className="tournament-name">
                <span className="tournament-game">{tournament.game}</span>
                <strong>{tournament.name}</strong>
              </div>
              <span data-label="Format">{tournament.format}</span>
              <span data-label="Entry">{tournament.entry}</span>
              <span data-label="Starts">{tournament.time}</span>
              <span data-label="Seats">{tournament.seats}</span>
              <Link
                to={`/games/${tournament.gameId}`}
                aria-label={`Open ${tournament.name}`}
              >
                <ChevronRight size={20} />
              </Link>
            </article>
          ))}
        </div>
      </section>
    </>
  )
}

export default HomePage
