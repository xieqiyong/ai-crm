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
}
