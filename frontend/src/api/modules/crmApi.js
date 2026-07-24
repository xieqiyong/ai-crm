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
  convertToCustomer: (payload) => request('/api/lead/convert-to-customer', { method: 'POST', body: JSON.stringify(payload) }),
  delete: (id) => remove('/api/lead', id),
}

export const customerApi = {
  page: (payload) => page('/api/customer', payload),
  detail: (id) => detail('/api/customer', id),
  save: (payload) => save('/api/customer', payload),
  delete: (id) => remove('/api/customer', id),
}

export const opportunityApi = {
  page: (payload) => page('/api/opportunity', payload),
  detail: (id) => detail('/api/opportunity', id),
  save: (payload) => save('/api/opportunity', payload),
  delete: (id) => remove('/api/opportunity', id),
}
