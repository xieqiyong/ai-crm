import { request } from '../httpClient'

export const observabilityApi = {
  pageRequestLog: (payload) => request('/api/observability/request-log/page', { method: 'POST', body: JSON.stringify(payload || {}) }),
  pageAuditLog: (payload) => request('/api/observability/audit-log/page', { method: 'POST', body: JSON.stringify(payload || {}) }),
}
