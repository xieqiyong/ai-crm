import { request } from '../httpClient'

export const workflowApi = {
  start: (payload) => request('/api/workflow/start', { method: 'POST', body: JSON.stringify(payload) }),
}
