import { request } from '../httpClient'

export const knowledgeApi = {
  pageDocument: (payload) => request('/api/knowledge/document/page', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  detailDocument: (id) => request('/api/knowledge/document/detail', {
    method: 'POST',
    body: JSON.stringify({ id }),
  }),
  saveDocument: (payload) => request('/api/knowledge/document/save', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  importDocument: (formData) => request('/api/knowledge/document/import', {
    method: 'POST',
    body: formData,
  }),
  ingestDocument: (payload) => request('/api/knowledge/document/ingest', {
    method: 'POST',
    body: JSON.stringify(typeof payload === 'object' ? payload : { id: payload }),
  }),
  ingestTask: (id) => request('/api/knowledge/document/ingest/task', {
    method: 'POST',
    body: JSON.stringify({ id }),
  }),
  search: (payload) => request('/api/knowledge/document/search', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  deleteDocument: (id) => request('/api/knowledge/document/delete', {
    method: 'POST',
    body: JSON.stringify({ id }),
  }),
}
