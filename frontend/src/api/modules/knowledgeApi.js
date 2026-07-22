import { request } from '../httpClient'

export const knowledgeApi = {
  pageDocument: (payload) => request('/api/knowledge/document/page', { method: 'POST', body: JSON.stringify(payload || {}) }),
  saveDocument: (payload) => request('/api/knowledge/document/save', { method: 'POST', body: JSON.stringify(payload) }),
}
