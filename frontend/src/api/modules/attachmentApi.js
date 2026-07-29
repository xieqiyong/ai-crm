import { request } from '../httpClient'

export const attachmentApi = {
  uploadImage: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request('/api/attachment/upload-image', {
      method: 'POST',
      body: formData,
    })
  },
  uploadFile: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request('/api/attachment/upload-file', {
      method: 'POST',
      body: formData,
    })
  },
}
