const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const test = require('node:test')

const { acquireRunLock } = require('../src/core/run-lock')

test('容器重启复用进程号时会清理旧锁', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'license-run-lock-'))
  const lockPath = path.join(directory, 'sync.lock')
  const config = { lockPath, export: { maxRunMinutes: 30 } }
  fs.writeFileSync(lockPath, JSON.stringify({
    pid: process.pid,
    token: '旧容器任务',
    createdAt: Date.now(),
  }))

  const release = acquireRunLock(config)
  assert.equal(fs.existsSync(lockPath), true)
  release()
  assert.equal(fs.existsSync(lockPath), false)
  fs.rmSync(directory, { recursive: true, force: true })
})

test('当前进程持有运行锁时拒绝重复获取', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'license-run-lock-'))
  const lockPath = path.join(directory, 'sync.lock')
  const config = { lockPath, export: { maxRunMinutes: 30 } }
  const release = acquireRunLock(config)

  assert.throws(() => acquireRunLock(config), /已有同步任务正在运行/)
  release()
  fs.rmSync(directory, { recursive: true, force: true })
})
