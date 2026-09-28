const ExcelJS = require('exceljs')

const { sha256Text } = require('../core/files')

async function calculateExcelContentHash(filePath) {
  const workbook = new ExcelJS.Workbook()
  await workbook.xlsx.readFile(filePath)
  const content = []
  for (const worksheet of workbook.worksheets) {
    const rows = []
    worksheet.eachRow({ includeEmpty: true }, (row, rowNumber) => {
      const cells = []
      row.eachCell({ includeEmpty: true }, (cell, columnNumber) => {
        cells.push([columnNumber, normalizeValue(cell.value)])
      })
      rows.push([rowNumber, cells])
    })
    content.push([worksheet.name, rows])
  }
  return sha256Text(JSON.stringify(content))
}

function normalizeValue(value) {
  if (value === null || value === undefined) return ''
  if (value instanceof Date) return value.toISOString()
  if (typeof value !== 'object') return String(value)
  if (Array.isArray(value.richText)) {
    return value.richText.map((item) => item.text || '').join('')
  }
  if (Object.prototype.hasOwnProperty.call(value, 'result')) {
    return normalizeValue(value.result)
  }
  if (Object.prototype.hasOwnProperty.call(value, 'text')) {
    return normalizeValue(value.text)
  }
  return JSON.stringify(value)
}

module.exports = { calculateExcelContentHash, normalizeValue }
