const { setPath } = require('../excel/license-sheet-parser')

class LicenseClient {
  constructor(config) {
    this.config = config.licenseApi || {}
  }

  async apply(payload, idempotencyKey) {
    if (!this.config.enabled) {
      throw new Error('License接口尚未启用')
    }
    const requestPayload = structuredClone(payload)
    if (this.config.externalRequestIdField) {
      setPath(requestPayload, this.config.externalRequestIdField, idempotencyKey)
    }
    const headers = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    }
    const token = this.config.tokenEnv ? process.env[this.config.tokenEnv] : ''
    if (this.config.tokenEnv && !token) {
      throw new Error(`环境变量${this.config.tokenEnv}未配置`)
    }
    if (token) {
      const tokenHeader = this.config.tokenHeader || 'Authorization'
      const authScheme = Object.prototype.hasOwnProperty.call(this.config, 'authScheme')
        ? String(this.config.authScheme || '')
        : 'Bearer'
      headers[tokenHeader] = `${authScheme} ${token}`.trim()
    }
    if (this.config.idempotencyHeader) {
      headers[this.config.idempotencyHeader] = idempotencyKey
    }
    const url = joinUrl(this.config.baseUrl, this.config.applyPath)
    const response = await fetch(url, {
      method: String(this.config.method || 'POST').toUpperCase(),
      headers,
      body: JSON.stringify(requestPayload),
      signal: AbortSignal.timeout(Number(this.config.timeoutMs || 30000)),
    })
    const text = await response.text()
    const body = parseBody(text)
    if (!response.ok) {
      throw new Error(`License接口响应失败，状态码${response.status}，响应：${shrink(text, 1000)}`)
    }
    this.validateBusinessSuccess(body)
    return {
      applicationNo: getPath(body, this.config.applicationNoPath),
      response: body,
    }
  }

  validateBusinessSuccess(body) {
    if (!this.config.successField) return
    const value = getPath(body, this.config.successField)
    const allowed = this.config.successValues || []
    const matched = allowed.some((item) => String(item) === String(value))
    if (!matched) {
      throw new Error(`License接口业务处理失败，${this.config.successField}=${String(value)}`)
    }
  }
}

function joinUrl(baseUrl, requestPath) {
  return `${String(baseUrl || '').replace(/\/$/, '')}/${String(requestPath || '').replace(/^\//, '')}`
}

function getPath(value, targetPath) {
  if (!targetPath) return ''
  return String(targetPath).split('.').filter(Boolean).reduce((current, key) => (
    current && Object.prototype.hasOwnProperty.call(current, key) ? current[key] : undefined
  ), value)
}

function parseBody(text) {
  if (!text) return {}
  try {
    return JSON.parse(text)
  } catch {
    return { rawText: text }
  }
}

function shrink(value, maxLength) {
  const text = String(value || '')
  return text.length > maxLength ? text.slice(0, maxLength) : text
}

module.exports = { LicenseClient }
