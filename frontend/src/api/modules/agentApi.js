import { request, streamRequest } from '../httpClient'

export const agentApi = {
  page: (payload) => request('/api/agent/page', { method: 'POST', body: JSON.stringify(payload || {}) }),
  detail: (id) => request('/api/agent/detail', { method: 'POST', body: JSON.stringify({ id }) }),
  save: (payload) => request('/api/agent/save', { method: 'POST', body: JSON.stringify(payload) }),
  mcps: (agentId) => request('/api/agent/mcp/list', { method: 'POST', body: JSON.stringify({ agentId }) }),
  saveMcp: (payload) => request('/api/agent/mcp/save', { method: 'POST', body: JSON.stringify(payload) }),
  deleteMcp: (id) => request('/api/agent/mcp/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  skills: (agentId) => request('/api/agent/skill/list', { method: 'POST', body: JSON.stringify({ agentId }) }),
  saveSkill: (payload) => request('/api/agent/skill/save', { method: 'POST', body: JSON.stringify(payload) }),
  deleteSkill: (id) => request('/api/agent/skill/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  tokenToday: () => request('/api/agent/token/today', { method: 'POST' }),
  tokenQuotaOverview: () => request('/api/agent/token/quota/overview', { method: 'POST' }),
  assignTokenQuota: (payload) => request('/api/agent/token/quota/assign', { method: 'POST', body: JSON.stringify(payload) }),
  clearTokenQuota: (userId) => request('/api/agent/token/quota/clear', { method: 'POST', body: JSON.stringify({ userId }) }),
  assistantAgents: () => request('/api/agent-assistant/agents', { method: 'POST' }),
  assistantConversations: (payload) => request('/api/agent-assistant/conversations', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  assistantMessages: (conversationId) => request('/api/agent-assistant/messages', {
    method: 'POST',
    body: JSON.stringify({ conversationId }),
  }),
  assistantDeleteConversation: (conversationId) => request('/api/agent-assistant/conversation/delete', {
    method: 'POST',
    body: JSON.stringify({ conversationId }),
  }),
  assistantStopRun: (requestId) => request('/api/agent-assistant/run/stop', {
    method: 'POST',
    body: JSON.stringify({ requestId }),
  }),
  assistantRunStream: (payload, handlers, options = {}) => streamRequest('/api/agent-assistant/run/stream', {
    ...options,
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }, handlers),
}
