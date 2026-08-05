import { useState } from 'react'
import { AuthProvider } from './auth/AuthProvider.tsx'
import { useAuth } from './auth/AuthContext.ts'

function AuthenticationScreen() {
  const { user, status, sessionError, connectWallet, retrySession, signOut } = useAuth()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConnect() {
    setError(null)
    setIsSubmitting(true)

    try {
      await connectWallet()
    } catch (connectionError) {
      if (!(connectionError instanceof Error)) {
        throw connectionError
      }

      setError(connectionError.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleSignOut() {
    setError(null)
    setIsSubmitting(true)

    try {
      await signOut()
    } catch (signOutError) {
      if (!(signOutError instanceof Error)) {
        throw signOutError
      }

      setError(signOutError.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleRetrySession() {
    setError(null)
    setIsSubmitting(true)

    try {
      await retrySession()
    } finally {
      setIsSubmitting(false)
    }
  }

  let authenticationPanel

  if (status === 'checking') {
    authenticationPanel = (
      <div className="session-status" role="status">
        <span className="spinner" aria-hidden="true" />
        <span>Checking your session</span>
      </div>
    )
  } else if (status === 'authenticated') {
    if (user === null) {
      throw new Error('An authenticated session must include a user')
    }

    authenticationPanel = (
      <div className="authenticated-panel" role="status">
        <span className="status-badge">
          <span className="status-dot" aria-hidden="true" />
          Authenticated
        </span>
        <div className="wallet-identity">
          <span>Signed in as</span>
          <strong>{user.name}</strong>
          <code>{user.walletAddress}</code>
        </div>
        <button
          className="secondary-button"
          type="button"
          aria-busy={isSubmitting}
          disabled={isSubmitting}
          onClick={() => void handleSignOut()}
        >
          {isSubmitting ? 'Signing out...' : 'Sign out'}
        </button>
      </div>
    )
  } else if (status === 'unauthenticated') {
    authenticationPanel = (
      <div className="connect-panel">
        <button
          className="metamask-button"
          type="button"
          aria-busy={isSubmitting}
          disabled={isSubmitting}
          onClick={() => void handleConnect()}
        >
          <span className="wallet-symbol" aria-hidden="true">M</span>
          {isSubmitting ? 'Waiting for MetaMask...' : 'Continue with MetaMask'}
        </button>
        <p>You will be asked to sign a message. This does not create a blockchain transaction.</p>
      </div>
    )
  } else {
    if (sessionError === null) {
      throw new Error('An authentication error state must include an error')
    }

    authenticationPanel = (
      <div className="session-error" role="alert">
        <strong>Could not verify your session</strong>
        <span>{sessionError.message}</span>
        <button
          className="secondary-button"
          type="button"
          aria-busy={isSubmitting}
          disabled={isSubmitting}
          onClick={() => void handleRetrySession()}
        >
          {isSubmitting ? 'Checking session...' : 'Retry'}
        </button>
      </div>
    )
  }

  return (
    <main className="authentication-page">
      <section className="authentication-card" aria-labelledby="authentication-title">
        <div className="brand-mark" aria-hidden="true">R</div>
        <p className="eyebrow">RareTable access</p>
        <h1 id="authentication-title">Authenticate with your wallet</h1>
        <p className="introduction">
          MetaMask is the only supported authentication method.
        </p>

        {authenticationPanel}
        {error && <p className="error-message" role="alert">{error}</p>}

        <div className="security-note">
          <span aria-hidden="true">&#10003;</span>
          Your private keys never leave MetaMask.
        </div>
      </section>
    </main>
  )
}

function App() {
  return (
    <AuthProvider>
      <AuthenticationScreen />
    </AuthProvider>
  )
}

export default App
