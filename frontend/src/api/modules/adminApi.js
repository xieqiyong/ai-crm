import { request } from '../httpClient'

export const adminApi = {
  overview: () => request('/api/admin/security/overview', { method: 'POST' }),
  saveDepartment: (payload) => request('/api/admin/security/departments/save', { method: 'POST', body: JSON.stringify(payload) }),
  deleteDepartment: (id) => request('/api/admin/security/departments/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  savePermission: (payload) => request('/api/admin/security/permissions/save', { method: 'POST', body: JSON.stringify(payload) }),
  updatePermissionStatus: (id, enabled) => request('/api/admin/security/permissions/status', { method: 'POST', body: JSON.stringify({ id, enabled }) }),
  deletePermission: (id) => request('/api/admin/security/permissions/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  saveRole: (payload) => request('/api/admin/security/roles/save', { method: 'POST', body: JSON.stringify(payload) }),
  deleteRole: (id) => request('/api/admin/security/roles/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  saveUser: (payload) => request('/api/admin/security/users/save', { method: 'POST', body: JSON.stringify(payload) }),
  updateUserStatus: (id, enabled) => request('/api/admin/security/users/status', { method: 'POST', body: JSON.stringify({ id, enabled }) }),
  resetUserPassword: (id) => request('/api/admin/security/users/reset-password', { method: 'POST', body: JSON.stringify({ id }) }),
}

export const modelConfigApi = {
  list: () => request('/api/admin/model-configs/list', { method: 'POST' }),
  save: (payload) => request('/api/admin/model-configs/save', { method: 'POST', body: JSON.stringify(payload) }),
  delete: (id) => request('/api/admin/model-configs/delete', { method: 'POST', body: JSON.stringify({ id }) }),
  setDefault: (id) => request('/api/admin/model-configs/default', { method: 'POST', body: JSON.stringify({ id }) }),
  status: (id) => request('/api/admin/model-configs/status', { method: 'POST', body: JSON.stringify({ id }) }),
  debug: (payload) => request('/api/admin/model-configs/debug', { method: 'POST', body: JSON.stringify(payload) }),
}
