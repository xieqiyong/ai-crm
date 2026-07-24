import { request } from '../httpClient'

export const assistantApi = {
  analyzeLead: (payload) => request('/api/assistant/lead/analyze', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
}
