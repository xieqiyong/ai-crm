const fs = require('fs')

const { openBrowserContext } = require('../browser/browser-context')
const { exportSmartSheet } = require('../browser/wecom-exporter')
const { sha256Buffer } = require('../core/files')
const { buildLicensePayload, parseLicenseSheet } = require('../excel/license-sheet-parser')
const { SourceRowRepository, SyncRepository, LicenseOutboxRepository } = require('../persistence/repositories')
const { processLicenseOutbox } = require('./license-outbox-job')

async function runSheetSync(database, config, logger, triggerType = 'MANUAL', options = {}) {
  const baseline = Boolean(options.baseline)
  const syncRepository = new SyncRepository(database)
  const sourceRepository = new SourceRowRepository(database)
  const outboxRepository = new LicenseOutboxRepository(database)
  const runId = syncRepository.startRun(config.source.id, triggerType)
  const counters = {
    totalCount: 0,
    createdCount: 0,
    changedCount: 0,
    skippedCount: 0,
    invalidCount: 0,
    baselinedCount: 0,
  }
  let filePath
  let fileHash
  try {
    filePath = await downloadSmartSheet(config, logger)
    const buffer = fs.readFileSync(filePath)
    fileHash = sha256Buffer(buffer)
    const parsed = await parseLicenseSheet(filePath, config)
    counters.totalCount = parsed.records.length
    logger.info('Excel解析完成', {
      worksheet: parsed.worksheetName,
      headerRow: parsed.headerRowNumber,
      totalCount: parsed.records.length,
    })

    for (const record of parsed.records) {
      const result = sourceRepository.ingest(config.source.id, record, { baseline })
      if (result.action === 'CREATED') counters.createdCount += 1
      else if (result.action === 'CHANGED') counters.changedCount += 1
      else if (result.action === 'INVALID') counters.invalidCount += 1
      else if (result.action === 'BASELINED') counters.baselinedCount += 1
      else counters.skippedCount += 1
    }

    if (config.licenseApi?.enabled && !baseline) {
      const waitingRows = sourceRepository.rowsWaitingForOutbox(config.source.id)
      for (const row of waitingRows) {
        const raw = JSON.parse(row.raw_json)
        const payload = buildLicensePayload(
          raw,
          config.excel.requestFieldMapping,
          config.excel.requestStaticFields,
          config.excel.requestJsonFields,
        )
        const idempotencyKey = `wecom-license-${config.source.id}-${row.row_key}`
        outboxRepository.enqueue(row.id, idempotencyKey, payload)
      }
      logger.info('License待申请任务准备完成', { pendingCount: waitingRows.length })
    }

    const licenseResult = baseline
      ? { successCount: 0, failedCount: 0 }
      : await processLicenseOutbox(database, config, logger)
    syncRepository.finishRun(runId, 'SUCCESS', {
      ...counters,
      filePath,
      fileHash,
    })
    logger.info('企微License表格同步完成', { runId, ...counters, ...licenseResult })
    return { runId, ...counters, ...licenseResult, filePath, fileHash }
  } catch (error) {
    syncRepository.finishRun(runId, 'FAILED', {
      ...counters,
      filePath,
      fileHash,
      errorMessage: error.message,
    })
    logger.error('企微License表格同步失败', { runId, error })
    throw error
  }
}

async function downloadSmartSheet(config, logger, dependencies = {}) {
  const openContext = dependencies.openBrowserContext || openBrowserContext
  const exportSheet = dependencies.exportSmartSheet || exportSmartSheet
  const wait = dependencies.wait || waitForTimeout
  const maxAttempts = Math.max(1, Number(config.export?.maxAttempts || 3))
  const retryDelayMs = Math.max(0, Number(config.export?.retryDelayMs || 1500))
  let lastError

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    let browserContext
    try {
      browserContext = await openContext(config)
      return await exportSheet(browserContext, config, logger)
    } catch (error) {
      lastError = error
      if (attempt >= maxAttempts || !isRecoverableBrowserError(error)) {
        throw error
      }
      logger.warn('企微导出浏览器异常，准备重新打开页面重试', {
        attempt,
        maxAttempts,
        error: error.message,
      })
    } finally {
      await browserContext?.close().catch(() => {})
    }
    await wait(retryDelayMs)
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

module.exports = { downloadSmartSheet, isRecoverableBrowserError, runSheetSync }
