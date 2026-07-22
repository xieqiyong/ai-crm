import { request } from '../httpClient'

export const dashboardApi = {
  overview: () => request('/api/dashboard/overview', { method: 'POST' }),
}
