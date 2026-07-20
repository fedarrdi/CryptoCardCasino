import { useState } from 'react'
import {
  ArrowRight,
  CalendarDays,
  ChevronRight,
  CircleDollarSign,
  Clock3,
  Diamond,
  Gamepad2,
  LayoutDashboard,
  Menu,
  ShieldCheck,
  Spade,
  Ticket,
  Trophy,
  Users,
  Wallet,
  X,
} from 'lucide-react'

type GameId = 'cheat' | 'tago'
type GameFilter = 'all' | GameId

const games = [
  {
    id: 'cheat' as const,
    name: 'Cheat',
    eyebrow: '6-player bluffing',
    description: 'Make the claim. Read the table. Call the bluff.',
    image: '/images/cheat-table.jpg',
    entry: '25 USDC',
    prize: '148.50 USDC',
    players: 4,
    capacity: 6,
    starts: '18 min',
    accent: 'red',
  },
  {
    id: 'tago' as const,
    name: 'TAGO',
    eyebrow: 'Rule of Pair',
    description: 'Build the pair. Count the point. Win the round.',
    image: '/images/tago-table.jpg',
    entry: '40 USDC',
    prize: '633.60 USDC',
    players: 8,
    capacity: 16,
    starts: '42 min',
    accent: 'blue',
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

        <nav className="top-navigation" aria-label="Primary navigation">
          <a className="is-active" href="#lobby">
            Lobby
          </a>
          <a href="#games">Games</a>
          <a href="#tournaments">Tournaments</a>
        </nav>

        <div className="topbar-actions">
          <span className="chain-status">
            <span className="status-dot" aria-hidden="true" />
            Live lobby
          </span>
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
            </nav>
          </div>

          <div className="sidebar-payout">
            <div className="sidebar-payout-heading">
              <ShieldCheck size={18} />
              <span>Payout split</span>
            </div>
            <div className="payout-values">
              <strong>99%</strong>
              <span>to the winner</span>
            </div>
            <div className="payout-bar" aria-hidden="true">
              <span />
            </div>
            <p>Every tournament. A fixed 1% platform fee.</p>
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
                and the games you cannot find at an ordinary cardroom.
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

          <section className="lobby-stats" aria-label="Lobby statistics">
            <div>
              <span>Live tables</span>
              <strong>02</strong>
            </div>
            <div>
              <span>Open seats</span>
              <strong>10</strong>
            </div>
            <div>
              <span>Prize pools</span>
              <strong>782 USDC</strong>
            </div>
            <div>
              <span>Platform fee</span>
              <strong>1%</strong>
            </div>
          </section>

          <section className="content-section" id="games" aria-labelledby="games-title">
            <div className="section-heading">
              <div>
                <span className="section-kicker">Choose your table</span>
                <h2 id="games-title">Games in the lobby</h2>
              </div>
              <div className="game-filters" aria-label="Filter games">
                {(['all', 'cheat', 'tago'] as GameFilter[]).map((filter) => (
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
              {visibleGames.map((game) => {
                const occupancy = (game.players / game.capacity) * 100

                return (
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

                      <div className="game-details">
                        <div>
                          <Ticket size={17} />
                          <span>
                            Entry
                            <strong>{game.entry}</strong>
                          </span>
                        </div>
                        <div>
                          <CircleDollarSign size={17} />
                          <span>
                            Winner takes
                            <strong>{game.prize}</strong>
                          </span>
                        </div>
                      </div>

                      <div className="seat-progress-row">
                        <span>
                          <Users size={16} />
                          {game.players} of {game.capacity} joined
                        </span>
                        <strong>{game.capacity - game.players} seats left</strong>
                      </div>
                      <div className="seat-progress" aria-hidden="true">
                        <span style={{ width: `${occupancy}%` }} />
                      </div>
                    </div>
                  </article>
                )
              })}
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
            <span>Player versus player. Winner takes 99%.</span>
          </footer>
        </main>
      </div>
    </div>
  )
}

export default App
