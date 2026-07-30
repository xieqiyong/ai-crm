import { request } from '../httpClient'

export const authApi = {
  login: (payload) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  forgotPassword: (payload) => request('/api/auth/forgot-password', { method: 'POST', body: JSON.stringify(payload) }),
  resetPassword: (payload) => request('/api/auth/reset-password', { method: 'POST', body: JSON.stringify(payload) }),
  changePassword: (payload) => request('/api/auth/change-password', { method: 'POST', body: JSON.stringify(payload) }),
  revokeOtherSessions: () => request('/api/auth/sessions/revoke-other', { method: 'POST' }),
  me: () => request('/api/auth/me', { method: 'POST' }),
  userOptions: () => request('/api/auth/users/options', { method: 'POST' }),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
}
