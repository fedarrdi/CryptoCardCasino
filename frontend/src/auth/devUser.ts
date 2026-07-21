import type { LoginResponse } from '../api/users.ts'

export type DevUser = LoginResponse

export const DEV_USER_STORAGE_KEY = 'raretable.devUser'

export function getStoredUser(): DevUser | null {
  const storedUser = localStorage.getItem(DEV_USER_STORAGE_KEY)

  if (storedUser === null) {
    return null
  }

  return JSON.parse(storedUser) as DevUser
}

export function storeUser(user: DevUser) {
  localStorage.setItem(DEV_USER_STORAGE_KEY, JSON.stringify(user))
}

export function clearStoredUser() {
  localStorage.removeItem(DEV_USER_STORAGE_KEY)
}
