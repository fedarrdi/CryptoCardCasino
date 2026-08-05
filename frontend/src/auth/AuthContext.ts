import { createContext, useContext } from 'react'
import type { AuthenticatedUser } from '../api/auth.ts'

export type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated' | 'error'

export type AuthContextValue = {
  user: AuthenticatedUser | null
  status: AuthStatus
  sessionError: Error | null
  connectWallet: () => Promise<void>
  retrySession: () => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)

  if (context === null) {
    throw new Error('useAuth must be used inside AuthProvider')
  }

  return context
}
