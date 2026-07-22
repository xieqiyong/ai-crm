import { request } from '../httpClient'

export const installApi = {
  status: () => request('/api/install/status'),
  setupSuperAdmin: (payload) => request('/api/install/setup', { method: 'POST', body: JSON.stringify(payload) }),
}
