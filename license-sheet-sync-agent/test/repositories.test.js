const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')

const { migrateDatabase, openInitializedDatabase } = require('../src/persistence/database')
const { LicenseOutboxRepository, SourceRowRepository } = require('../src/persistence/repositories')

test('相同行不会重复入队且成功申请后可追溯', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'license-sheet-db-'))
  const config = {
    rootDir: path.resolve(__dirname, '..'),
    databasePath: path.join(directory, 'test.db'),
  }
  const logger = { info: () => {} }
  try {
    migrateDatabase(config, logger)
    const database = openInitializedDatabase(config)
    try {
      const sourceRows = new SourceRowRepository(database)
      const outbox = new LicenseOutboxRepository(database)
      const record = {
        rowKey: 'row-key-1',
        contentHash: 'content-hash-1',
        rowNumber: 2,
        raw: { 公司名称: '测试企业' },
        validationError: '',
      }
      const created = sourceRows.ingest('source-1', record)
      assert.equal(created.action, 'CREATED')
      assert.equal(sourceRows.ingest('source-1', record).action, 'SKIPPED')

      outbox.enqueue(created.id, 'request-key-1', { companyName: '测试企业' })
      const claimed = outbox.claimBatch(10)
      assert.equal(claimed.length, 1)
      outbox.markSuccess(claimed[0], 'LICENSE-1', { code: 0 })

      const source = database.prepare('select status from source_row where id = ?').get(created.id)
      const application = database.prepare('select application_no from license_application').get()
      assert.equal(source.status, 'APPLIED')
      assert.equal(application.application_no, 'LICENSE-1')
      assert.equal(outbox.claimBatch(10).length, 0)
    } finally {
      database.close()
    }
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('首次基线数据不会进入License申请队列', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'license-sheet-baseline-'))
  const config = {
    rootDir: path.resolve(__dirname, '..'),
    databasePath: path.join(directory, 'test.db'),
  }
  const logger = { info: () => {} }
  try {
    migrateDatabase(config, logger)
    const database = openInitializedDatabase(config)
    try {
      const sourceRows = new SourceRowRepository(database)
      const record = {
        rowKey: 'history-row-key',
        contentHash: 'history-content-hash',
        rowNumber: 2,
        raw: { 公司名称: '历史测试企业' },
        validationError: '',
      }
      const result = sourceRows.ingest('source-1', record, { baseline: true })
      assert.equal(result.action, 'BASELINED')
      assert.equal(sourceRows.rowsWaitingForOutbox('source-1').length, 0)
      const source = database.prepare('select status from source_row where id = ?').get(result.id)
      assert.equal(source.status, 'BASELINED')

      const discoveredRecord = {
        rowKey: 'existing-discovered-row-key',
        contentHash: 'existing-discovered-content-hash',
        rowNumber: 3,
        raw: { 公司名称: '待建立基线企业' },
        validationError: '',
      }
      const discovered = sourceRows.ingest('source-1', discoveredRecord)
      assert.equal(discovered.action, 'CREATED')
      const converted = sourceRows.ingest('source-1', discoveredRecord, { baseline: true })
      assert.equal(converted.action, 'BASELINED')
      assert.equal(sourceRows.rowsWaitingForOutbox('source-1').length, 0)
    } finally {
      database.close()
    }
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('手工重试可以忽略自动退避时间', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'license-sheet-retry-'))
  const config = {
    rootDir: path.resolve(__dirname, '..'),
    databasePath: path.join(directory, 'test.db'),
  }
  const logger = { info: () => {} }
  try {
    migrateDatabase(config, logger)
    const database = openInitializedDatabase(config)
    try {
      const sourceRows = new SourceRowRepository(database)
      const outbox = new LicenseOutboxRepository(database)
      const created = sourceRows.ingest('source-1', {
        rowKey: 'retry-row-key',
        contentHash: 'retry-content-hash',
        rowNumber: 2,
        raw: { 公司名称: '真实企业' },
        validationError: '',
      })
      outbox.enqueue(created.id, 'retry-request-key', { company: '真实企业' })
      const firstAttempt = outbox.claimBatch(1)[0]
      outbox.markFailure(firstAttempt, '临时失败', 1)

      assert.equal(outbox.claimBatch(1).length, 0)
      assert.equal(outbox.claimBatch(1, true).length, 1)
    } finally {
      database.close()
    }
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
