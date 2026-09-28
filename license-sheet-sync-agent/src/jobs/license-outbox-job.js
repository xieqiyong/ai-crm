const { LicenseClient } = require('../license/license-client')
const { setPath } = require('../excel/license-sheet-parser')
const { LicenseOutboxRepository } = require('../persistence/repositories')

async function processLicenseOutbox(database, config, logger, options = {}) {
  if (!config.licenseApi?.enabled) {
    logger.info('License接口尚未启用，本次只完成表格下载、解析和增量识别')
    return { successCount: 0, failedCount: 0 }
  }
  const repository = new LicenseOutboxRepository(database)
  const recoveredCount = repository.recoverStaleSubmitting(
    Number(config.licenseApi.submittingStaleMinutes || 10),
  )
  if (recoveredCount > 0) {
    logger.warn('检测到异常中断的License提交任务，已进入幂等重试', { recoveredCount })
  }
  const client = new LicenseClient(config)
  let successCount = 0
  let failedCount = 0

  while (true) {
    const force = Boolean(options.force)
    const batch = repository.claimBatch(force ? 500 : 20, force)
    if (!batch.length) break
    for (const item of batch) {
      try {
        logger.info('提交License申请', {
          outboxId: item.id,
          attempt: item.attempt_count,
          idempotencyKey: item.idempotency_key,
        })
        const payload = applyRuntimeStaticFields(
          JSON.parse(item.request_json),
          config.excel?.requestStaticFields,
        )
        const result = await client.apply(payload, item.idempotency_key)
        repository.markSuccess(item, result.applicationNo, result.response)
        successCount += 1
        logger.info('License申请成功', {
          outboxId: item.id,
          applicationNo: result.applicationNo || '',
        })
      } catch (error) {
        failedCount += 1
        repository.markFailure(item, error.message, Number(config.licenseApi.maxAttempts || 6))
        logger.error('License申请失败，已进入重试机制', {
          outboxId: item.id,
          attempt: item.attempt_count,
          error: error.message,
        })
      }
    }
    if (force) break
  }
  return { successCount, failedCount }
}

function applyRuntimeStaticFields(payload, staticFields) {
  const result = structuredClone(payload)
  for (const [targetPath, value] of Object.entries(staticFields || {})) {
    setPath(result, targetPath, value)
  }
  return result
}

module.exports = { applyRuntimeStaticFields, processLicenseOutbox }
