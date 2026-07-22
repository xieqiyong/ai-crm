import { STORAGE_KEYS } from '../config/appConfig'
import { parseJsonPreservingLargeIntegers } from '../utils/json'
import { removeStorage } from '../utils/storage'

export function getStoredAuth() {
  const token = localStorage.getItem(STORAGE_KEYS.token)
  const userText = localStorage.getItem(STORAGE_KEYS.user)
  if (!token || !userText) return null
  try {
    return { token, user: parseJsonPreservingLargeIntegers(userText) }
  } catch {
    removeStorage(STORAGE_KEYS.user)
    return null
  }
}

export function saveAuth(payload) {
  localStorage.setItem(STORAGE_KEYS.token, payload.token)
  localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(payload))
}

export function clearAuth() {
  removeStorage(STORAGE_KEYS.token)
  removeStorage(STORAGE_KEYS.user)
}
