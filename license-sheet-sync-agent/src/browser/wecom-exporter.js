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
  const browserDownloadDir = config.browserDownloadDir || path.join(config.dataDir, 'browser-downloads')
  fs.mkdirSync(browserDownloadDir, { recursive: true })
  const existingBrowserDownloads = listFiles(browserDownloadDir)
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
    await withTimeout(
      triggerExport(page),
      Number(config.export?.triggerTimeoutMs || 30000),
      '触发企微Excel导出超时',
    )
    logger.info('已经触发Excel导出，等待企微生成文件', { source: source.name })

    const download = await downloadPromise
    exportPhase = '保存下载文件'
    const suggested = download.suggestedFilename() || `${source.name}.xlsx`
    if (path.extname(suggested).toLowerCase() === '.xls') {
      throw new Error('企微返回了旧版.xls文件，当前任务只接受导出菜单中的.xlsx格式')
    }
    const targetName = `${safeFileName(source.id)}-${timestampToken()}.xlsx`
    const filePath = path.join(config.downloadDir, targetName)
    fs.mkdirSync(config.downloadDir, { recursive: true })
    const stagedPath = await waitForNewFile(
      browserDownloadDir,
      existingBrowserDownloads,
      Math.min(Number(config.export?.downloadTimeoutMs || 90000), 10000),
    )
    if (stagedPath) {
      const preserved = await preserveStagedDownload(
        stagedPath,
        filePath,
        Number(config.export?.downloadTimeoutMs || 90000),
        Number(config.export?.downloadStallTimeoutMs || 5000),
      )
      if (!preserved) await download.saveAs(filePath)
    } else {
      await download.saveAs(filePath)
    }
    validateExcelFile(filePath)
    exportPhase = '下载完成'
    logger.info('企微智能表格导出完成', { source: source.name, filePath })
    return filePath
  } catch (error) {
    const screenshot = path.join(config.dataDir, `error-${safeFileName(source.id)}-${timestampToken()}.png`)
    await page.screenshot({ path: screenshot, fullPage: false, timeout: 5000 }).catch(() => {})
    error.message = `${error.message}，现场截图：${screenshot}`
    throw error
  } finally {
    exportActive = false
    await page.close().catch(() => {})
    await keeperPage.close().catch(() => {})
  }
}

function listFiles(directory) {
  return new Set(fs.readdirSync(directory, { withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => entry.name))
}

async function waitForNewFile(directory, existingFiles, timeoutMs) {
  const deadline = Date.now() + Math.max(0, Number(timeoutMs) || 0)
  do {
    const candidates = fs.readdirSync(directory, { withFileTypes: true })
      .filter((entry) => entry.isFile() && !existingFiles.has(entry.name))
      .map((entry) => {
        const filePath = path.join(directory, entry.name)
        return { filePath, modifiedAt: fs.statSync(filePath).mtimeMs }
      })
      .sort((first, second) => second.modifiedAt - first.modifiedAt)
    if (candidates.length) return candidates[0].filePath
    await waitForTimeout(100)
  } while (Date.now() < deadline)
  return null
}

async function preserveStagedDownload(stagedPath, targetPath, timeoutMs, stallTimeoutMs = 5000) {
  fs.rmSync(targetPath, { force: true })
  try {
    fs.linkSync(stagedPath, targetPath)
  } catch (error) {
    if (!['EXDEV', 'EPERM', 'EACCES', 'ENOENT'].includes(error.code)) throw error
    return false
  }
  const deadline = Date.now() + Math.max(0, Number(timeoutMs) || 0)
  let lastSize = -1
  let lastChangeAt = Date.now()
  do {
    if (isCompleteZip(targetPath)) return true
    const currentSize = fs.existsSync(targetPath) ? fs.statSync(targetPath).size : -1
    if (currentSize !== lastSize) {
      lastSize = currentSize
      lastChangeAt = Date.now()
    } else if (Date.now() - lastChangeAt >= Math.max(0, Number(stallTimeoutMs) || 0)) {
      break
    }
    await waitForTimeout(200)
  } while (Date.now() < deadline)
  fs.rmSync(targetPath, { force: true })
  throw new Error('企微导出的Excel文件在浏览器关闭前没有下载完整')
}

function isCompleteZip(filePath) {
  if (!fs.existsSync(filePath)) return false
  const stat = fs.statSync(filePath)
  if (stat.size < 100) return false
  const tailLength = Math.min(stat.size, 65557)
  const tail = Buffer.alloc(tailLength)
  const handle = fs.openSync(filePath, 'r')
  try {
    fs.readSync(handle, tail, 0, tailLength, stat.size - tailLength)
  } finally {
    fs.closeSync(handle)
  }
  return tail.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06])) >= 0
}

function waitForTimeout(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
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
  const direct = page.locator('text=/^导出$/').filter({ visible: true }).first()
  if (await direct.isVisible().catch(() => false)) {
    await direct.hover({ timeout: 2000 }).catch(() => {})
    await direct.click({ force: true, timeout: 5000 })
    await triggerFormatClick(page, [])
    return
  }
  if (!(await openTopMenu(page))) {
    throw new Error('没有成功打开右上角三道杠菜单')
  }
  await page.waitForTimeout(500)
  const exportItem = page.locator('text=/^导出/').filter({ visible: true }).first()
  if (!(await exportItem.isVisible().catch(() => false))) {
    throw new Error('右上角菜单已经打开，但没有找到导出入口')
  }
  await exportItem.hover({ timeout: 2000 }).catch(() => {})
  await exportItem.click({ force: true, timeout: 5000 })
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
    await named.click({ force: true, timeout: 5000 }).catch(() => {})
    if (await isMenuOpen(page)) return true
  }

  const candidates = await collectTopbarCandidates(page)
  for (const candidate of candidates) {
    await candidate.item.click({ force: true, timeout: 3000 }).catch(() => {})
    if (await isMenuOpen(page)) return true
    await page.keyboard.press('Escape').catch(() => {})
    await page.mouse.click(400, 400).catch(() => {})
    await page.waitForTimeout(300)
  }
  const viewport = page.viewportSize() || { width: 1440, height: 900 }
  for (const point of [
    { x: viewport.width - 80, y: 28 },
    { x: viewport.width - 80, y: 48 },
    { x: viewport.width - 120, y: 28 },
  ]) {
    await page.keyboard.press('Escape').catch(() => {})
    await page.mouse.click(point.x, point.y).catch(() => {})
    await page.waitForTimeout(500)
    if (await isMenuOpen(page)) return true
  }
  return false
}

async function collectTopbarCandidates(page) {
  const buttons = page.locator([
    'button',
    '[role="button"]',
    '[class*="btn" i]',
    '[class*="menu" i]',
    '[class*="more" i]',
  ].join(', '))
  const count = await buttons.count().catch(() => 0)
  const candidates = []
  const viewport = page.viewportSize() || { width: 1440, height: 900 }
  const targetX = viewport.width - 80
  for (let index = 0; index < count; index += 1) {
    const item = buttons.nth(index)
    const box = await item.boundingBox().catch(() => null)
    if (!box || box.y > 80 || box.x < 600 || box.width > 130 || box.height > 80) continue
    const centerX = box.x + box.width / 2
    candidates.push({ item, score: Math.abs(centerX - targetX) + box.y * 2 })
  }
  candidates.sort((first, second) => first.score - second.score)
  return candidates.slice(0, 12)
}

async function isMenuOpen(page) {
  return page.locator('text=/^导出$|^打印$|打印和导出|^下载$|^设置$|^帮助$/')
    .filter({ visible: true })
    .first()
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
    const item = page.locator(pattern).filter({ visible: true }).first()
    await item.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {})
    if (await item.isVisible().catch(() => false)) {
      formatItem = item
      break
    }
    for (const target of hoverTargets) {
      await target.hover({ timeout: 2000 }).catch(() => {})
    }
    await page.waitForTimeout(500)
  }
  if (!formatItem) {
    throw new Error('导出菜单中没有出现“本地Excel表格(.xlsx)”')
  }
  await formatItem.click({ force: true, timeout: 5000 })
  await page.waitForTimeout(800)
  const confirm = page.locator('text=/开始导出|确认导出|确定导出|^确定$/')
    .filter({ visible: true })
    .first()
  if (await confirm.isVisible().catch(() => false)) {
    await confirm.click({ force: true, timeout: 5000 })
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

module.exports = {
  exportSmartSheet,
  isCompleteZip,
  listFiles,
  preserveStagedDownload,
  waitForNewFile,
}
