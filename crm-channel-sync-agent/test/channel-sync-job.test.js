const test = require('node:test')
const assert = require('node:assert/strict')

const {
  isRecoverableBrowserError,
  uploadWithRetry,
} = require('../src/jobs/channel-sync-job')

test('浏览器上下文关闭属于可重试错误', () => {
  assert.equal(
    isRecoverableBrowserError(new Error('Target page, context or browser has been closed')),
    true,
  )
})

test('CRM上传失败后会按配置重试', async () => {
  let attempts = 0
  const result = await uploadWithRetry(
    { crmApi: { maxAttempts: 3, retryDelayMs: 0 } },
    { name: '测试渠道' },
    'test.xlsx',
    { warn: () => {} },
    {
      uploadChannelFile: async () => {
        attempts += 1
        if (attempts < 3) throw new Error('临时失败')
        return { createdCount: 1 }
      },
      wait: async () => {},
    },
  )
  assert.equal(attempts, 3)
  assert.equal(result.createdCount, 1)
})
