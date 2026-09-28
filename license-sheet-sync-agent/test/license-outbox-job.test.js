const assert = require('node:assert/strict')
const test = require('node:test')

const { applyRuntimeStaticFields } = require('../src/jobs/license-outbox-job')

test('重试任务使用当前产品和渠道静态配置', () => {
  const original = {
    company: '杭州乘云数字技术有限公司',
    productName: '旧产品',
  }
  const result = applyRuntimeStaticFields(original, {
    productName: 'OpenOcta',
    channelCode: '000',
    authType: '正式',
  })

  assert.deepEqual(result, {
    company: '杭州乘云数字技术有限公司',
    productName: 'OpenOcta',
    channelCode: '000',
    authType: '正式',
  })
  assert.equal(original.productName, '旧产品')
  assert.equal(original.channelCode, undefined)
})
