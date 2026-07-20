import { useState } from 'react'
import {
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
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'

function AppLayout() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const location = useLocation()

  const closeMobileMenu = () => setMobileMenuOpen(false)
  const homeSectionIsActive = (hash: string) =>
    location.pathname === '/' &&
    (location.hash === hash || (!location.hash && hash === '#lobby'))

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
              <Link
                className={homeSectionIsActive('#tournaments') ? 'is-active' : undefined}
                to="/#tournaments"
                onClick={closeMobileMenu}
              >
                <Trophy size={18} />
                Tournaments
              </Link>
            </nav>
          </div>

          <div className="sidebar-section">
            <span className="sidebar-label">Available games</span>
            <nav className="sidebar-navigation" aria-label="Game navigation">
              <NavLink
                className={({ isActive }) => (isActive ? 'is-active' : undefined)}
                to="/games/cheat"
                onClick={closeMobileMenu}
              >
                <Spade size={18} />
                Cheat
                <span className="sidebar-count">1</span>
              </NavLink>
              <NavLink
                className={({ isActive }) => (isActive ? 'is-active' : undefined)}
                to="/games/tago"
                onClick={closeMobileMenu}
              >
                <Diamond size={18} />
                TAGO
                <span className="sidebar-count">1</span>
              </NavLink>
              <NavLink
                className={({ isActive }) => (isActive ? 'is-active' : undefined)}
                to="/games/durak"
                onClick={closeMobileMenu}
              >
                <Club size={18} />
                Durak
                <span className="sidebar-count">1</span>
              </NavLink>
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
