const fs = require('fs')

async function uploadChannelFile(config, source, filePath) {
  const api = config.crmApi
  if (!api?.enabled) {
    throw new Error('CRM上传接口尚未启用')
  }
  const token = String(process.env[api.tokenEnv] || '').trim()
  if (!token) {
    throw new Error(`环境变量${api.tokenEnv}尚未配置`)
  }
  const buffer = fs.readFileSync(filePath)
  const form = new FormData()
  form.append('sourceUrl', source.url)
  form.append(
    'file',
    new Blob([buffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }),
    `${source.id}.xlsx`,
  )
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), Number(api.timeoutMs || 120000))
  let response
  try {
    response = await fetch(joinUrl(api.baseUrl, api.importPath), {
      method: 'POST',
      headers: { [api.tokenHeader || 'X-CRM-Channel-Sync-Token']: token },
      body: form,
      signal: controller.signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error(`CRM上传超时，超过${api.timeoutMs || 120000}毫秒`)
    }
    throw new Error(`CRM接口连接失败：${error.message}`)
  } finally {
    clearTimeout(timeout)
  }
  const text = await response.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    throw new Error(`CRM接口返回了非JSON响应，状态码：${response.status}`)
  }
  if (!response.ok || !body?.success) {
    throw new Error(body?.message || `CRM接口处理失败，状态码：${response.status}`)
  }
  return body.data || {}
}

function joinUrl(baseUrl, apiPath) {
  return `${String(baseUrl).replace(/\/$/, '')}/${String(apiPath).replace(/^\//, '')}`
}

module.exports = { joinUrl, uploadChannelFile }
