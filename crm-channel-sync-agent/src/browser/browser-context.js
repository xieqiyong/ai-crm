const fs = require('fs')
const path = require('path')

const { chromium } = require('playwright')

async function openBrowserContext(config, headless = config.headless) {
  removeStaleProfileLocks(config.profileDir)
  return chromium.launchPersistentContext(config.profileDir, {
    headless: Boolean(headless),
    artifactsDir: config.artifactDir,
    viewport: { width: 1440, height: 900 },
    acceptDownloads: true,
    locale: 'zh-CN',
    timezoneId: config.timezone || 'Asia/Shanghai',
    args: [
      '--disable-blink-features=AutomationControlled',
      '--no-sandbox',
      '--disable-dev-shm-usage',
    ],
  })
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
  const currentUrl = page.url()
  const loginVisible = await page.locator('text=/微信登录|扫码登录|企业微信登录/').first()
    .isVisible({ timeout: 1200 })
    .catch(() => false)
  if (loginVisible || /login\.work\.weixin\.qq\.com|\/login/i.test(currentUrl)) {
    throw new Error('企微登录已失效，请通过noVNC重新扫码登录')
  }
}

module.exports = { openBrowserContext, removeStaleProfileLocks, verifyWecomLogin }
