import { useState, type ReactNode } from 'react'
import { loginUser } from '../api/users.ts'
import { AuthContext } from './AuthContext.ts'
import {
  clearStoredUser,
  getStoredUser,
  storeUser,
  type DevUser,
} from './devUser.ts'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<DevUser | null>(() => getStoredUser())

  async function signIn(name: string) {
    const signedInUser = await loginUser(name)
    storeUser(signedInUser)
    setUser(signedInUser)
  }

  function signOut() {
    clearStoredUser()
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, signIn, signOut }}>{children}</AuthContext.Provider>
}
