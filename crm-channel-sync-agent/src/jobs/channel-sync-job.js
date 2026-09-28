const { openBrowserContext } = require('../browser/browser-context')
const { exportSmartSheet } = require('../browser/wecom-exporter')
const { uploadChannelFile } = require('../crm/crm-client')
const { calculateExcelContentHash } = require('../excel/excel-content-hash')

async function runChannelSync(config, logger, stateStore, triggerType = 'MANUAL') {
  if (!config.crmApi?.enabled) {
    throw new Error('CRM上传接口尚未启用，停止同步以避免错误推进本地状态')
  }
  const tokenName = config.crmApi.tokenEnv
  if (!tokenName || !String(process.env[tokenName] || '').trim()) {
    throw new Error(`环境变量${tokenName || 'CRM_CHANNEL_SYNC_INTERNAL_TOKEN'}尚未配置`)
  }
  const sources = config.sources.filter((source) => source.enabled !== false)
  const summary = { total: sources.length, success: 0, skipped: 0, failed: 0 }
  logger.info('渠道表格同步开始', { triggerType, sourceCount: sources.length })
  for (const source of sources) {
    try {
      const result = await syncSource(config, source, logger, stateStore)
      if (result.skipped) summary.skipped += 1
      else summary.success += 1
    } catch (error) {
      summary.failed += 1
      stateStore.markFailure(source.id, error)
      logger.error('渠道表格同步失败', { source: source.name, error })
    }
  }
  logger.info('渠道表格同步结束', summary)
  if (summary.failed > 0) {
    throw new Error(`本轮有${summary.failed}个渠道来源同步失败`)
  }
  return summary
}

async function syncSource(config, source, logger, stateStore) {
  const filePath = await downloadSmartSheet(config, source, logger)
  const contentHash = await calculateExcelContentHash(filePath)
  const previous = stateStore.source(source.id)
  if (previous?.contentHash === contentHash) {
    logger.info('表格业务内容没有变化，跳过CRM上传', { source: source.name })
    return { skipped: true, filePath, contentHash }
  }
  const result = await uploadWithRetry(config, source, filePath, logger)
  stateStore.markSuccess(source.id, {
    contentHash,
    filePath,
    result,
    lastError: '',
  })
  logger.info('渠道表格同步成功', {
    source: source.name,
    fetchedCount: result.fetchedCount || 0,
    createdCount: result.createdCount || 0,
    updatedCount: result.updatedCount || 0,
    skippedCount: result.skippedCount || 0,
    failedCount: result.failedCount || 0,
  })
  return { skipped: false, filePath, contentHash, result }
}

async function downloadSmartSheet(config, source, logger, dependencies = {}) {
  const openContext = dependencies.openBrowserContext || openBrowserContext
  const exportSheet = dependencies.exportSmartSheet || exportSmartSheet
  const wait = dependencies.wait || waitForTimeout
  const maxAttempts = Math.max(1, Number(config.export?.maxAttempts || 3))
  const retryDelayMs = Math.max(0, Number(config.export?.retryDelayMs || 1500))
  let lastError
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    let context
    try {
      context = await openContext(config)
      return await exportSheet(context, config, source, logger)
    } catch (error) {
      lastError = error
      if (attempt >= maxAttempts || !isRecoverableBrowserError(error)) {
        throw error
      }
      logger.warn('企微导出浏览器异常，准备重建浏览器后重试', {
        source: source.name,
        attempt,
        maxAttempts,
        error: error.message,
      })
    } finally {
      await context?.close().catch(() => {})
    }
    await wait(retryDelayMs)
  }
  throw lastError
}

async function uploadWithRetry(config, source, filePath, logger, dependencies = {}) {
  const upload = dependencies.uploadChannelFile || uploadChannelFile
  const wait = dependencies.wait || waitForTimeout
  const maxAttempts = Math.max(1, Number(config.crmApi?.maxAttempts || 3))
  const retryDelayMs = Math.max(0, Number(config.crmApi?.retryDelayMs || 3000))
  let lastError
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      return await upload(config, source, filePath)
    } catch (error) {
      lastError = error
      if (attempt >= maxAttempts) throw error
      logger.warn('CRM渠道导入失败，等待后重试', {
        source: source.name,
        attempt,
        maxAttempts,
        error: error.message,
      })
      await wait(retryDelayMs * attempt)
    }
  }
  throw lastError
}

function isRecoverableBrowserError(error) {
  const message = String(error?.message || error)
  return /Target page, context or browser has been closed/i.test(message)
    || /download\.saveAs/i.test(message)
    || /Download.*(?:canceled|failed)/i.test(message)
}

function waitForTimeout(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

module.exports = {
  downloadSmartSheet,
  isRecoverableBrowserError,
  runChannelSync,
  syncSource,
  uploadWithRetry,
}
