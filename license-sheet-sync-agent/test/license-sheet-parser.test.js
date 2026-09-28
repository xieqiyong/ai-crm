const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')
const ExcelJS = require('exceljs')

const { buildLicensePayload, parseLicenseSheet } = require('../src/excel/license-sheet-parser')

test('能够识别表头并解析License申请行', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'license-sheet-parser-'))
  const filePath = path.join(directory, 'sample.xlsx')
  try {
    const workbook = new ExcelJS.Workbook()
    const worksheet = workbook.addWorksheet('申请记录')
    worksheet.addRow(['说明'])
    worksheet.addRow(['填写时间', '公司名称', '姓名', '手机号-手机号', '企业邮箱-电子邮箱', 'App Id', '版本号'])
    worksheet.addRow([
      '2026-09-21 10:00:00',
      '测试企业',
      '测试联系人',
      '13800000000',
      'test@example.com',
      '0123456789abcdef0123456789abcdef',
      '1.0.0',
    ])
    await workbook.xlsx.writeFile(filePath)

    const excel = {
      headerScanRows: 10,
      identitySelectors: [
        ['填写时间', '提交时间'],
        ['手机号-手机号', '手机号码'],
        ['App Id', '机器码'],
      ],
      minimumIdentityMatches: 3,
      requiredSelectors: [
        ['公司名称', '企业名称'],
        ['企业邮箱-电子邮箱', '企业邮箱'],
      ],
      validationRules: [
        {
          selector: ['企业邮箱-电子邮箱', '企业邮箱'],
          pattern: '^[^@]+@[^@]+\\.[^@]+$',
          forbiddenDomains: ['qq.com'],
        },
      ],
      requestFieldMapping: {
        company: ['公司名称', '企业名称'],
        phone: ['手机号-手机号', '手机号码'],
      },
      requestStaticFields: { productName: 'OpenOcta', channelCode: '000', authType: '正式' },
      requestJsonFields: {
        baseInfo: {
          constants: { source: 'wecom-smart-sheet' },
          mapping: { submittedAt: ['填写时间'] },
        },
      },
    }
    const result = await parseLicenseSheet(filePath, { excel })
    assert.equal(result.headerRowNumber, 2)
    assert.equal(result.records.length, 1)
    assert.equal(result.records[0].validationError, '')
    assert.equal(result.records[0].raw['公司名称'], '测试企业')
    assert.deepEqual(buildLicensePayload(
      result.records[0].raw,
      excel.requestFieldMapping,
      excel.requestStaticFields,
      excel.requestJsonFields,
    ), {
      company: '测试企业',
      phone: '13800000000',
      productName: 'OpenOcta',
      channelCode: '000',
      authType: '正式',
      baseInfo: JSON.stringify({
        source: 'wecom-smart-sheet',
        submittedAt: '2026-09-21 10:00:00',
      }),
    })
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('个人邮箱会被标记为无效数据', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'license-sheet-parser-invalid-'))
  const filePath = path.join(directory, 'sample.xlsx')
  try {
    const workbook = new ExcelJS.Workbook()
    const worksheet = workbook.addWorksheet('AMC用户信息收集表单')
    worksheet.addRow(['填写时间', '手机号', '企业邮箱', 'App Id'])
    worksheet.addRow(['2026-09-21 10:00:00', '13800000000', 'test@qq.com', '0123456789abcdef0123456789abcdef'])
    await workbook.xlsx.writeFile(filePath)
    const result = await parseLicenseSheet(filePath, {
      excel: {
        identitySelectors: [['填写时间'], ['手机号'], ['App Id']],
        minimumIdentityMatches: 3,
        validationRules: [{
          selector: ['企业邮箱'],
          forbiddenDomains: ['qq.com'],
          forbiddenDomainMessage: '暂不支持个人邮箱，请填写企业邮箱',
        }],
      },
    })
    assert.match(result.records[0].validationError, /个人邮箱/)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
