const fs = require('fs')
const path = require('path')

const { chromium } = require('playwright')

async function openBrowserContext(config, headless = config.headless) {
  const browserDownloadDir = config.browserDownloadDir || path.join(config.dataDir, 'browser-downloads')
  await waitForProfileRelease(
    config.profileDir,
    Number(config.export?.profileReleaseTimeoutMs || 30000),
  )
  resetBrowserDownloadDir(browserDownloadDir)
  removeStaleProfileLocks(config.profileDir)
  return chromium.launchPersistentContext(config.profileDir, {
    headless: Boolean(headless),
    viewport: { width: 1440, height: 900 },
    acceptDownloads: true,
    downloadsPath: browserDownloadDir,
    locale: 'zh-CN',
    timezoneId: config.timezone || 'Asia/Shanghai',
    args: [
      '--disable-blink-features=AutomationControlled',
      '--no-sandbox',
      '--disable-dev-shm-usage',
    ],
  })
}

function resetBrowserDownloadDir(browserDownloadDir) {
  fs.rmSync(browserDownloadDir, { recursive: true, force: true })
  fs.mkdirSync(browserDownloadDir, { recursive: true })
}

async function waitForProfileRelease(profileDir, timeoutMs = 30000, dependencies = {}) {
  const isActive = dependencies.isActive || hasActiveProfileProcess
  const wait = dependencies.wait || waitForTimeout
  const now = dependencies.now || Date.now
  const deadline = now() + Math.max(0, Number(timeoutMs) || 0)
  while (isActive(profileDir)) {
    if (now() >= deadline) {
      throw new Error('等待企微浏览器退出超时，请稍后重试')
    }
    await wait(500)
  }
}

function waitForTimeout(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

function removeStaleProfileLocks(profileDir) {
  if (hasActiveProfileProcess(profileDir)) {
    throw new Error('企微浏览器登录目录正在被其他Chromium进程使用')
  }
  for (const fileName of ['SingletonLock', 'SingletonCookie', 'SingletonSocket']) {
    fs.rmSync(path.join(profileDir, fileName), { force: true })
  }
}

function hasActiveProfileProcess(profileDir) {
  if (process.platform !== 'linux' || !fs.existsSync('/proc')) return false
  const argument = `--user-data-dir=${profileDir}`
  for (const entry of fs.readdirSync('/proc')) {
    if (!/^\d+$/.test(entry)) continue
    try {
      const commandLine = fs.readFileSync(path.join('/proc', entry, 'cmdline'), 'utf8')
      if (commandLine.includes(argument)) return true
    } catch {
      // 进程可能在读取期间结束，继续检查其他进程
    }
  }
  return false
}

async function verifyWecomLogin(page) {
  const frames = typeof page.frames === 'function' ? page.frames() : []
  const surfaces = [page, ...frames]
  const loginChecks = await Promise.all(surfaces.map(async (surface) => {
    const currentUrl = surface.url()
    if (/login\.work\.weixin\.qq\.com|\/login/i.test(currentUrl)) return true
    return surface.locator([
      'text=/企业身份登录/',
      'text=/企业微信扫码登录/',
      'text=/使用企业微信扫描二维码登录/',
      'text=/微信登录|扫码登录|企业微信登录/',
    ].join(', ')).first()
      .isVisible({ timeout: 1200 })
      .catch(() => false)
  }))
  if (loginChecks.some(Boolean)) {
    throw new Error('企微登录已失效，请通过noVNC执行登录流程')
  }
}

module.exports = {
  openBrowserContext,
  removeStaleProfileLocks,
  resetBrowserDownloadDir,
  verifyWecomLogin,
  waitForProfileRelease,
}
