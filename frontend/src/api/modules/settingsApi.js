import { request } from '../httpClient'

export const settingsApi = {
  followupTaskDetail: () => request('/api/settings/followup-task/detail', { method: 'POST' }),
  saveFollowupTask: (payload) => request('/api/settings/followup-task/save', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  messageWebhookDetail: () => request('/api/settings/message-webhook/detail', { method: 'POST' }),
  saveMessageWebhook: (payload) => request('/api/settings/message-webhook/save', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  testMessageWebhook: () => request('/api/settings/message-webhook/test', { method: 'POST' }),
}
