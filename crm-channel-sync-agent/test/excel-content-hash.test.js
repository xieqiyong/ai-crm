const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const ExcelJS = require('exceljs')

const { calculateExcelContentHash } = require('../src/excel/excel-content-hash')

test('相同业务内容的Excel哈希一致，单元格变化后哈希变化', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'crm-channel-hash-'))
  const firstPath = path.join(directory, 'first.xlsx')
  const secondPath = path.join(directory, 'second.xlsx')
  try {
    await writeWorkbook(firstPath, '杭州示例公司')
    await writeWorkbook(secondPath, '杭州示例公司')
    const firstHash = await calculateExcelContentHash(firstPath)
    const secondHash = await calculateExcelContentHash(secondPath)
    assert.equal(firstHash, secondHash)

    await writeWorkbook(secondPath, '宁波示例公司')
    const changedHash = await calculateExcelContentHash(secondPath)
    assert.notEqual(firstHash, changedHash)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

async function writeWorkbook(filePath, companyName) {
  const workbook = new ExcelJS.Workbook()
  const sheet = workbook.addWorksheet('表单')
  sheet.addRow(['公司名称', '手机号'])
  sheet.addRow([companyName, '13800000000'])
  await workbook.xlsx.writeFile(filePath)
}
