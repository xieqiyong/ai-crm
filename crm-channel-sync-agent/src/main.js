const fs = require('fs')
const cron = require('node-cron')

const { openBrowserContext } = require('./browser/browser-context')
const { loadConfig } = require('./core/config')
const { ensureDataDirectories } = require('./core/files')
const { createLogger } = require('./core/logger')
const { acquireRunLock } = require('./core/run-lock')
const { runChannelSync } = require('./jobs/channel-sync-job')
const { StateStore } = require('./persistence/state-store')

async function main() {
  const config = loadConfig()
  ensureDataDirectories(config)
  const logger = createLogger(config.logPath)
  registerProcessHandlers(logger)
  const mode = String(process.argv[2] || 'run').toLowerCase()
  if (mode === 'login') {
    await login(config, logger)
    return
  }
  if (mode === 'doctor') {
    doctor(config, logger)
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
    const source = config.sources.find((item) => item.enabled !== false) || config.sources[0]
    const pages = context.pages()
    const page = pages[0] || await context.newPage()
    await page.goto(source.url, { waitUntil: 'domcontentloaded', timeout: 60000 })
    logger.info('企微登录窗口已经打开，请通过noVNC完成扫码登录，完成后回到终端按回车')
    await waitForEnterOrSignal()
    logger.info('企微浏览器登录状态已经保存')
  } finally {
    await context?.close().catch(() => {})
    release()
  }
}

async function runOnce(config, logger, triggerType) {
  const release = acquireRunLock(config)
  try {
    const stateStore = new StateStore(config.statePath)
    return await runChannelSync(config, logger, stateStore, triggerType)
  } finally {
    release()
  }
}

async function start(config, logger) {
  for (const expression of config.schedules) {
    if (!cron.validate(expression)) {
      throw new Error(`定时表达式不正确：${expression}`)
    }
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
  for (const expression of config.schedules) {
    cron.schedule(expression, () => execute(`SCHEDULED:${expression}`), {
      timezone: config.timezone || 'Asia/Shanghai',
    })
  }
  logger.info('CRM渠道同步服务已经启动', {
    schedules: config.schedules,
    timezone: config.timezone,
    sourceCount: config.sources.filter((item) => item.enabled !== false).length,
  })
  if (config.runOnStart) {
    await execute('STARTUP')
  }
  await new Promise(() => {})
}

function doctor(config, logger) {
  const tokenName = config.crmApi?.tokenEnv
  const stateStore = new StateStore(config.statePath)
  logger.info('运行环境检查完成', {
    configPath: config.configPath,
    enabledSourceCount: config.sources.filter((item) => item.enabled !== false).length,
    schedules: config.schedules,
    profileExists: fs.existsSync(config.profileDir),
    stateExists: fs.existsSync(config.statePath),
    crmApiEnabled: Boolean(config.crmApi?.enabled),
    crmApiBaseUrl: config.crmApi?.baseUrl,
    crmTokenConfigured: tokenName ? Boolean(process.env[tokenName]) : false,
    sourceStates: stateStore.snapshot().sources,
  })
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
