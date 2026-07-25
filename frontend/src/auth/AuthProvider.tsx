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
  clearCsrfCredentials,
  setAuthenticationRequiredHandler,
  setCsrfCredentials,
} from '../api/client.ts'
import { AuthContext, type AuthStatus } from './AuthContext.ts'
import { connectMetaMask, signMetaMaskMessage } from './metamask.ts'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>('checking')

  const clearAuthentication = useCallback(() => {
    clearCsrfCredentials()
    setUser(null)
    setStatus('unauthenticated')
  }, [])

  useEffect(() => {
    setAuthenticationRequiredHandler(clearAuthentication)
    return () => setAuthenticationRequiredHandler(null)
  }, [clearAuthentication])

  useEffect(() => {
    const abortController = new AbortController()

    async function restoreSession() {
      try {
        const authenticatedUser = await getAuthenticatedUser(abortController.signal)
        const csrfCredentials = await getCsrfCredentials(abortController.signal)

        if (!abortController.signal.aborted) {
          setCsrfCredentials(csrfCredentials)
          setUser(authenticatedUser)
          setStatus('authenticated')
        }
      } catch {
        if (!abortController.signal.aborted) {
          clearAuthentication()
        }
      }
    }

    void restoreSession()
    return () => abortController.abort()
  }, [clearAuthentication])

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
    setUser(authenticatedUser)
    setStatus('authenticated')
  }, [])

  const signOut = useCallback(async () => {
    await deleteAuthenticatedSession()
    clearAuthentication()
  }, [clearAuthentication])

  const contextValue = useMemo(
    () => ({ user, status, connectWallet, signOut }),
    [connectWallet, signOut, status, user],
  )

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>
}
