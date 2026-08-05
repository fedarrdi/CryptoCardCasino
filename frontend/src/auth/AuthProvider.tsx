import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  createAuthenticatedSession,
  createLoginChallenge,
  deleteAuthenticatedSession,
  getAuthenticatedUser,
  getCsrfCredentials,
  type AuthenticatedUser,
} from '../api/auth.ts'
import {
  ApiRequestError,
  clearCsrfCredentials,
  setAuthenticationRequiredHandler,
  setCsrfCredentials,
} from '../api/client.ts'
import { AuthContext, type AuthStatus } from './AuthContext.ts'
import { connectMetaMask, signMetaMaskMessage } from './metamask.ts'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>('checking')
  const [sessionError, setSessionError] = useState<Error | null>(null)

  const clearAuthentication = useCallback(() => {
    clearCsrfCredentials()
    setUser(null)
    setSessionError(null)
    setStatus('unauthenticated')
  }, [])

  useEffect(() => {
    setAuthenticationRequiredHandler(clearAuthentication)
    return () => setAuthenticationRequiredHandler(null)
  }, [clearAuthentication])

  const restoreSession = useCallback(async (signal?: AbortSignal) => {
    setSessionError(null)
    setStatus('checking')

    try {
      const authenticatedUser = await getAuthenticatedUser(signal)
      const csrfCredentials = await getCsrfCredentials(signal)

      if (signal?.aborted !== true) {
        setCsrfCredentials(csrfCredentials)
        setUser(authenticatedUser)
        setStatus('authenticated')
      }
    } catch (restoreError) {
      if (signal?.aborted === true) {
        return
      }

      if (restoreError instanceof ApiRequestError && restoreError.status === 401) {
        clearAuthentication()
        return
      }

      if (!(restoreError instanceof Error)) {
        throw restoreError
      }

      clearCsrfCredentials()
      setUser(null)
      setSessionError(restoreError)
      setStatus('error')
    }
  }, [clearAuthentication])

  useEffect(() => {
    const abortController = new AbortController()

    void restoreSession(abortController.signal)
    return () => abortController.abort()
  }, [restoreSession])

  const connectWallet = useCallback(async () => {
    const wallet = await connectMetaMask()
    const challenge = await createLoginChallenge(wallet.walletAddress, wallet.chainId)
    const signature = await signMetaMaskMessage(
      wallet.provider,
      wallet.walletAddress,
      challenge.message,
    )
    const authenticatedUser = await createAuthenticatedSession(challenge.nonce, signature)
    const csrfCredentials = await getCsrfCredentials()

    setCsrfCredentials(csrfCredentials)
    setSessionError(null)
    setUser(authenticatedUser)
    setStatus('authenticated')
  }, [])

  const retrySession = useCallback(async () => {
    await restoreSession()
  }, [restoreSession])

  const signOut = useCallback(async () => {
    await deleteAuthenticatedSession()
    clearAuthentication()
  }, [clearAuthentication])

  const contextValue = useMemo(
    () => ({ user, status, sessionError, connectWallet, retrySession, signOut }),
    [connectWallet, retrySession, sessionError, signOut, status, user],
  )

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>
}
