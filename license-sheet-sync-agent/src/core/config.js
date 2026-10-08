const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', '..')

function loadConfig() {
  const configPath = path.resolve(process.env.LICENSE_SYNC_CONFIG || path.join(ROOT, 'config.json'))
  if (!fs.existsSync(configPath)) {
    throw new Error(`配置文件不存在：${configPath}，请复制config.example.json为config.json`)
  }
  const config = applyEnvironmentOverrides(JSON.parse(fs.readFileSync(configPath, 'utf8')))
  validateConfig(config)
  const dataDir = path.resolve(ROOT, config.dataDir || './data')
  return {
    ...config,
    configPath,
    rootDir: ROOT,
    dataDir,
    profileDir: path.join(dataDir, 'browser-profile'),
    browserDownloadDir: path.join(dataDir, 'browser-downloads'),
    downloadDir: path.join(dataDir, 'downloads'),
    databasePath: path.join(dataDir, 'license-sync.db'),
    logPath: path.join(dataDir, 'logs', 'sync.log'),
    lockPath: path.join(dataDir, 'sync.lock'),
  }
}

function applyEnvironmentOverrides(config) {
  const licenseApi = { ...(config.licenseApi || {}) }
  if (hasEnvironmentValue('LICENSE_API_ENABLED')) {
    licenseApi.enabled = parseBooleanEnvironment('LICENSE_API_ENABLED')
  }
  if (hasEnvironmentValue('LICENSE_API_BASE_URL')) {
    licenseApi.baseUrl = process.env.LICENSE_API_BASE_URL.trim()
  }
  if (hasEnvironmentValue('LICENSE_API_APPLY_PATH')) {
    licenseApi.applyPath = process.env.LICENSE_API_APPLY_PATH.trim()
  }
  if (hasEnvironmentValue('LICENSE_API_TIMEOUT_MS')) {
    const timeoutMs = Number(process.env.LICENSE_API_TIMEOUT_MS)
    if (!Number.isInteger(timeoutMs) || timeoutMs <= 0) {
      throw new Error('环境变量LICENSE_API_TIMEOUT_MS必须是正整数')
    }
    licenseApi.timeoutMs = timeoutMs
  }
  return { ...config, licenseApi }
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

function validateConfig(config) {
  if (!config?.source?.id || !config?.source?.url) {
    throw new Error('配置缺少source.id或source.url')
  }
  let url
  try {
    url = new URL(config.source.url)
  } catch {
    throw new Error('source.url不是合法链接')
  }
  if (url.hostname !== 'doc.weixin.qq.com' || !url.pathname.includes('/smartsheet/')) {
    throw new Error('source.url必须是企微智能表格链接')
  }
  if (!url.searchParams.get('tab')) {
    throw new Error('source.url缺少tab参数')
  }
  const identitySelectors = config?.excel?.identitySelectors
  if (!Array.isArray(identitySelectors) || !identitySelectors.length) {
    throw new Error('必须配置excel.identitySelectors，禁止在没有稳定身份键时自动申请License')
  }
  if (config?.licenseApi?.enabled) {
    if (!config.licenseApi.baseUrl || !config.licenseApi.applyPath) {
      throw new Error('License接口已启用，但baseUrl或applyPath未配置')
    }
    if (!Object.keys(config?.excel?.requestFieldMapping || {}).length) {
      throw new Error('License接口已启用，但excel.requestFieldMapping尚未配置')
    }
  }
}

module.exports = { applyEnvironmentOverrides, loadConfig }
