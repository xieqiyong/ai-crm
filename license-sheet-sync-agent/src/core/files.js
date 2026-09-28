const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

function ensureDataDirectories(config) {
  for (const target of [config.dataDir, config.profileDir, config.downloadDir, path.dirname(config.logPath)]) {
    fs.mkdirSync(target, { recursive: true })
  }
}

function sha256Buffer(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex')
}

function sha256Text(value) {
  return sha256Buffer(Buffer.from(String(value || ''), 'utf8'))
}

function safeFileName(value) {
  return String(value || 'export.xlsx').replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_')
}

function timestampToken() {
  return new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14)
}

module.exports = {
  ensureDataDirectories,
  safeFileName,
  sha256Buffer,
  sha256Text,
  timestampToken,
}
