import { request } from '../httpClient'

export const mailApi = {
  account: () => request('/api/tools/mail/account/detail', { method: 'POST' }),
  saveAccount: (payload) => request('/api/tools/mail/account/save', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  customerOptions: () => request('/api/tools/mail/customers/options', { method: 'POST' }),
  send: (payload, files = []) => {
    const formData = new FormData()
    formData.append(
      'request',
      new Blob([JSON.stringify(payload)], { type: 'application/json' }),
    )
    files.forEach((file) => formData.append('files', file))
    return request('/api/tools/mail/send', { method: 'POST', body: formData })
  },
  pageLogs: (payload) => request('/api/tools/mail/logs/page', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  deleteLog: (id) => request('/api/tools/mail/logs/delete', {
    method: 'POST',
    body: JSON.stringify({ id }),
  }),
}
