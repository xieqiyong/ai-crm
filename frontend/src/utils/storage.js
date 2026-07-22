export function readStorage(key, fallback = '') {
  try {
    const value = localStorage.getItem(key)
    return value === null ? fallback : value
  } catch {
    return fallback
  }
}

export function writeStorage(key, value) {
  try {
    if (value === undefined || value === null || value === '') {
      localStorage.removeItem(key)
      return
    }
    localStorage.setItem(key, value)
  } catch {
    return
  }
}

export function removeStorage(key) {
  try {
    localStorage.removeItem(key)
  } catch {
    return
  }
}
