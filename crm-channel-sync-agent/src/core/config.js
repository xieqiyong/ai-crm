const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', '..')

function loadConfig() {
  const configPath = path.resolve(
    process.env.CRM_CHANNEL_SYNC_CONFIG || path.join(ROOT, 'config.json'),
  )
  if (!fs.existsSync(configPath)) {
    throw new Error(`配置文件不存在：${configPath}，请复制config.example.json为config.json`)
  }
  const original = JSON.parse(fs.readFileSync(configPath, 'utf8'))
  const config = applyEnvironmentOverrides(original)
  validateConfig(config)
  const dataDir = path.resolve(ROOT, config.dataDir || './data')
  return {
    ...config,
    configPath,
    rootDir: ROOT,
    dataDir,
    profileDir: path.join(dataDir, 'browser-profile'),
    downloadDir: path.join(dataDir, 'downloads'),
    statePath: path.join(dataDir, 'state.json'),
    logPath: path.join(dataDir, 'logs', 'sync.log'),
    lockPath: path.join(dataDir, 'sync.lock'),
  }
}

function applyEnvironmentOverrides(config) {
  const crmApi = { ...(config.crmApi || {}) }
  if (hasEnvironmentValue('CRM_API_ENABLED')) {
    crmApi.enabled = parseBooleanEnvironment('CRM_API_ENABLED')
  }
  if (hasEnvironmentValue('CRM_API_BASE_URL')) {
    crmApi.baseUrl = process.env.CRM_API_BASE_URL.trim()
  }
  if (hasEnvironmentValue('CRM_API_IMPORT_PATH')) {
    crmApi.importPath = process.env.CRM_API_IMPORT_PATH.trim()
  }
  if (hasEnvironmentValue('CRM_API_TIMEOUT_MS')) {
    const timeoutMs = Number(process.env.CRM_API_TIMEOUT_MS)
    if (!Number.isInteger(timeoutMs) || timeoutMs <= 0) {
      throw new Error('环境变量CRM_API_TIMEOUT_MS必须是正整数')
    }
    crmApi.timeoutMs = timeoutMs
  }
  return { ...config, crmApi }
}

function validateConfig(config) {
  if (!Array.isArray(config.sources) || !config.sources.length) {
    throw new Error('配置中至少需要一条sources渠道来源')
  }
  const sourceIds = new Set()
  for (const source of config.sources) {
    if (!source?.id || !source?.name || !source?.url) {
      throw new Error('每条渠道来源都必须配置id、name和url')
    }
    if (sourceIds.has(source.id)) {
      throw new Error(`渠道来源编号重复：${source.id}`)
    }
    sourceIds.add(source.id)
    validateSourceUrl(source.url, source.name)
  }
  if (!Array.isArray(config.schedules) || !config.schedules.length) {
    throw new Error('至少需要配置一条schedules定时表达式')
  }
  if (config.crmApi?.enabled) {
    if (!config.crmApi.baseUrl || !config.crmApi.importPath) {
      throw new Error('CRM接口已启用，但baseUrl或importPath未配置')
    }
    if (!config.crmApi.tokenEnv) {
      throw new Error('CRM接口已启用，但tokenEnv未配置')
    }
  }
}

function validateSourceUrl(value, sourceName) {
  let url
  try {
    url = new URL(value)
  } catch {
    throw new Error(`渠道来源链接不合法：${sourceName}`)
  }
  if (url.hostname !== 'doc.weixin.qq.com' || !url.pathname.includes('/smartsheet/')) {
    throw new Error(`渠道来源必须是企微智能表格链接：${sourceName}`)
  }
  if (!url.searchParams.get('tab')) {
    throw new Error(`渠道来源链接缺少tab参数：${sourceName}`)
  }
}

function hasEnvironmentValue(name) {
  return typeof process.env[name] === 'string' && process.env[name].trim() !== ''
}

function parseBooleanEnvironment(name) {
  const value = process.env[name].trim().toLowerCase()
  if (['true', '1', 'yes', 'on'].includes(value)) return true
  if (['false', '0', 'no', 'off'].includes(value)) return false
  throw new Error(`环境变量${name}必须是true或false`)
}

module.exports = { applyEnvironmentOverrides, loadConfig, validateConfig }
