import { createContext, useContext } from 'react'
import type { DevUser } from './devUser.ts'

export type AuthContextValue = {
  user: DevUser | null
  signIn: (name: string) => Promise<void>
  signOut: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)

  if (context === null) {
    throw new Error('useAuth must be used inside AuthProvider')
  }

  return context
}
