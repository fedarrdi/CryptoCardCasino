import { useState } from 'react'
import {
  ArrowRight,
  CalendarDays,
  ChevronRight,
  Clock3,
  Club,
  Diamond,
  Gamepad2,
  LayoutDashboard,
  Menu,
  Spade,
  Trophy,
  Wallet,
  X,
} from 'lucide-react'

type GameId = 'cheat' | 'tago' | 'durak'
type GameFilter = 'all' | GameId

const games = [
  {
    id: 'cheat' as const,
    name: 'Cheat',
    eyebrow: '6-player bluffing',
    description: 'Make the claim. Read the table. Call the bluff.',
    image: '/images/cheat-table.jpg',
    starts: '18 min',
    accent: 'red',
  },
  {
    id: 'tago' as const,
    name: 'TAGO',
    eyebrow: 'Rule of Pair',
    description: 'Build the pair. Count the point. Win the round.',
    image: '/images/tago-table.jpg',
    starts: '42 min',
    accent: 'blue',
  },
  {
    id: 'durak' as const,
    name: 'Durak',
    eyebrow: 'Last card loses',
    description: 'Play your hand and avoid being the last player holding cards.',
    image: '/images/durak-table.jpg',
    starts: '1 hr',
    accent: 'green',
  },
]

const tournaments = [
  {
    game: 'Cheat',
    name: 'Monday Bluff Club',
    format: '6-player table',
    entry: '25 USDC',
    time: '18 min',
    seats: '4 / 6',
  },
  {
    game: 'TAGO',
    name: 'Rule of Pair Open',
    format: '16-player bracket',
    entry: '40 USDC',
    time: '42 min',
    seats: '8 / 16',
  },
  {
    game: 'Cheat',
    name: 'Night Table',
    format: '6-player table',
    entry: '100 USDC',
    time: '2 hr',
    seats: '2 / 6',
  },
]

function App() {
  const [activeFilter, setActiveFilter] = useState<GameFilter>('all')
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const visibleGames =
    activeFilter === 'all'
      ? games
      : games.filter((game) => game.id === activeFilter)

  const closeMobileMenu = () => setMobileMenuOpen(false)

  return (
    <div className="app-shell">
      <header className="topbar">
        <button
          className="icon-button mobile-menu-button"
          type="button"
          aria-label="Open navigation"
          onClick={() => setMobileMenuOpen(true)}
        >
          <Menu size={21} />
        </button>

        <a className="brand" href="#lobby" aria-label="RareTable home">
          <span className="brand-mark" aria-hidden="true">
            <Diamond size={19} fill="currentColor" />
          </span>
          <span>RARETABLE</span>
        </a>

        <div className="topbar-actions">
          <button className="wallet-button" type="button">
            <Wallet size={18} />
            <span>Connect wallet</span>
          </button>
        </div>
      </header>

      <div className="page-layout">
        <aside className={`sidebar ${mobileMenuOpen ? 'is-open' : ''}`}>
          <div className="sidebar-mobile-header">
            <span>Navigation</span>
            <button
              className="icon-button"
              type="button"
              aria-label="Close navigation"
              onClick={closeMobileMenu}
            >
              <X size={20} />
            </button>
          </div>

          <div className="sidebar-section">
            <span className="sidebar-label">Explore</span>
            <nav className="sidebar-navigation" aria-label="Lobby navigation">
              <a className="is-active" href="#lobby" onClick={closeMobileMenu}>
                <LayoutDashboard size={18} />
                Lobby
              </a>
              <a href="#games" onClick={closeMobileMenu}>
                <Gamepad2 size={18} />
                Games
              </a>
              <a href="#tournaments" onClick={closeMobileMenu}>
                <Trophy size={18} />
                Tournaments
              </a>
            </nav>
          </div>

          <div className="sidebar-section">
            <span className="sidebar-label">Available games</span>
            <nav className="sidebar-navigation" aria-label="Game navigation">
              <a href="#games" onClick={closeMobileMenu}>
                <Spade size={18} />
                Cheat
                <span className="sidebar-count">1</span>
              </a>
              <a href="#games" onClick={closeMobileMenu}>
                <Diamond size={18} />
                TAGO
                <span className="sidebar-count">1</span>
              </a>
              <a href="#games" onClick={closeMobileMenu}>
                <Club size={18} />
                Durak
                <span className="sidebar-count">1</span>
              </a>
            </nav>
          </div>
        </aside>

        {mobileMenuOpen && (
          <button
            className="navigation-backdrop"
            type="button"
            aria-label="Close navigation"
            onClick={closeMobileMenu}
          />
        )}

        <main className="main-content">
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
                <article className="game-card" key={game.id}>
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
                      <a
                        className="round-action"
                        href="#tournaments"
                        aria-label={`View ${game.name} tournaments`}
                      >
                        <ArrowRight size={19} />
                      </a>
                    </div>

                    <p className="game-description">{game.description}</p>
                  </div>
                </article>
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
                  <a href="#games" aria-label={`Open ${tournament.name}`}>
                    <ChevronRight size={20} />
                  </a>
                </article>
              ))}
            </div>
          </section>

          <footer className="footer">
            <span>RARETABLE</span>
            <span>Competitive card games, built for players.</span>
          </footer>
        </main>
      </div>
    </div>
  )
}

export default App
