import { useCallback, useEffect, useState } from 'react'

import {
  ApiError,
  clearSessionCsrf,
  createLoginChallenge,
  createSession,
  deleteSession,
  getCurrentUser,
  initializeSessionCsrf,
  SessionUserMismatchError,
  type AuthenticatedUser,
} from './api.ts'
import { LockedBtcChart } from './BtcChart.tsx'
import { TradingWorkspace } from './TradingWorkspace.tsx'
import { connectMetaMask, signMessage } from './wallet.ts'

type AuthStatus = 'checking' | 'unauthenticated' | 'authenticated'
type PendingAction = 'login' | 'logout' | null

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
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const abortController = new AbortController()

    async function restoreSession() {
      try {
        const currentUser = await getCurrentUser(abortController.signal)

        if (abortController.signal.aborted) {
          return
        }

        await initializeSessionCsrf()

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
      clearSessionCsrf()
      await initializeSessionCsrf()

      setUser(authenticatedUser)
      setAuthStatus('authenticated')
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setPendingAction(null)
    }
  }

  async function handleLogout() {
    setPendingAction('logout')
    setError(null)

    try {
      if (user === null) {
        throw new Error('No authenticated wallet session is active.')
      }
      await deleteSession(user.userId)
      setUser(null)
      setAuthStatus('unauthenticated')
    } catch (requestError) {
      if (requestError instanceof SessionUserMismatchError) {
        handleSessionExpired()
      } else {
        setError(errorMessage(requestError))
      }
    } finally {
      setPendingAction(null)
    }
  }

  const handleSessionExpired = useCallback(() => {
    clearSessionCsrf()
    setUser(null)
    setAuthStatus('unauthenticated')
    setError('Your wallet session expired. Connect again to continue paper trading.')
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

      <main className={isAuthenticated ? 'trading-main' : undefined}>
        {isAuthenticated ? (
          <>
            <section className="dashboard-intro">
              <div>
                <span className="eyebrow">BTC paper trading · $10,000 starting balance</span>
                <h1>Trade the move.<br />Risk only the lesson.</h1>
              </div>
              <p>
                Market execution, 1–100× leverage, and account-wide cross
                margin. Every position and realized result is saved to your
                wallet profile.
              </p>
            </section>
            <TradingWorkspace
              key={user.userId}
              expectedUserId={user.userId}
              onSessionExpired={handleSessionExpired}
            />
          </>
        ) : (
          <>
            <section className="intro">
              <div>
                <span className="eyebrow">Market sandbox · zero capital at risk</span>
                <h1>Make the call.<br />Keep the lesson.</h1>
              </div>
              <p>
                A focused paper-trading workspace for testing an idea against
                live BTC market data—without putting real funds on the line.
              </p>
            </section>

            <section className="workspace">
              <aside className="side-panel">
                <div>
                  <span className="panel-label">Session</span>
                  <h2>Wallet required</h2>
                  <p>
                    Sign a one-time login message in MetaMask. This proves
                    wallet ownership without moving funds.
                  </p>
                </div>

                <div className="data-source">
                  <span className="source-icon" aria-hidden="true">B</span>
                  <div>
                    <small>Market source</small>
                    <strong>Binance USDⓈ-M Futures</strong>
                  </div>
                  <span className="source-status">Stored history</span>
                </div>
              </aside>
            </section>
            <LockedBtcChart />
          </>
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
