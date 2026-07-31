import { useEffect, useState } from 'react'
import { DEFAULT_PREFERENCES, STORAGE_KEYS } from '../config/appConfig'
import { readStorage, writeStorage } from '../utils/storage'

export function useThemePreferences() {
  const [preferences, setPreferences] = useState(() => ({
    theme: readStorage(STORAGE_KEYS.theme, DEFAULT_PREFERENCES.theme),
    accent: readStorage(STORAGE_KEYS.accent, DEFAULT_PREFERENCES.accent),
    density: readStorage(STORAGE_KEYS.density, DEFAULT_PREFERENCES.density),
    logo: readStorage(STORAGE_KEYS.logo, DEFAULT_PREFERENCES.logo),
    favicon: readStorage(STORAGE_KEYS.favicon, DEFAULT_PREFERENCES.favicon),
  }))

  useEffect(() => {
    const applyTheme = () => {
      const resolved = preferences.theme === 'system'
        ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
        : preferences.theme
      document.documentElement.dataset.theme = resolved
      document.documentElement.dataset.density = preferences.density
      document.documentElement.style.setProperty('--brand', preferences.accent)
    }
    applyTheme()
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    media.addEventListener?.('change', applyTheme)
    return () => media.removeEventListener?.('change', applyTheme)
  }, [preferences])

  useEffect(() => {
    const favicon = document.querySelector('link[rel~="icon"]') || document.createElement('link')
    favicon.rel = 'icon'
    favicon.href = preferences.favicon || preferences.logo || '/favicon.svg'
    if (preferences.favicon || preferences.logo) {
      favicon.removeAttribute('type')
    } else {
      favicon.type = 'image/svg+xml'
    }
    if (!favicon.parentNode) {
      document.head.appendChild(favicon)
    }
  }, [preferences.favicon, preferences.logo])

  const updatePreferences = (next) => {
    const merged = { ...preferences, ...next }
    setPreferences(merged)
    writeStorage(STORAGE_KEYS.theme, merged.theme)
    writeStorage(STORAGE_KEYS.accent, merged.accent)
    writeStorage(STORAGE_KEYS.density, merged.density)
    writeStorage(STORAGE_KEYS.logo, merged.logo)
    writeStorage(STORAGE_KEYS.favicon, merged.favicon)
  }

  return [preferences, updatePreferences]
}
