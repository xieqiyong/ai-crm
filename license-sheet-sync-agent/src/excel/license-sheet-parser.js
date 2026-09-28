const ExcelJS = require('exceljs')

const { sha256Text } = require('../core/files')

async function parseLicenseSheet(filePath, config) {
  const workbook = new ExcelJS.Workbook()
  await workbook.xlsx.readFile(filePath)
  const worksheet = resolveWorksheet(workbook, config.excel?.sheetName)
  const headerRowNumber = resolveHeaderRowNumber(worksheet, config.excel)
  const headers = readHeaders(worksheet.getRow(headerRowNumber))
  if (!headers.size) {
    throw new Error('没有在Excel中识别到有效表头')
  }

  const records = []
  for (let rowNumber = headerRowNumber + 1; rowNumber <= worksheet.rowCount; rowNumber += 1) {
    const raw = readDataRow(worksheet.getRow(rowNumber), headers)
    if (!Object.keys(raw).length) continue
    records.push(buildRecord(raw, rowNumber, config.excel))
  }
  if (!records.length) {
    throw new Error('Excel中没有可处理的数据行')
  }
  return { worksheetName: worksheet.name, headerRowNumber, headers: [...headers.values()], records }
}

function resolveWorksheet(workbook, configuredName) {
  if (configuredName) {
    const configured = workbook.getWorksheet(configuredName)
    if (!configured) throw new Error(`Excel中不存在工作表：${configuredName}`)
    return configured
  }
  const worksheet = workbook.worksheets.find((item) => item.actualRowCount > 0)
  if (!worksheet) throw new Error('Excel中没有可读取的工作表')
  return worksheet
}

function resolveHeaderRowNumber(worksheet, excelConfig) {
  const scanRows = Math.min(Number(excelConfig?.headerScanRows || 20), worksheet.rowCount)
  const knownSelectors = [
    ...(excelConfig?.identitySelectors || []),
    ...(excelConfig?.requiredSelectors || []),
    ...Object.values(excelConfig?.requestFieldMapping || {}),
    ...collectRequestJsonSelectors(excelConfig?.requestJsonFields),
  ]
  let bestRow = 0
  let bestScore = -1
  for (let rowNumber = 1; rowNumber <= scanRows; rowNumber += 1) {
    const headers = [...readHeaders(worksheet.getRow(rowNumber)).values()]
    if (!headers.length) continue
    const matched = knownSelectors.filter((selector) => findHeader(headers, selector)).length
    const score = matched * 1000 + headers.length
    if (score > bestScore) {
      bestScore = score
      bestRow = rowNumber
    }
  }
  if (!bestRow) throw new Error('前几行中没有识别到Excel表头')
  return bestRow
}

function readHeaders(row) {
  const headers = new Map()
  const counts = new Map()
  row.eachCell({ includeEmpty: false }, (cell, columnNumber) => {
    const original = cellText(cell)
    const normalized = normalizeHeader(original)
    if (!normalized) return
    const count = Number(counts.get(normalized) || 0) + 1
    counts.set(normalized, count)
    headers.set(columnNumber, count === 1 ? normalized : `${normalized}#${count}`)
  })
  return headers
}

function readDataRow(row, headers) {
  const result = {}
  for (const [columnNumber, header] of headers.entries()) {
    const value = cellText(row.getCell(columnNumber))
    if (value) result[header] = value
  }
  return result
}

function buildRecord(raw, rowNumber, excelConfig) {
  const identitySelectors = excelConfig?.identitySelectors || []
  const identityValues = identitySelectors
    .map((selector) => resolveValue(raw, selector))
    .filter(Boolean)
  const minimumMatches = Math.max(1, Number(excelConfig?.minimumIdentityMatches || identitySelectors.length))
  const missingRequired = (excelConfig?.requiredSelectors || [])
    .filter((selector) => !resolveValue(raw, selector))
    .map(selectorLabel)
  const errors = []
  if (identityValues.length < minimumMatches) {
    errors.push(`稳定身份字段不足，需要至少${minimumMatches}组，实际${identityValues.length}组`)
  }
  if (missingRequired.length) {
    errors.push(`缺少必填字段：${missingRequired.join('、')}`)
  }
  for (const rule of excelConfig?.validationRules || []) {
    const value = resolveValue(raw, rule.selector)
    if (!value) continue
    if (rule.pattern && !new RegExp(rule.pattern).test(value)) {
      errors.push(rule.message || `${selectorLabel(rule.selector)}格式不正确`)
    }
    if (Array.isArray(rule.forbiddenDomains)) {
      const domain = String(value).split('@').pop().toLowerCase()
      const forbidden = rule.forbiddenDomains.some((item) => String(item).toLowerCase() === domain)
      if (forbidden) {
        errors.push(rule.forbiddenDomainMessage || `${selectorLabel(rule.selector)}不能使用个人邮箱`)
      }
    }
  }
  const canonicalRaw = canonicalJson(raw)
  const contentHash = sha256Text(canonicalRaw)
  const rowKey = errors.length
    ? `invalid:${contentHash}`
    : sha256Text(identityValues.map(normalizeIdentityValue).join('|'))
  return {
    rowNumber,
    raw,
    rowKey,
    contentHash,
    identityValues,
    validationError: errors.join('；'),
  }
}

function buildLicensePayload(raw, mapping, staticFields = {}, requestJsonFields = {}) {
  const payload = {}
  for (const [targetPath, selector] of Object.entries(mapping || {})) {
    const value = resolveValue(raw, selector)
    if (value !== '') setPath(payload, targetPath, value)
  }
  for (const [targetPath, value] of Object.entries(staticFields || {})) {
    setPath(payload, targetPath, value)
  }
  for (const [targetPath, definition] of Object.entries(requestJsonFields || {})) {
    const jsonValue = {}
    Object.assign(jsonValue, definition?.constants || {})
    for (const [jsonKey, selector] of Object.entries(definition?.mapping || {})) {
      const value = resolveValue(raw, selector)
      if (value !== '') setPath(jsonValue, jsonKey, value)
    }
    setPath(payload, targetPath, JSON.stringify(jsonValue))
  }
  return payload
}

function collectRequestJsonSelectors(requestJsonFields) {
  const selectors = []
  for (const definition of Object.values(requestJsonFields || {})) {
    selectors.push(...Object.values(definition?.mapping || {}))
  }
  return selectors
}

function resolveValue(raw, selector) {
  const aliases = Array.isArray(selector) ? selector : [selector]
  for (const alias of aliases) {
    const key = normalizeHeader(alias)
    const value = raw[key]
    if (value !== undefined && value !== null && String(value).trim()) {
      return String(value).trim()
    }
  }
  return ''
}

function findHeader(headers, selector) {
  const values = new Set(headers.map(normalizeHeader))
  const aliases = Array.isArray(selector) ? selector : [selector]
  return aliases.some((alias) => values.has(normalizeHeader(alias)))
}

function cellText(cell) {
  if (!cell) return ''
  const value = cell.value
  if (value instanceof Date) return value.toISOString()
  if (value && typeof value === 'object') {
    if (Array.isArray(value.richText)) {
      return value.richText.map((item) => item.text || '').join('').trim()
    }
    if (Object.prototype.hasOwnProperty.call(value, 'result')) {
      return String(value.result ?? '').trim()
    }
    if (value.text) return String(value.text).trim()
    if (value.hyperlink) return String(value.text || value.hyperlink).trim()
  }
  return String(cell.text || value || '').trim()
}

function normalizeHeader(value) {
  return String(value || '')
    .trim()
    .replace(/[＊*]/g, '')
    .replace(/\s+/g, '')
}

function normalizeIdentityValue(value) {
  return String(value || '').trim().toLowerCase().replace(/[\s\-－—()（）]/g, '')
}

function canonicalJson(value) {
  const ordered = {}
  Object.keys(value).sort().forEach((key) => {
    ordered[key] = value[key]
  })
  return JSON.stringify(ordered)
}

function selectorLabel(selector) {
  return Array.isArray(selector) ? selector[0] : String(selector)
}

function setPath(target, targetPath, value) {
  const parts = String(targetPath || '').split('.').filter(Boolean)
  if (!parts.length) return
  let current = target
  for (let index = 0; index < parts.length - 1; index += 1) {
    const key = parts[index]
    if (!current[key] || typeof current[key] !== 'object') current[key] = {}
    current = current[key]
  }
  current[parts[parts.length - 1]] = value
}

module.exports = {
  buildLicensePayload,
  parseLicenseSheet,
  resolveValue,
  setPath,
}
