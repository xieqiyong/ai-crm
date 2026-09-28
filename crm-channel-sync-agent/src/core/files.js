const crypto = require('crypto')
const fs = require('fs')
const path = require('path')

function ensureDataDirectories(config) {
  const targets = [config.dataDir, config.profileDir, config.downloadDir, path.dirname(config.logPath)]
  for (const target of targets) {
    fs.mkdirSync(target, { recursive: true })
  }
}

function safeFileName(value) {
  return String(value || 'export').replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_')
}

function sha256Text(value) {
  return crypto.createHash('sha256').update(String(value || ''), 'utf8').digest('hex')
}

function timestampToken() {
  return new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14)
}

module.exports = { ensureDataDirectories, safeFileName, sha256Text, timestampToken }
