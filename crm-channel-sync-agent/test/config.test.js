const test = require('node:test')
const assert = require('node:assert/strict')

const { applyEnvironmentOverrides, validateConfig } = require('../src/core/config')

function sampleConfig() {
  return {
    schedules: ['0 9 * * *'],
    crmApi: {
      enabled: true,
      baseUrl: 'http://crm.example.com',
      importPath: '/api/internal/channel/source/import-excel',
      tokenEnv: 'CRM_CHANNEL_SYNC_INTERNAL_TOKEN',
    },
    sources: [{
      id: 'source-a',
      name: '渠道A',
      url: 'https://doc.weixin.qq.com/smartsheet/demo?tab=sheet-a',
    }],
  }
}

test('合法的多渠道配置可以通过校验', () => {
  const config = sampleConfig()
  config.sources.push({
    id: 'source-b',
    name: '渠道B',
    url: 'https://doc.weixin.qq.com/smartsheet/demo?tab=sheet-b',
  })
  assert.doesNotThrow(() => validateConfig(config))
})

test('重复的渠道编号会被拒绝', () => {
  const config = sampleConfig()
  config.sources.push({ ...config.sources[0], name: '重复渠道' })
  assert.throws(() => validateConfig(config), /渠道来源编号重复/)
})

test('环境变量可以覆盖CRM接口地址', () => {
  const previous = process.env.CRM_API_BASE_URL
  process.env.CRM_API_BASE_URL = 'http://new-crm.example.com'
  try {
    const config = applyEnvironmentOverrides(sampleConfig())
    assert.equal(config.crmApi.baseUrl, 'http://new-crm.example.com')
  } finally {
    if (previous === undefined) delete process.env.CRM_API_BASE_URL
    else process.env.CRM_API_BASE_URL = previous
  }
})
