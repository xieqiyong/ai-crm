const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const test = require('node:test')

const {
  isCompleteZip,
  listFiles,
  preserveStagedDownload,
  waitForNewFile,
} = require('../src/browser/wecom-exporter')

test('浏览器退出时仍能保留已经开始下载的Excel文件', async (context) => {
  const baseDir = fs.mkdtempSync(path.join(os.tmpdir(), 'license-sheet-download-'))
  context.after(() => fs.rmSync(baseDir, { recursive: true, force: true }))
  const browserDownloadDir = path.join(baseDir, 'browser-downloads')
  const downloadDir = path.join(baseDir, 'downloads')
  fs.mkdirSync(browserDownloadDir)
  fs.mkdirSync(downloadDir)
  const existingFiles = listFiles(browserDownloadDir)
  const stagedPath = path.join(browserDownloadDir, 'download-guid')
  const targetPath = path.join(downloadDir, 'result.xlsx')

  setTimeout(() => fs.writeFileSync(stagedPath, Buffer.from('PK'.padEnd(128, '0'))), 20)
  const detectedPath = await waitForNewFile(browserDownloadDir, existingFiles, 1000)
  assert.equal(detectedPath, stagedPath)

  setTimeout(() => fs.appendFileSync(stagedPath, Buffer.from([0x50, 0x4b, 0x05, 0x06])), 20)
  assert.equal(await preserveStagedDownload(stagedPath, targetPath, 1000), true)
  fs.rmSync(stagedPath)

  assert.equal(fs.existsSync(targetPath), true)
  assert.equal(isCompleteZip(targetPath), true)
})

test('不完整的下载文件不会被当成Excel使用', () => {
  const baseDir = fs.mkdtempSync(path.join(os.tmpdir(), 'license-sheet-incomplete-'))
  try {
    const filePath = path.join(baseDir, 'incomplete.xlsx')
    fs.writeFileSync(filePath, Buffer.from('PK'.padEnd(128, '0')))
    assert.equal(isCompleteZip(filePath), false)
  } finally {
    fs.rmSync(baseDir, { recursive: true, force: true })
  }
})

test('下载文件停止增长后快速失败以便外层重试', async (context) => {
  const baseDir = fs.mkdtempSync(path.join(os.tmpdir(), 'license-sheet-stalled-'))
  context.after(() => fs.rmSync(baseDir, { recursive: true, force: true }))
  const stagedPath = path.join(baseDir, 'stalled.crdownload')
  const targetPath = path.join(baseDir, 'result.xlsx')
  fs.writeFileSync(stagedPath, Buffer.from('PK'.padEnd(128, '0')))

  await assert.rejects(
    preserveStagedDownload(stagedPath, targetPath, 5000, 100),
    /没有下载完整/,
  )
  assert.equal(fs.existsSync(targetPath), false)
})
