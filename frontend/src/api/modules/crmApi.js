import { request } from '../httpClient'

const pageRequests = new Map()

function page(path, payload) {
  const body = JSON.stringify(payload || {})
  const key = `${path}/page:${body}`
  if (pageRequests.has(key)) {
    return pageRequests.get(key)
  }
  const promise = request(`${path}/page`, { method: 'POST', body }).finally(() => {
    pageRequests.delete(key)
  })
  pageRequests.set(key, promise)
  return promise
}

function detail(path, id) {
  return request(`${path}/detail`, { method: 'POST', body: JSON.stringify({ id }) })
}

function save(path, payload) {
  return request(`${path}/save`, { method: 'POST', body: JSON.stringify(payload) })
}

function remove(path, id) {
  return request(`${path}/delete`, { method: 'POST', body: JSON.stringify({ id }) })
}

export const leadApi = {
  page: (payload) => page('/api/lead', payload),
  detail: (id) => detail('/api/lead', id),
  save: (payload) => save('/api/lead', payload),
  importExcel: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request('/api/lead/import-excel', { method: 'POST', body: form })
  },
  assign: (payload) => request('/api/lead/assign', { method: 'POST', body: JSON.stringify(payload) }),
  convertToCustomer: (payload) => request('/api/lead/convert-to-customer', { method: 'POST', body: JSON.stringify(payload) }),
  delete: (id) => remove('/api/lead', id),
}

export const customerApi = {
  page: (payload) => page('/api/customer', payload),
  industryOptions: () => request('/api/customer/industry-options', { method: 'POST' }),
  detail: (id) => detail('/api/customer', id),
  save: (payload) => save('/api/customer', payload),
  assign: (payload) => request('/api/customer/assign', { method: 'POST', body: JSON.stringify(payload) }),
  delete: (id) => remove('/api/customer', id),
}

export const productApi = {
  page: (payload) => page('/api/product', payload),
  detail: (id) => detail('/api/product', id),
  save: (payload) => save('/api/product', payload),
  delete: (id) => remove('/api/product', id),
}

export const opportunityApi = {
  page: (payload) => page('/api/opportunity', payload),
  detail: (id) => detail('/api/opportunity', id),
  save: (payload) => save('/api/opportunity', payload),
  delete: (id) => remove('/api/opportunity', id),
}

export const followupApi = {
  page: (payload) => page('/api/followup', payload),
  objectPage: (payload) => request('/api/followup/object-page', { method: 'POST', body: JSON.stringify(payload || {}) }),
  targetOptions: (payload) => request('/api/followup/target-options', { method: 'POST', body: JSON.stringify(payload || {}) }),
  detail: (id) => detail('/api/followup', id),
  save: (payload) => save('/api/followup', payload),
  uploadMedia: (followupId, file) => {
    const form = new FormData()
    form.append('followupId', followupId)
    form.append('file', file)
    return request('/api/followup/media/upload', { method: 'POST', body: form })
  },
  mediaList: (followupId) => request('/api/followup/media/list', { method: 'POST', body: JSON.stringify({ id: followupId }) }),
  delete: (id) => remove('/api/followup', id),
}

export const taskApi = {
  page: (payload) => page('/api/task', payload),
  detail: (id) => detail('/api/task', id),
  targetOptions: (payload) => request('/api/task/target-options', { method: 'POST', body: JSON.stringify(payload || {}) }),
  save: (payload) => save('/api/task', payload),
  assign: (payload) => request('/api/task/assign', { method: 'POST', body: JSON.stringify(payload) }),
  start: (id) => request('/api/task/start', { method: 'POST', body: JSON.stringify({ id }) }),
  complete: (id) => request('/api/task/complete', { method: 'POST', body: JSON.stringify({ id }) }),
  cancel: (payload) => request('/api/task/cancel', { method: 'POST', body: JSON.stringify(payload) }),
  delete: (id) => remove('/api/task', id),
}
