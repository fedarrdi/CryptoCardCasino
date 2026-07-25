import { ArrowRight, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { games } from '../data/games.ts'

function HomePage() {
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
          <span className="feature-kicker">Rare cardroom</span>
          <h1 id="lobby-title">The table is yours.</h1>
          <p>
            Play Cheat at private player-versus-player tables.
          </p>
          <div className="feature-actions">
            <a className="primary-action" href="#games">
              Explore games
              <ArrowRight size={18} />
            </a>
          </div>
        </div>
      </section>

      <section className="content-section" id="games" aria-labelledby="games-title">
        <div className="section-heading">
          <div>
            <span className="section-kicker">Choose your table</span>
            <h2 id="games-title">Available games</h2>
          </div>
        </div>

        <div className="game-grid">
          {games.map((game) => (
            <Link
              className="game-card"
              key={game.id}
              to={`/games/${game.id}`}
              aria-label={`Open ${game.name} tables`}
            >
              <div className="game-card-artwork">
                <img src={game.image} alt={`${game.name} card game artwork`} />
                <span className={`game-player-range game-status-${game.accent}`}>
                  <Users size={15} />
                  {game.minimumPlayers}-{game.maximumPlayers} players
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
    </>
  )
}

export default HomePage
