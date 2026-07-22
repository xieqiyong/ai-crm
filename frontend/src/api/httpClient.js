import { runtimeConfig } from '../config/env'
import { clearAuth, getStoredAuth } from '../store/authStorage'
import { parseJsonPreservingLargeIntegers } from '../utils/json'

function joinUrl(path) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${runtimeConfig.apiBaseUrl}${normalizedPath}`
}

function appendQuery(url, params) {
  if (!params) return url
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, value)
    }
  })
  const text = search.toString()
  return text ? `${url}?${text}` : url
}

export async function request(path, options = {}) {
  const auth = getStoredAuth()
  const headers = { ...(options.headers || {}) }
  const hasBody = Object.prototype.hasOwnProperty.call(options, 'body')
  if (hasBody && !(options.body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] || 'application/json'
  }
  if (auth?.token) headers.Authorization = `Bearer ${auth.token}`

  const response = await fetch(appendQuery(joinUrl(path), options.params), {
    ...options,
    headers,
  })
  if (response.status === 401) {
    clearAuth()
    throw new Error('登录已失效，请重新登录')
  }
  const text = await response.text().catch(() => '')
  const result = (() => {
    try {
      return parseJsonPreservingLargeIntegers(text)
    } catch {
      return null
    }
  })()
  if (!result) throw new Error('服务响应格式不正确')
  if (!result.success) throw new Error(result.message || '请求处理失败')
  return result.data
}
