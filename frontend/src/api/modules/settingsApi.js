import { request } from '../httpClient'

export const settingsApi = {
  followupTaskDetail: () => request('/api/settings/followup-task/detail', { method: 'POST' }),
  saveFollowupTask: (payload) => request('/api/settings/followup-task/save', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
}
