const assert = require('node:assert/strict')
const test = require('node:test')

const { applyEnvironmentOverrides } = require('../src/core/config')

test('License接口地址和开关可以由环境变量覆盖', () => {
  process.env.LICENSE_API_ENABLED = 'true'
  process.env.LICENSE_API_BASE_URL = 'https://license.example.com'
  process.env.LICENSE_API_APPLY_PATH = '/internal/applications'
  process.env.LICENSE_API_TIMEOUT_MS = '45000'
  try {
    const config = applyEnvironmentOverrides({
      licenseApi: {
        enabled: false,
        baseUrl: 'http://127.0.0.1:8089',
        applyPath: '/old-path',
        timeoutMs: 30000,
      },
    })
    assert.equal(config.licenseApi.enabled, true)
    assert.equal(config.licenseApi.baseUrl, 'https://license.example.com')
    assert.equal(config.licenseApi.applyPath, '/internal/applications')
    assert.equal(config.licenseApi.timeoutMs, 45000)
  } finally {
    delete process.env.LICENSE_API_ENABLED
    delete process.env.LICENSE_API_BASE_URL
    delete process.env.LICENSE_API_APPLY_PATH
    delete process.env.LICENSE_API_TIMEOUT_MS
  }
})
