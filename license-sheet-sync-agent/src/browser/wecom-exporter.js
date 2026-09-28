const fs = require('fs')
const path = require('path')

const { safeFileName, timestampToken } = require('../core/files')
const { verifyWecomLogin } = require('./browser-context')

async function exportSmartSheet(context, config, logger) {
  const keeperPage = await context.newPage()
  await keeperPage.goto('about:blank')
  const page = await context.newPage()
  const source = config.source
  let downloadPromise
  let exportActive = true
  let exportPhase = '准备页面'
  registerBrowserDiagnostics(context, page, logger, () => ({ exportActive, exportPhase }))
  try {
    logger.info('打开企微智能表格', { source: source.name, url: safeLogUrl(source.url) })
    await withTimeout(preventPageSelfClose(page), 5000, '初始化企微页面保护超时')
    logger.info('开始加载企微智能表格页面', { source: source.name })
    await page.goto(source.url, { waitUntil: 'domcontentloaded', timeout: 60000 })
    logger.info('企微智能表格页面基础加载完成', { source: source.name })
    await page.waitForTimeout(Number(config.export?.pageReadyDelayMs || 5000))
    await verifyWecomLogin(page)

    exportPhase = '等待下载事件'
    downloadPromise = page.waitForEvent('download', {
      timeout: Number(config.export?.downloadTimeoutMs || 90000),
    })
    downloadPromise.catch(() => {})
    await triggerExport(page)
    logger.info('已经触发Excel导出，等待企微生成文件', { source: source.name })

    const download = await downloadPromise
    exportPhase = '保存下载文件'
    const suggested = download.suggestedFilename() || `${source.name}.xlsx`
    if (path.extname(suggested).toLowerCase() === '.xls') {
      throw new Error('企微返回了旧版.xls文件，当前任务只接受导出菜单中的.xlsx格式')
    }
    const targetName = `${safeFileName(source.id)}-${timestampToken()}.xlsx`
    const filePath = path.join(config.downloadDir, targetName)
    await download.saveAs(filePath)
    validateExcelFile(filePath)
    exportPhase = '下载完成'
    logger.info('企微智能表格导出完成', { source: source.name, filePath })
    return filePath
  } catch (error) {
    const screenshot = path.join(config.dataDir, `error-${safeFileName(source.id)}-${timestampToken()}.png`)
    await page.screenshot({ path: screenshot, fullPage: false }).catch(() => {})
    error.message = `${error.message}，现场截图：${screenshot}`
    throw error
  } finally {
    exportActive = false
    await page.close().catch(() => {})
    await keeperPage.close().catch(() => {})
  }
}

async function preventPageSelfClose(page) {
  await page.addInitScript(() => {
    window.close = () => {}
  })
}

async function withTimeout(operation, timeoutMs, message) {
  let timer
  try {
    return await Promise.race([
      operation,
      new Promise((resolve, reject) => {
        timer = setTimeout(() => reject(new Error(message)), timeoutMs)
      }),
    ])
  } finally {
    clearTimeout(timer)
  }
}

function registerBrowserDiagnostics(context, page, logger, state) {
  page.once('close', () => {
    const current = state()
    if (current.exportActive) {
      logger.warn('企微导出页面在文件保存前被关闭', { phase: current.exportPhase })
    }
  })
  page.once('crash', () => {
    const current = state()
    if (current.exportActive) {
      logger.warn('企微导出页面发生崩溃', { phase: current.exportPhase })
    }
  })
  context.once('close', () => {
    const current = state()
    if (current.exportActive) {
      logger.warn('企微导出浏览器上下文在文件保存前被关闭', { phase: current.exportPhase })
    }
  })
}

async function triggerExport(page) {
  const direct = page.locator('text=/^导出$/').first()
  if (await direct.isVisible().catch(() => false)) {
    await direct.hover().catch(() => {})
    await direct.click({ force: true })
    await triggerFormatClick(page, [])
    return
  }
  if (!(await openTopMenu(page))) {
    throw new Error('没有成功打开右上角三道杠菜单')
  }
  await page.waitForTimeout(500)
  const exportItem = page.locator('text=/^导出/').first()
  if (!(await exportItem.isVisible().catch(() => false))) {
    throw new Error('右上角菜单已经打开，但没有找到导出入口')
  }
  await exportItem.hover().catch(() => {})
  await exportItem.click({ force: true })
  await triggerFormatClick(page, [exportItem])
}

async function openTopMenu(page) {
  const named = page.locator([
    '[aria-label*="菜单"]',
    '[aria-label*="更多"]',
    '[title*="菜单"]',
    '[title*="更多"]',
    '[aria-label*="menu" i]',
    '[aria-label*="more" i]',
    '[class*="menu-btn" i]',
  ].join(', ')).first()
  if (await named.isVisible().catch(() => false)) {
    await named.click({ force: true }).catch(() => {})
    if (await isMenuOpen(page)) return true
  }

  const candidates = await collectTopbarCandidates(page)
  for (const candidate of candidates) {
    await candidate.item.click({ force: true }).catch(() => {})
    if (await isMenuOpen(page)) return true
    await page.mouse.click(400, 400).catch(() => {})
    await page.waitForTimeout(300)
  }
  return false
}

async function collectTopbarCandidates(page) {
  const buttons = page.locator('button, [role="button"], [class*="btn" i]')
  const count = await buttons.count().catch(() => 0)
  const candidates = []
  for (let index = 0; index < count; index += 1) {
    const item = buttons.nth(index)
    const box = await item.boundingBox().catch(() => null)
    if (!box || box.y > 130 || box.x < 700 || box.width > 130) continue
    candidates.push({ item, score: box.x + box.width, y: box.y })
  }
  candidates.sort((first, second) => (second.score - first.score) || (second.y - first.y))
  return candidates.slice(0, 10)
}

async function isMenuOpen(page) {
  return page.locator('text=/^导出$|^打印$|打印和导出|^下载$|^设置$|^帮助$/').first()
    .isVisible({ timeout: 1000 })
    .catch(() => false)
}

async function triggerFormatClick(page, hoverTargets) {
  const patterns = [
    'text=/本地Excel表格\\s*\\(\\s*\\.xlsx\\s*\\)/i',
    'text=/\\.xlsx/i',
    'text=/本地Excel表格|Excel/i',
  ]
  let formatItem
  for (const pattern of patterns) {
    const item = page.locator(pattern).first()
    await item.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {})
    if (await item.isVisible().catch(() => false)) {
      formatItem = item
      break
    }
    for (const target of hoverTargets) {
      await target.hover().catch(() => {})
    }
    await page.waitForTimeout(500)
  }
  if (!formatItem) {
    throw new Error('导出菜单中没有出现“本地Excel表格(.xlsx)”')
  }
  await formatItem.click({ force: true })
  await page.waitForTimeout(800)
  const confirm = page.locator('text=/开始导出|确认导出|确定导出|^确定$/').first()
  if (await confirm.isVisible().catch(() => false)) {
    await confirm.click({ force: true })
  }
}

function validateExcelFile(filePath) {
  const stat = fs.statSync(filePath)
  if (stat.size < 100) {
    throw new Error('企微导出的Excel文件为空或不完整')
  }
  const handle = fs.openSync(filePath, 'r')
  try {
    const magic = Buffer.alloc(2)
    fs.readSync(handle, magic, 0, 2, 0)
    if (magic.toString('utf8') !== 'PK') {
      throw new Error('企微下载结果不是有效的.xlsx文件，可能下载到了登录页或错误页面')
    }
  } finally {
    fs.closeSync(handle)
  }
}

function safeLogUrl(value) {
  try {
    const url = new URL(value)
    url.searchParams.delete('scode')
    return url.toString()
  } catch {
    return ''
  }
}

module.exports = { exportSmartSheet }
