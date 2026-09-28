const assert = require('node:assert/strict')
const http = require('node:http')
const test = require('node:test')

const { LicenseClient } = require('../src/license/license-client')

test('License客户端使用内部令牌并传递幂等请求编号', async () => {
  let received
  const server = http.createServer((request, response) => {
    const chunks = []
    request.on('data', (chunk) => chunks.push(chunk))
    request.on('end', () => {
      received = {
        headers: request.headers,
        body: JSON.parse(Buffer.concat(chunks).toString('utf8')),
      }
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ status: 200, data: { applyId: 123 } }))
    })
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const port = server.address().port
  process.env.TEST_LICENSE_INTERNAL_TOKEN = 'test-internal-token'
  try {
    const client = new LicenseClient({
      licenseApi: {
        enabled: true,
        baseUrl: `http://127.0.0.1:${port}`,
        applyPath: '/internal/license/openocta/applications',
        tokenEnv: 'TEST_LICENSE_INTERNAL_TOKEN',
        tokenHeader: 'X-License-Internal-Token',
        authScheme: '',
        idempotencyHeader: 'Idempotency-Key',
        externalRequestIdField: 'externalRequestId',
        applicationNoPath: 'data.applyId',
        successField: 'status',
        successValues: [200],
      },
    })
    const result = await client.apply({ company: '测试企业' }, 'request-key-1')
    assert.equal(result.applicationNo, 123)
    assert.equal(received.headers['x-license-internal-token'], 'test-internal-token')
    assert.equal(received.headers['idempotency-key'], 'request-key-1')
    assert.equal(received.body.externalRequestId, 'request-key-1')
  } finally {
    delete process.env.TEST_LICENSE_INTERNAL_TOKEN
    await new Promise((resolve) => server.close(resolve))
  }
})
