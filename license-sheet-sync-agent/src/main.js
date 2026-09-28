const fs = require('fs')
const path = require('path')
const cron = require('node-cron')

const { openBrowserContext } = require('./browser/browser-context')
const { ensureDataDirectories } = require('./core/files')
const { loadConfig } = require('./core/config')
const { createLogger } = require('./core/logger')
const { acquireRunLock } = require('./core/run-lock')
const { parseLicenseSheet } = require('./excel/license-sheet-parser')
const { processLicenseOutbox } = require('./jobs/license-outbox-job')
const { runSheetSync } = require('./jobs/sheet-sync-job')
const { migrateDatabase, openInitializedDatabase } = require('./persistence/database')
const { SyncRepository } = require('./persistence/repositories')

async function main() {
  const config = loadConfig()
  ensureDataDirectories(config)
  const logger = createLogger(config.logPath)
  registerProcessHandlers(logger)
  const mode = String(process.argv[2] || 'run').toLowerCase()

  if (mode === 'migrate') {
    migrateDatabase(config, logger)
    logger.info('同步数据库初始化完成', { databasePath: config.databasePath })
    return
  }
  if (mode === 'login') {
    await login(config, logger)
    return
  }
  if (mode === 'doctor') {
    doctor(config, logger)
    return
  }
  if (mode === 'retry') {
    await retry(config, logger)
    return
  }
  if (mode === 'baseline') {
    await baseline(config, logger)
    return
  }
  if (mode === 'validate') {
    await validateFile(config, logger, process.argv[3])
    return
  }
  if (mode === 'start') {
    await start(config, logger)
    return
  }
  await runOnce(config, logger, 'MANUAL')
}

async function login(config, logger) {
  const release = acquireRunLock(config)
  let context
  try {
    context = await openBrowserContext(config, false)
    const pages = context.pages()
    const page = pages[0] || await context.newPage()
    await page.goto(config.source.url, { waitUntil: 'domcontentloaded', timeout: 60000 })
    logger.info('企微登录窗口已经打开，请通过noVNC完成扫码登录，完成后回到终端按回车')
    await waitForEnterOrSignal()
    logger.info('企微浏览器登录状态已经保存')
  } finally {
    await context?.close().catch(() => {})
    release()
  }
}

async function runOnce(config, logger, triggerType, options = {}) {
  const release = acquireRunLock(config)
  const database = openInitializedDatabase(config)
  try {
    return await runSheetSync(database, config, logger, triggerType, options)
  } finally {
    database.close()
    release()
  }
}

async function baseline(config, logger) {
  if (config.licenseApi?.enabled) {
    throw new Error('执行首次基线前必须关闭licenseApi.enabled，避免历史数据误提交')
  }
  const result = await runOnce(config, logger, 'BASELINE', { baseline: true })
  logger.info('首次数据基线完成，当前历史记录不会进入License申请队列', result)
}

async function validateFile(config, logger, inputPath) {
  if (!inputPath) {
    throw new Error('请指定要校验的Excel文件，例如：npm run validate -- AMC_许可申请表单.xlsx')
  }
  const filePath = path.resolve(process.cwd(), inputPath)
  if (!fs.existsSync(filePath)) {
    throw new Error(`Excel文件不存在：${filePath}`)
  }
  const result = await parseLicenseSheet(filePath, config)
  const invalidRows = result.records
    .filter((item) => item.validationError)
    .map((item) => ({ rowNumber: item.rowNumber, reason: item.validationError }))
  logger.info('Excel文件校验完成', {
    filePath,
    worksheet: result.worksheetName,
    totalCount: result.records.length,
    validCount: result.records.length - invalidRows.length,
    invalidCount: invalidRows.length,
    invalidRows,
  })
}

async function retry(config, logger) {
  const release = acquireRunLock(config)
  const database = openInitializedDatabase(config)
  try {
    const result = await processLicenseOutbox(database, config, logger, { force: true })
    logger.info('License失败任务重试结束', result)
  } finally {
    database.close()
    release()
  }
}

async function start(config, logger) {
  openInitializedDatabase(config).close()
  const expression = config.schedule || '0 9,14 * * *'
  if (!cron.validate(expression)) {
    throw new Error(`定时表达式不正确：${expression}`)
  }
  let running = false
  const execute = async (triggerType) => {
    if (running) {
      logger.warn('上一轮同步尚未结束，本次调度自动跳过')
      return
    }
    running = true
    try {
      await runOnce(config, logger, triggerType)
    } catch (error) {
      logger.error('定时同步执行失败', error)
    } finally {
      running = false
    }
  }
  cron.schedule(expression, () => execute('SCHEDULED'), {
    timezone: config.timezone || 'Asia/Shanghai',
  })
  logger.info('License表格同步服务已经启动', {
    schedule: expression,
    timezone: config.timezone || 'Asia/Shanghai',
    source: config.source.name,
  })
  if (config.runOnStart) {
    await execute('STARTUP')
  }
  await new Promise(() => {})
}

function doctor(config, logger) {
  const checks = {
    configPath: config.configPath,
    sourceUrl: maskShareCode(config.source.url),
    databaseInitialized: fs.existsSync(config.databasePath),
    display: process.env.DISPLAY || '',
    licenseApiEnabled: Boolean(config.licenseApi?.enabled),
    licenseTokenConfigured: config.licenseApi?.tokenEnv
      ? Boolean(process.env[config.licenseApi.tokenEnv])
      : true,
  }
  if (checks.databaseInitialized) {
    const database = openInitializedDatabase(config)
    try {
      checks.latestRun = new SyncRepository(database).latestRun(config.source.id) || null
    } finally {
      database.close()
    }
  }
  logger.info('运行环境检查完成', checks)
}

function waitForEnterOrSignal() {
  return new Promise((resolve) => {
    if (process.stdin.isTTY) {
      process.stdin.resume()
      process.stdin.once('data', resolve)
      return
    }
    const finish = () => resolve()
    process.once('SIGINT', finish)
    process.once('SIGTERM', finish)
  })
}

function maskShareCode(value) {
  try {
    const url = new URL(value)
    if (url.searchParams.has('scode')) url.searchParams.set('scode', '***')
    return url.toString()
  } catch {
    return ''
  }
}

function registerProcessHandlers(logger) {
  process.on('unhandledRejection', (error) => {
    logger.error('出现未处理的异步异常', error)
  })
  process.on('uncaughtException', (error) => {
    logger.error('出现未捕获异常，进程即将退出', error)
    process.exitCode = 1
  })
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
