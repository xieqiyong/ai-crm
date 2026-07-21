const TOKEN_KEY = 'crm.token'
const USER_KEY = 'crm.user'

export function getStoredAuth() {
  const token = localStorage.getItem(TOKEN_KEY)
  const userText = localStorage.getItem(USER_KEY)
  if (!token || !userText) return null
  try {
    return { token, user: JSON.parse(userText) }
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export function saveAuth(payload) {
  localStorage.setItem(TOKEN_KEY, payload.token)
  localStorage.setItem(USER_KEY, JSON.stringify(payload))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

async function request(path, options = {}) {
  const auth = getStoredAuth()
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (auth?.token) headers.Authorization = `Bearer ${auth.token}`
  const response = await fetch(path, { ...options, headers })
  const result = await response.json().catch(() => null)
  if (!result) throw new Error('服务响应格式不正确')
  if (!result.success) throw new Error(result.message || '请求处理失败')
  return result.data
}

export const api = {
  installStatus: () => request('/api/install/status'),
  setupSuperAdmin: (payload) => request('/api/install/setup', { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  me: () => request('/api/auth/me'),
}
