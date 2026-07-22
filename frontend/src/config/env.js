const viteEnv = import.meta.env || {}
const runtimeEnv = typeof window !== 'undefined' && window.__CRM_CONFIG__ ? window.__CRM_CONFIG__ : {}

function trimSlash(value) {
  if (!value) return ''
  return String(value).replace(/\/$/, '')
}

export const runtimeConfig = {
  mode: viteEnv.MODE || runtimeEnv.APP_ENV || 'production',
  appEnv: runtimeEnv.APP_ENV || viteEnv.VITE_APP_ENV || viteEnv.MODE || 'production',
  apiBaseUrl: trimSlash(runtimeEnv.API_BASE_URL || viteEnv.VITE_API_BASE_URL || ''),
  apiTimeout: Number(runtimeEnv.API_TIMEOUT || viteEnv.VITE_API_TIMEOUT || 30000),
}

export function backendAddressLabel() {
  return runtimeConfig.apiBaseUrl || '同源地址 /api'
}
