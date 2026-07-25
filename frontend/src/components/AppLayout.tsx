import { useState } from 'react'
import {
  Diamond,
  Gamepad2,
  LayoutDashboard,
  LoaderCircle,
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
}

function AppLayout() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [walletMenuOpen, setWalletMenuOpen] = useState(false)
  const [walletError, setWalletError] = useState<string | null>(null)
  const [walletIsSubmitting, setWalletIsSubmitting] = useState(false)
  const { user, status, connectWallet, signOut } = useAuth()
  const location = useLocation()

  const closeMobileMenu = () => setMobileMenuOpen(false)
  const homeSectionIsActive = (hash: string) =>
    location.pathname === '/' &&
    (location.hash === hash || (!location.hash && hash === '#lobby'))

  async function handleConnectWallet() {
    setWalletError(null)
    setWalletIsSubmitting(true)

    try {
      await connectWallet()
      setWalletMenuOpen(false)
    } catch (error) {
      if (!(error instanceof Error)) {
        throw error
      }

      setWalletError(error.message)
      setWalletMenuOpen(true)
    } finally {
      setWalletIsSubmitting(false)
    }
  }

  async function handleLogout() {
    setWalletError(null)
    setWalletIsSubmitting(true)

    try {
      await signOut()
      setWalletMenuOpen(false)
    } catch (error) {
      if (!(error instanceof Error)) {
        throw error
      }

      setWalletError(error.message)
    } finally {
      setWalletIsSubmitting(false)
    }
  }

  const shortWalletAddress = user
    ? `${user.walletAddress.slice(0, 6)}...${user.walletAddress.slice(-4)}`
    : null

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
          <div className="wallet-session-control">
            <button
              className={`wallet-button ${user ? 'is-connected' : ''}`}
              type="button"
              aria-expanded={walletMenuOpen}
              disabled={status === 'checking' || walletIsSubmitting}
              onClick={() => {
                if (user) {
                  setWalletMenuOpen((isOpen) => !isOpen)
                } else {
                  void handleConnectWallet()
                }
              }}
            >
              {status === 'checking' || walletIsSubmitting ? (
                <LoaderCircle className="is-spinning" size={18} />
              ) : user ? (
                <UserRound size={18} />
              ) : (
                <Wallet size={18} />
              )}
              <span>
                {status === 'checking'
                  ? 'Checking session'
                  : walletIsSubmitting
                    ? user
                      ? 'Disconnecting'
                      : 'Connecting'
                    : shortWalletAddress ?? 'Connect wallet'}
              </span>
            </button>

            {walletMenuOpen && (
              <div className="wallet-popover">
                {user ? (
                  <div className="wallet-session">
                    <span>Signed in as</span>
                    <strong>{user.name}</strong>
                    <code>{user.walletAddress}</code>
                    <span className="wallet-capability">EOA wallet</span>
                    <button
                      className="wallet-secondary-button"
                      type="button"
                      disabled={walletIsSubmitting}
                      onClick={() => void handleLogout()}
                    >
                      <LogOut size={16} />
                      Log out
                    </button>
                    {walletError && <span className="wallet-error">{walletError}</span>}
                  </div>
                ) : (
                  <div className="wallet-connect-panel">
                    <span>Wallet connection</span>
                    <strong>MetaMask</strong>
                    <span className="wallet-capability">EOA wallets only</span>
                    <button
                      className="wallet-primary-button"
                      type="button"
                      disabled={walletIsSubmitting}
                      onClick={() => void handleConnectWallet()}
                    >
                      <Wallet size={16} />
                      {walletIsSubmitting ? 'Connecting' : 'Connect MetaMask'}
                    </button>
                    {walletError && <span className="wallet-error">{walletError}</span>}
                  </div>
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
