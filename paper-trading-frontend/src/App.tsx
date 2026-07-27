import { useCallback, useEffect, useState } from 'react'

import {
  ApiError,
  createLoginChallenge,
  createSession,
  deleteSession,
  getBtcPrice,
  getCurrentUser,
  type AuthenticatedUser,
  type BtcPrice,
} from './api.ts'
import { BtcChart, LockedBtcChart } from './BtcChart.tsx'
import { connectMetaMask, signMessage } from './wallet.ts'

type AuthStatus = 'checking' | 'unauthenticated' | 'authenticated'
type PendingAction = 'login' | 'price' | 'logout' | null

const priceFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

function shortenAddress(address: string): string {
  return `${address.slice(0, 6)}…${address.slice(-4)}`
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }

  return 'The request failed.'
}

function App() {
  const [authStatus, setAuthStatus] = useState<AuthStatus>('checking')
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [btcPrice, setBtcPrice] = useState<BtcPrice | null>(null)
  const [fetchedAt, setFetchedAt] = useState<Date | null>(null)
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const abortController = new AbortController()

    async function restoreSession() {
      try {
        const currentUser = await getCurrentUser(abortController.signal)

        if (!abortController.signal.aborted) {
          setUser(currentUser)
          setAuthStatus('authenticated')
        }
      } catch (requestError) {
        if (abortController.signal.aborted) {
          return
        }

        setAuthStatus('unauthenticated')

        if (!(requestError instanceof ApiError && requestError.status === 401)) {
          setError(errorMessage(requestError))
        }
      }
    }

    void restoreSession()
    return () => abortController.abort()
  }, [])

  async function handleLogin() {
    setPendingAction('login')
    setError(null)

    try {
      const wallet = await connectMetaMask()
      const challenge = await createLoginChallenge(
        wallet.walletAddress,
        wallet.chainId,
      )
      const signature = await signMessage(
        wallet.provider,
        wallet.walletAddress,
        challenge.message,
      )
      const authenticatedUser = await createSession(
        challenge.nonce,
        signature,
      )

      setUser(authenticatedUser)
      setAuthStatus('authenticated')
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setPendingAction(null)
    }
  }

  async function handlePriceRequest() {
    setPendingAction('price')
    setError(null)

    try {
      const price = await getBtcPrice()
      setBtcPrice(price)
      setFetchedAt(new Date())
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        setUser(null)
        setAuthStatus('unauthenticated')
      }

      setError(errorMessage(requestError))
    } finally {
      setPendingAction(null)
    }
  }

  async function handleLogout() {
    setPendingAction('logout')
    setError(null)

    try {
      await deleteSession()
      setUser(null)
      setBtcPrice(null)
      setFetchedAt(null)
      setAuthStatus('unauthenticated')
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setPendingAction(null)
    }
  }

  const handleSessionExpired = useCallback(() => {
    setUser(null)
    setBtcPrice(null)
    setFetchedAt(null)
    setAuthStatus('unauthenticated')
    setError('Your wallet session expired. Connect again to view market data.')
  }, [])

  const isAuthenticated = authStatus === 'authenticated' && user !== null

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="RareTable Paper home">
          <span className="brand-mark">R</span>
          <span>
            <strong>RareTable</strong>
            <small>Paper</small>
          </span>
        </a>

        <div className="wallet-area">
          {authStatus === 'checking' && (
            <span className="session-copy">Checking session…</span>
          )}

          {isAuthenticated && (
            <>
              <span className="wallet-chip">
                <span className="status-dot" />
                {shortenAddress(user.walletAddress)}
              </span>
              <button
                className="text-button"
                type="button"
                onClick={handleLogout}
                disabled={pendingAction !== null}
              >
                {pendingAction === 'logout' ? 'Signing out…' : 'Sign out'}
              </button>
            </>
          )}

          {authStatus === 'unauthenticated' && (
            <button
              className="wallet-button"
              type="button"
              onClick={handleLogin}
              disabled={pendingAction !== null}
            >
              <span className="wallet-icon" aria-hidden="true">◇</span>
              {pendingAction === 'login' ? 'Check MetaMask…' : 'Connect wallet'}
            </button>
          )}
        </div>
      </header>

      <main>
        <section className="intro">
          <div>
            <span className="eyebrow">Market sandbox · zero capital at risk</span>
            <h1>Make the call.<br />Keep the lesson.</h1>
          </div>
          <p>
            A focused paper-trading workspace for testing an idea against live
            BTC market data—without putting real funds on the line.
          </p>
        </section>

        <section className="workspace">
          <article className="market-card">
            <div className="market-heading">
              <div className="asset">
                <span className="bitcoin-mark">₿</span>
                <div>
                  <span className="pair">BTC / USDT</span>
                  <span className="asset-name">Bitcoin</span>
                </div>
              </div>
              <span className="live-pill"><i /> Live market</span>
            </div>

            <div className="price-block" aria-live="polite">
              <span className="price-label">Current mid price</span>
              <strong className={btcPrice === null ? 'price-empty' : ''}>
                {btcPrice === null ? (
                  '—'
                ) : (
                  <>
                    {priceFormatter.format(btcPrice.price)} <em>USDT</em>
                  </>
                )}
              </strong>
              <span className="price-note">
                {fetchedAt === null
                  ? 'Best bid + best ask, divided by two'
                  : `Fetched at ${fetchedAt.toLocaleTimeString()}`}
              </span>
            </div>

            <button
              className="price-button"
              type="button"
              onClick={handlePriceRequest}
              disabled={!isAuthenticated || pendingAction !== null}
            >
              <span>
                {pendingAction === 'price'
                  ? 'Fetching Binance price…'
                  : btcPrice === null
                    ? 'Get BTC price'
                    : 'Refresh BTC price'}
              </span>
              <span aria-hidden="true">↗</span>
            </button>

            {!isAuthenticated && authStatus !== 'checking' && (
              <p className="locked-note">
                Connect your wallet to request live market data.
              </p>
            )}
          </article>

          <aside className="side-panel">
            <div>
              <span className="panel-label">Session</span>
              <h2>
                {isAuthenticated ? `Welcome, ${user.name}` : 'Wallet required'}
              </h2>
              <p>
                {isAuthenticated
                  ? 'Your signed wallet session is active. No transaction or gas fee is required.'
                  : 'Sign a one-time login message in MetaMask. This proves wallet ownership without moving funds.'}
              </p>
            </div>

            <div className="data-source">
              <span className="source-icon" aria-hidden="true">B</span>
              <div>
                <small>Market source</small>
                <strong>Binance Spot</strong>
              </div>
              <span className="source-status">On request</span>
            </div>
          </aside>
        </section>

        {isAuthenticated ? (
          <BtcChart onSessionExpired={handleSessionExpired} />
        ) : (
          <LockedBtcChart />
        )}

        {error !== null && (
          <div className="error-banner" role="alert">
            <span>!</span>
            <p>{error}</p>
            <button type="button" onClick={() => setError(null)} aria-label="Dismiss error">
              ×
            </button>
          </div>
        )}
      </main>

      <footer>
        <span>RareTable Paper · Simulation environment</span>
        <span>Prices are informational, not financial advice.</span>
      </footer>
    </div>
  )
}

export default App
