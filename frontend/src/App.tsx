import { useCallback, useEffect, useState } from 'react'
import {
  ApiRequestError,
  clearCsrfCredentials,
  createAuthenticatedSession,
  createLoginChallenge,
  deleteAuthenticatedSession,
  getAuthenticatedUser,
  initializeCsrf,
  type AuthenticatedUser,
} from './api/auth.ts'
import { connectMetaMask, signMetaMaskMessage } from './auth/metamask.ts'

type ScreenState =
  | { kind: 'checking' }
  | { kind: 'signedOut'; error: string | null }
  | { kind: 'signedIn'; user: AuthenticatedUser; error: string | null }
  | { kind: 'sessionError'; error: string }

function requireError(error: unknown): Error {
  if (!(error instanceof Error)) {
    throw error
  }

  return error
}

function App() {
  const [state, setState] = useState<ScreenState>({ kind: 'checking' })
  const [isSubmitting, setIsSubmitting] = useState(false)

  const checkSession = useCallback(async (signal?: AbortSignal) => {
    setState({ kind: 'checking' })

    try {
      const user = await getAuthenticatedUser(signal)
      await initializeCsrf(signal)

      if (signal?.aborted !== true) {
        setState({ kind: 'signedIn', user, error: null })
      }
    } catch (error) {
      if (signal?.aborted === true) {
        return
      }

      if (error instanceof ApiRequestError && error.status === 401) {
        clearCsrfCredentials()
        setState({ kind: 'signedOut', error: null })
        return
      }

      clearCsrfCredentials()
      setState({ kind: 'sessionError', error: requireError(error).message })
    }
  }, [])

  useEffect(() => {
    const abortController = new AbortController()

    void checkSession(abortController.signal)
    return () => abortController.abort()
  }, [checkSession])

  async function authenticate() {
    setIsSubmitting(true)
    setState({ kind: 'signedOut', error: null })
    let sessionExchangeStarted = false

    try {
      const wallet = await connectMetaMask()
      const challenge = await createLoginChallenge(wallet.walletAddress, wallet.chainId)
      const signature = await signMetaMaskMessage(
        wallet.provider,
        wallet.walletAddress,
        challenge.message,
      )
      sessionExchangeStarted = true
      const user = await createAuthenticatedSession(challenge.nonce, signature)
      await initializeCsrf()
      setState({ kind: 'signedIn', user, error: null })
    } catch (error) {
      const message = requireError(error).message

      if (error instanceof ApiRequestError && error.status === 401) {
        clearCsrfCredentials()
        setState({ kind: 'signedOut', error: message })
      } else if (sessionExchangeStarted) {
        clearCsrfCredentials()
        setState({ kind: 'sessionError', error: message })
      } else {
        setState({ kind: 'signedOut', error: message })
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  async function signOut(user: AuthenticatedUser) {
    setIsSubmitting(true)
    setState({ kind: 'signedIn', user, error: null })

    try {
      await deleteAuthenticatedSession()
      setState({ kind: 'signedOut', error: null })
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) {
        clearCsrfCredentials()
        setState({ kind: 'signedOut', error: null })
      } else {
        setState({ kind: 'signedIn', user, error: requireError(error).message })
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main>
      <h1>Wallet authentication</h1>

      {state.kind === 'checking' && <p role="status">Checking session...</p>}

      {state.kind === 'signedOut' && (
        <section>
          <button
            type="button"
            aria-busy={isSubmitting}
            disabled={isSubmitting}
            onClick={() => void authenticate()}
          >
            {isSubmitting ? 'Waiting for MetaMask...' : 'Authenticate with MetaMask'}
          </button>
          {state.error && <p className="error" role="alert">{state.error}</p>}
        </section>
      )}

      {state.kind === 'signedIn' && (
        <section aria-live="polite">
          <p>Authenticated wallet:</p>
          <code>{state.user.walletAddress}</code>
          <button
            type="button"
            aria-busy={isSubmitting}
            disabled={isSubmitting}
            onClick={() => void signOut(state.user)}
          >
            {isSubmitting ? 'Signing out...' : 'Sign out'}
          </button>
          {state.error && <p className="error" role="alert">{state.error}</p>}
        </section>
      )}

      {state.kind === 'sessionError' && (
        <section>
          <p className="error" role="alert">{state.error}</p>
          <button
            type="button"
            aria-busy={isSubmitting}
            disabled={isSubmitting}
            onClick={() => void checkSession()}
          >
            Retry
          </button>
        </section>
      )}
    </main>
  )
}

export default App
