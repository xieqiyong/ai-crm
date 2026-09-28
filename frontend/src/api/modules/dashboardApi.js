import { request } from '../httpClient'

export const dashboardApi = {
  overview: () => request('/api/dashboard/overview', { method: 'POST' }),
  followupAttentionPage: (payload) => request('/api/dashboard/followup-attention/page', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
}
