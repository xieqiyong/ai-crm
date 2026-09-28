const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const test = require('node:test')

const { removeStaleProfileLocks } = require('../src/browser/browser-context')

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
