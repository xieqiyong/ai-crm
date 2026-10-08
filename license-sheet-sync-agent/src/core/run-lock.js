const fs = require('fs')

const PROCESS_TOKEN = `${process.pid}-${Date.now()}-${Math.random()}`

function acquireRunLock(config) {
  const staleMs = Number(config.export?.maxRunMinutes || 30) * 60 * 1000
  if (fs.existsSync(config.lockPath)) {
    const current = readLock(config.lockPath)
    if (current
      && Date.now() - Number(current.createdAt || 0) < staleMs
      && isLockOwnerAlive(current)) {
      throw new Error(`已有同步任务正在运行，进程号：${current.pid || '未知'}`)
    }
    fs.rmSync(config.lockPath, { force: true })
  }
  const value = {
    pid: process.pid,
    processToken: PROCESS_TOKEN,
    token: `${process.pid}-${Date.now()}-${Math.random()}`,
    createdAt: Date.now(),
  }
  fs.writeFileSync(config.lockPath, JSON.stringify(value), { flag: 'wx' })
  return () => {
    const current = readLock(config.lockPath)
    if (current?.token === value.token) {
      fs.rmSync(config.lockPath, { force: true })
    }
  }
}

function isLockOwnerAlive(lock) {
  const processId = Number(lock?.pid)
  if (!isProcessAlive(processId)) return false
  if (processId !== process.pid) return true
  return lock?.processToken === PROCESS_TOKEN
}

function readLock(lockPath) {
  try {
    return JSON.parse(fs.readFileSync(lockPath, 'utf8'))
  } catch {
    return null
  }
}

function isProcessAlive(pid) {
  const processId = Number(pid)
  if (!Number.isInteger(processId) || processId <= 0) return false
  try {
    process.kill(processId, 0)
    return true
  } catch (error) {
    return error?.code === 'EPERM'
  }
}

module.exports = { acquireRunLock, isProcessAlive }
