const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const test = require('node:test')

const {
  removeStaleProfileLocks,
  resetBrowserDownloadDir,
  verifyWecomLogin,
  waitForProfileRelease,
} = require('../src/browser/browser-context')

test('启动浏览器前只清理Chromium临时锁文件', () => {
  const profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'license-browser-profile-'))
  const loginState = path.join(profileDir, 'Cookies')
  fs.writeFileSync(loginState, '登录态')
  for (const fileName of ['SingletonLock', 'SingletonCookie', 'SingletonSocket']) {
    fs.writeFileSync(path.join(profileDir, fileName), '旧锁')
  }

  removeStaleProfileLocks(profileDir)

  assert.equal(fs.existsSync(loginState), true)
  for (const fileName of ['SingletonLock', 'SingletonCookie', 'SingletonSocket']) {
    assert.equal(fs.existsSync(path.join(profileDir, fileName)), false)
  }
  fs.rmSync(profileDir, { recursive: true, force: true })
})

test('浏览器子进程退出后再继续使用登录目录', async () => {
  let activeChecks = 0
  let waitCount = 0
  await waitForProfileRelease('/app/data/browser-profile', 30000, {
    isActive: () => {
      activeChecks += 1
      return activeChecks < 3
    },
    wait: async () => {
      waitCount += 1
    },
  })

  assert.equal(activeChecks, 3)
  assert.equal(waitCount, 2)
})

test('浏览器子进程长期不退出时明确报错', async () => {
  let currentTime = 0
  await assert.rejects(
    waitForProfileRelease('/app/data/browser-profile', 1000, {
      isActive: () => true,
      now: () => currentTime,
      wait: async () => {
        currentTime += 500
      },
    }),
    /等待企微浏览器退出超时/,
  )
})

test('每次启动浏览器前清理专用下载临时目录', () => {
  const baseDir = fs.mkdtempSync(path.join(os.tmpdir(), 'license-browser-download-'))
  const browserDownloadDir = path.join(baseDir, 'browser-downloads')
  fs.mkdirSync(browserDownloadDir)
  fs.writeFileSync(path.join(browserDownloadDir, 'old.crdownload'), 'old')

  resetBrowserDownloadDir(browserDownloadDir)

  assert.deepEqual(fs.readdirSync(browserDownloadDir), [])
  fs.rmSync(baseDir, { recursive: true, force: true })
})

test('登录二维码出现在内嵌页面时也能识别登录失效', async () => {
  const hiddenSurface = createLoginSurface(false)
  const loginFrame = createLoginSurface(true)
  const page = {
    ...hiddenSurface,
    frames: () => [hiddenSurface, loginFrame],
  }

  await assert.rejects(verifyWecomLogin(page), /企微登录已失效/)
})

function createLoginSurface(visible) {
  return {
    url: () => 'https://doc.weixin.qq.com/smartsheet/example',
    locator: () => ({
      first: () => ({
        isVisible: async () => visible,
      }),
    }),
  }
}
