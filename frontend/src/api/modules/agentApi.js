import { request } from '../httpClient'

export const agentApi = {
  page: (payload) => request('/api/agent/page', { method: 'POST', body: JSON.stringify(payload || {}) }),
  save: (payload) => request('/api/agent/save', { method: 'POST', body: JSON.stringify(payload) }),
  saveMcp: (payload) => request('/api/agent/mcp/save', { method: 'POST', body: JSON.stringify(payload) }),
  saveSkill: (payload) => request('/api/agent/skill/save', { method: 'POST', body: JSON.stringify(payload) }),
}
