import { request } from '../httpClient'

export const notificationApi = {
  page: (payload) => request('/api/notifications/page', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  unreadCount: () => request('/api/notifications/unread-count', { method: 'POST' }),
  recipients: () => request('/api/notifications/recipients', { method: 'POST' }),
  read: (id) => request('/api/notifications/read', {
    method: 'POST',
    body: JSON.stringify({ id }),
  }),
  readAll: () => request('/api/notifications/read-all', { method: 'POST' }),
  send: (payload) => request('/api/notifications/send', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
}
