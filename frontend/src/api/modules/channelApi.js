import { request } from '../httpClient'

export const channelApi = {
  page: (payload) => request('/api/channel/page', { method: 'POST', body: JSON.stringify(payload || {}) }),
  detail: (id) => request('/api/channel/detail', { method: 'POST', body: JSON.stringify({ id }) }),
  save: (payload) => request('/api/channel/save', { method: 'POST', body: JSON.stringify(payload) }),
  delete: (id) => request('/api/channel/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  importMedia: (formData) => request('/api/channel/media/import', { method: 'POST', body: formData }),
  importDocument: (formData) => request('/api/channel/document/import', { method: 'POST', body: formData }),
  prepareTranscription: (id) => request('/api/channel/transcription/prepare', {
    method: 'POST',
    body: JSON.stringify({ id }),
  }),
  prepareAnalysis: (id) => request('/api/channel/analysis/prepare', { method: 'POST', body: JSON.stringify({ id }) }),
  promote: (payload) => request('/api/channel/promote', { method: 'POST', body: JSON.stringify(payload) }),
  formPage: (payload) => request('/api/channel/marketing-form/page', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  formDetail: (id) => request('/api/channel/marketing-form/detail', { method: 'POST', body: JSON.stringify({ id }) }),
  formSave: (payload) => request('/api/channel/marketing-form/save', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  formDelete: (id) => request('/api/channel/marketing-form/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  publicFormDetail: (formCode) => request('/api/public/marketing-form/detail', {
    method: 'POST',
    body: JSON.stringify({ formCode }),
  }),
  publicFormSubmit: (payload) => request('/api/public/marketing-form/submit', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
}
