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

export async function streamRequest(path, options = {}, handlers = {}) {
  const auth = getStoredAuth()
  const headers = { ...(options.headers || {}) }
  const hasBody = Object.prototype.hasOwnProperty.call(options, 'body')
  if (hasBody && !(options.body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] || 'application/json'
  }
  headers.Accept = headers.Accept || 'text/event-stream'
  if (auth?.token) headers.Authorization = `Bearer ${auth.token}`

  const response = await fetch(appendQuery(joinUrl(path), options.params), {
    ...options,
    headers,
  })
  if (response.status === 401) {
    clearAuth()
    throw new Error('登录已失效，请重新登录')
  }
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    const result = parseMaybeJson(text)
    throw new Error(result?.message || `请求处理失败：${response.status}`)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let lastPayload = null
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/)
    buffer = parts.pop() || ''
    for (const part of parts) {
      const event = parseSseEvent(part)
      if (!event) continue
      lastPayload = event.payload
      dispatchStreamEvent(event, handlers)
    }
  }
  if (buffer.trim()) {
    const event = parseSseEvent(buffer)
    if (event) {
      lastPayload = event.payload
      dispatchStreamEvent(event, handlers)
    }
  }
  return lastPayload
}

function dispatchStreamEvent(event, handlers) {
  const payload = event?.payload
  const name = event?.name || 'message'
  const type = String(payload?.type || name || '').toUpperCase()
  handlers.onEvent?.(name, payload)
  handlers.onRuntimeEvent?.(payload)
  if (name === 'thought' || type === 'THOUGHT') handlers.onThought?.(payload)
  if (name === 'delta' || type === 'ANSWER_DELTA') handlers.onDelta?.(payload)
  if (name === 'done' || type === 'RUN_FINISHED') handlers.onDone?.(payload)
  if (type === 'ANSWER_FINISHED') handlers.onAnswerFinished?.(payload)
  if (type === 'RUN_ERROR') handlers.onErrorEvent?.(payload)
}

function parseMaybeJson(text) {
  try {
    return parseJsonPreservingLargeIntegers(text)
  } catch {
    return null
  }
}

function parseSseEvent(part) {
  const lines = String(part || '').split(/\r?\n/)
  let name = 'message'
  const data = []
  lines.forEach((line) => {
    if (line.startsWith('event:')) {
      name = line.slice(6).trim() || 'message'
    }
    if (line.startsWith('data:')) {
      data.push(line.slice(5).trimStart())
    }
  })
  if (!data.length) return null
  const text = data.join('\n')
  return {
    name,
    payload: parseMaybeJson(text) || text,
  }
}
