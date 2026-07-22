import { type FormEvent, useState } from 'react'
import {
  Club,
  Diamond,
  Gamepad2,
  LayoutDashboard,
  LogOut,
  Menu,
  Spade,
  UserRound,
  Wallet,
  X,
  type LucideIcon,
} from 'lucide-react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.ts'
import { games, type GameId } from '../data/games.ts'

const GAME_ICONS: Record<GameId, LucideIcon> = {
  cheat: Spade,
  tago: Diamond,
  'tien-len': Club,
}

function AppLayout() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [loginMenuOpen, setLoginMenuOpen] = useState(false)
  const [loginName, setLoginName] = useState('')
  const [loginError, setLoginError] = useState<string | null>(null)
  const [loginIsSubmitting, setLoginIsSubmitting] = useState(false)
  const { user, signIn, signOut } = useAuth()
  const location = useLocation()

  const closeMobileMenu = () => setMobileMenuOpen(false)
  const homeSectionIsActive = (hash: string) =>
    location.pathname === '/' &&
    (location.hash === hash || (!location.hash && hash === '#lobby'))

  async function handleLoginSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoginError(null)
    setLoginIsSubmitting(true)

    try {
      await signIn(loginName)
      setLoginMenuOpen(false)
      setLoginName('')
    } catch (error) {
      if (!(error instanceof Error)) {
        throw error
      }

      setLoginError(error.message)
    } finally {
      setLoginIsSubmitting(false)
    }
  }

  function handleLogout() {
    signOut()
    setLoginMenuOpen(false)
  }

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

        <Link className="brand" to="/" aria-label="RareTable home">
          <span className="brand-mark" aria-hidden="true">
            <Diamond size={19} fill="currentColor" />
          </span>
          <span>RARETABLE</span>
        </Link>

        <div className="topbar-actions">
          <div className="dev-login">
            <button
              className={`wallet-button ${user ? 'is-connected' : ''}`}
              type="button"
              aria-expanded={loginMenuOpen}
              onClick={() => setLoginMenuOpen((isOpen) => !isOpen)}
            >
              {user ? <UserRound size={18} /> : <Wallet size={18} />}
              <span>{user ? user.name : 'Test login'}</span>
            </button>

            {loginMenuOpen && (
              <div className="login-popover">
                {user ? (
                  <div className="login-session">
                    <span>Signed in as</span>
                    <strong>{user.name}</strong>
                    <code>{user.userId}</code>
                    <button className="login-secondary-button" type="button" onClick={handleLogout}>
                      <LogOut size={16} />
                      Log out
                    </button>
                  </div>
                ) : (
                  <form className="login-form" onSubmit={handleLoginSubmit}>
                    <label htmlFor="dev-login-name">Name</label>
                    <input
                      id="dev-login-name"
                      value={loginName}
                      autoComplete="name"
                      onChange={(event) => setLoginName(event.target.value)}
                    />
                    <button
                      className="login-submit-button"
                      type="submit"
                      disabled={loginIsSubmitting || !loginName.trim()}
                    >
                      {loginIsSubmitting ? 'Creating...' : 'Create user'}
                    </button>
                    {loginError && <span className="login-error">{loginError}</span>}
                  </form>
                )}
              </div>
            )}
          </div>
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
              <Link
                className={homeSectionIsActive('#lobby') ? 'is-active' : undefined}
                to="/#lobby"
                onClick={closeMobileMenu}
              >
                <LayoutDashboard size={18} />
                Lobby
              </Link>
              <Link
                className={
                  homeSectionIsActive('#games') || location.pathname.startsWith('/games/')
                    ? 'is-active'
                    : undefined
                }
                to="/#games"
                onClick={closeMobileMenu}
              >
                <Gamepad2 size={18} />
                Games
              </Link>
            </nav>
          </div>

          <div className="sidebar-section">
            <span className="sidebar-label">Available games</span>
            <nav className="sidebar-navigation" aria-label="Game navigation">
              {games.map((game) => {
                const GameIcon = GAME_ICONS[game.id]

                return (
                  <NavLink
                    className={({ isActive }) => (isActive ? 'is-active' : undefined)}
                    to={`/games/${game.id}`}
                    onClick={closeMobileMenu}
                    key={game.id}
                  >
                    <GameIcon size={18} />
                    {game.name}
                  </NavLink>
                )
              })}
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
          <Outlet />
          <footer className="footer">
            <span>RARETABLE</span>
            <span>Competitive card games, built for players.</span>
          </footer>
        </main>
      </div>
    </div>
  )
}

export default AppLayout
