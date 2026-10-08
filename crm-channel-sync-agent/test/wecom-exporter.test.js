const fs = require('fs')
const os = require('os')
const path = require('path')
const test = require('node:test')
const assert = require('node:assert/strict')

const {
  recoverDownloadFromArtifacts,
  snapshotArtifactFiles,
} = require('../src/browser/wecom-exporter')

test('页面关闭后可以从浏览器持久目录恢复新下载文件', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'crm-channel-artifact-'))
  try {
    const snapshot = snapshotArtifactFiles(directory)
    const artifactPath = path.join(directory, 'download-guid')
    const content = Buffer.alloc(256, 1)
    content.write('PK', 0, 'utf8')
    fs.writeFileSync(artifactPath, content)
    const targetPath = path.join(directory, 'result.xlsx')

    const recovered = await recoverDownloadFromArtifacts(
      directory,
      snapshot,
      targetPath,
      1000,
      10,
    )

    assert.equal(recovered, artifactPath)
    assert.equal(fs.existsSync(targetPath), true)
    assert.deepEqual(fs.readFileSync(targetPath), content)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('恢复时不会误用任务开始前的旧文件', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'crm-channel-artifact-'))
  try {
    const oldPath = path.join(directory, 'old-download')
    const content = Buffer.alloc(256, 1)
    content.write('PK', 0, 'utf8')
    fs.writeFileSync(oldPath, content)
    const snapshot = snapshotArtifactFiles(directory)
    const targetPath = path.join(directory, 'result.xlsx')

    const recovered = await recoverDownloadFromArtifacts(
      directory,
      snapshot,
      targetPath,
      30,
      10,
    )

    assert.equal(recovered, null)
    assert.equal(fs.existsSync(targetPath), false)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
