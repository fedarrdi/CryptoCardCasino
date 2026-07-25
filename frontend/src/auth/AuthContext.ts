import { createContext, useContext } from 'react'
import type { AuthenticatedUser } from '../api/auth.ts'

export type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated'

export type AuthContextValue = {
  user: AuthenticatedUser | null
  status: AuthStatus
  connectWallet: () => Promise<void>
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
