const fs = require('fs')
const path = require('path')

const Database = require('better-sqlite3')

const { loadConfig } = require('../src/core/config')
const { parseLicenseSheet } = require('../src/excel/license-sheet-parser')

async function main() {
  const config = loadConfig()
  const latestFile = findLatestExcel(config.downloadDir)
  const parsed = await parseLicenseSheet(latestFile, config)
  const database = new Database(config.databasePath)
  database.pragma('journal_mode = WAL')
  database.pragma('foreign_keys = ON')
  database.pragma('busy_timeout = 5000')

  try {
    const invalidRows = database.prepare(`
      select id, content_hash, validation_error
      from source_row
      where source_id = ?
        and status = 'INVALID'
        and validation_error like '%个人邮箱%'
      order by id desc
    `).all(config.source.id)
    if (!invalidRows.length) {
      console.log('没有需要迁移的个人邮箱历史记录')
      return
    }

    const latestInvalidId = Number(invalidRows[0].id)
    const recordsByContentHash = new Map(parsed.records.map((item) => [item.contentHash, item]))
    const findByRowKey = database.prepare(`
      select id from source_row where source_id = ? and row_key = ? and id <> ? limit 1
    `)
    const update = database.prepare(`
      update source_row
      set row_key = ?, status = 'BASELINED', validation_error = null, updated_at = ?
      where id = ? and status = 'INVALID'
    `)
    const result = database.transaction(() => {
      let baselinedCount = 0
      let retainedCount = 0
      let unresolvedCount = 0
      for (const row of invalidRows) {
        const record = recordsByContentHash.get(row.content_hash)
        if (!record || record.validationError) {
          unresolvedCount += 1
          continue
        }
        if (Number(row.id) === latestInvalidId) {
          retainedCount += 1
          continue
        }
        if (findByRowKey.get(config.source.id, record.rowKey, row.id)) {
          unresolvedCount += 1
          continue
        }
        baselinedCount += update.run(record.rowKey, new Date().toISOString(), row.id).changes
      }
      return { baselinedCount, retainedCount, unresolvedCount }
    })()

    console.log(JSON.stringify({
      latestFile: path.basename(latestFile),
      latestInvalidId,
      ...result,
    }))
  } finally {
    database.close()
  }
}

function findLatestExcel(downloadDir) {
  const files = fs.readdirSync(downloadDir)
    .filter((item) => item.toLowerCase().endsWith('.xlsx'))
    .map((item) => ({
      path: path.join(downloadDir, item),
      modifiedAt: fs.statSync(path.join(downloadDir, item)).mtimeMs,
    }))
    .sort((left, right) => right.modifiedAt - left.modifiedAt)
  if (!files.length) {
    throw new Error('下载目录中没有可用于迁移的Excel文件')
  }
  return files[0].path
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
