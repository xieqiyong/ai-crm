const assert = require('node:assert/strict')
const test = require('node:test')

const { downloadSmartSheet, isRecoverableBrowserError } = require('../src/jobs/sheet-sync-job')

test('浏览器下载上下文意外关闭时重新创建上下文', async () => {
  const contexts = []
  let exportCount = 0
  const result = await downloadSmartSheet(
    { export: { maxAttempts: 3, retryDelayMs: 0 } },
    { warn() {} },
    {
      openBrowserContext: async () => {
        const context = { closeCount: 0, async close() { this.closeCount += 1 } }
        contexts.push(context)
        return context
      },
      exportSmartSheet: async () => {
        exportCount += 1
        if (exportCount === 1) {
          throw new Error('download.saveAs: Target page, context or browser has been closed')
        }
        return '/app/data/downloads/result.xlsx'
      },
      wait: async () => {},
    },
  )

  assert.equal(result, '/app/data/downloads/result.xlsx')
  assert.equal(exportCount, 2)
  assert.equal(contexts.length, 2)
  assert.deepEqual(contexts.map((item) => item.closeCount), [1, 1])
})

test('业务错误不进行浏览器重试', async () => {
  let openCount = 0
  await assert.rejects(
    downloadSmartSheet(
      { export: { maxAttempts: 3, retryDelayMs: 0 } },
      { warn() {} },
      {
        openBrowserContext: async () => {
          openCount += 1
          return { async close() {} }
        },
        exportSmartSheet: async () => {
          throw new Error('企微登录已失效，请重新登录')
        },
        wait: async () => {},
      },
    ),
    /企微登录已失效/,
  )
  assert.equal(openCount, 1)
})

test('仅识别可恢复的浏览器下载错误', () => {
  assert.equal(isRecoverableBrowserError(new Error('Target page, context or browser has been closed')), true)
  assert.equal(isRecoverableBrowserError(new Error('download.saveAs: Download canceled')), true)
  assert.equal(isRecoverableBrowserError(new Error('触发企微Excel导出超时')), true)
  assert.equal(isRecoverableBrowserError(new Error('Timeout while waiting for event "download"')), true)
  assert.equal(isRecoverableBrowserError(new Error('企微浏览器登录目录正在被其他Chromium进程使用')), true)
  assert.equal(isRecoverableBrowserError(new Error('等待企微浏览器退出超时，请稍后重试')), true)
  assert.equal(isRecoverableBrowserError(new Error('没有成功打开右上角三道杠菜单')), true)
  assert.equal(isRecoverableBrowserError(new Error('企微导出的Excel文件在浏览器关闭前没有下载完整')), true)
  assert.equal(isRecoverableBrowserError(new Error('企微登录已失效，请重新登录')), false)
})
