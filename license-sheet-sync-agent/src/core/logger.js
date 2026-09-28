const fs = require('fs')
const path = require('path')

function createLogger(logPath) {
  fs.mkdirSync(path.dirname(logPath), { recursive: true })

  function write(level, message, detail) {
    const suffix = detail === undefined ? '' : ` ${serialize(detail)}`
    const line = `[${new Date().toLocaleString('zh-CN', { hour12: false })}] ${level} ${message}${suffix}`
    console.log(line)
    fs.appendFileSync(logPath, line + '\n')
  }

  return {
    info: (message, detail) => write('INFO', message, detail),
    warn: (message, detail) => write('WARN', message, detail),
    error: (message, detail) => write('ERROR', message, detail),
  }
}

function serialize(value) {
  if (value instanceof Error) {
    return JSON.stringify({ message: value.message, stack: value.stack })
  }
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, (key, item) => {
      if (item instanceof Error) {
        return { message: item.message, stack: item.stack }
      }
      return item
    })
  } catch {
    return String(value)
  }
}

module.exports = { createLogger }
