import { request } from '../httpClient'

export const authApi = {
  login: (payload) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  forgotPassword: (payload) => request('/api/auth/forgot-password', { method: 'POST', body: JSON.stringify(payload) }),
  resetPassword: (payload) => request('/api/auth/reset-password', { method: 'POST', body: JSON.stringify(payload) }),
  me: () => request('/api/auth/me'),
  userOptions: () => request('/api/auth/users/options', { method: 'POST' }),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
}
