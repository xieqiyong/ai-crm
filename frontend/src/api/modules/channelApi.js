import { request } from '../httpClient'

export const channelApi = {
  page: (payload) => request('/api/channel/page', { method: 'POST', body: JSON.stringify(payload || {}) }),
  save: (payload) => request('/api/channel/save', { method: 'POST', body: JSON.stringify(payload) }),
  importMedia: (formData) => request('/api/channel/media/import', { method: 'POST', body: formData }),
  prepareTranscription: (id) => request('/api/channel/transcription/prepare', { method: 'POST', body: JSON.stringify({ id }) }),
  prepareAnalysis: (id) => request('/api/channel/analysis/prepare', { method: 'POST', body: JSON.stringify({ id }) }),
  promote: (payload) => request('/api/channel/promote', { method: 'POST', body: JSON.stringify(payload) }),
}
