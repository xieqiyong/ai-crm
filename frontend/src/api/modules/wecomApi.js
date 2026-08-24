import { request } from '../httpClient'

export const wecomApi = {
  configDetail: () => request('/api/wecom/config/detail', { method: 'POST' }),
  configSave: (payload) => request('/api/wecom/config/save', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  bindingList: (configId) => request('/api/wecom/binding/list', {
    method: 'POST',
    body: JSON.stringify({ configId }),
  }),
  bindingSave: (payload) => request('/api/wecom/binding/save', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  syncLatest: (configId) => request('/api/wecom/sync/latest', {
    method: 'POST',
    body: JSON.stringify({ configId }),
  }),
  syncDetail: (id) => request('/api/wecom/sync/detail', {
    method: 'POST',
    body: JSON.stringify({ id }),
  }),
}
