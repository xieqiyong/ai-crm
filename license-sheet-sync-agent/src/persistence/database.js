const fs = require('fs')
const path = require('path')
const Database = require('better-sqlite3')

function migrateDatabase(config, logger) {
  fs.mkdirSync(path.dirname(config.databasePath), { recursive: true })
  const database = openDatabase(config.databasePath, false)
  try {
    database.exec(`
      create table if not exists schema_migration (
        version text primary key,
        applied_at text not null
      )
    `)
    const migrationDir = path.join(config.rootDir, 'migrations')
    const files = fs.readdirSync(migrationDir)
      .filter((item) => item.endsWith('.sql'))
      .sort()
    for (const fileName of files) {
      const existing = database.prepare('select version from schema_migration where version = ?').get(fileName)
      if (existing) continue
      const sql = fs.readFileSync(path.join(migrationDir, fileName), 'utf8')
      database.transaction(() => {
        database.exec(sql)
        database.prepare('insert into schema_migration(version, applied_at) values (?, ?)')
          .run(fileName, nowText())
      })()
      logger.info('数据库迁移完成', { version: fileName })
    }
  } finally {
    database.close()
  }
}

function openInitializedDatabase(config) {
  if (!fs.existsSync(config.databasePath)) {
    throw new Error('同步数据库尚未初始化，请先执行 npm run migrate')
  }
  const database = openDatabase(config.databasePath, true)
  try {
    database.prepare('select version from schema_migration limit 1').get()
  } catch (error) {
    database.close()
    throw new Error('同步数据库结构不完整，请先执行 npm run migrate')
  }
  return database
}

function openDatabase(databasePath, fileMustExist) {
  const database = new Database(databasePath, { fileMustExist })
  database.pragma('journal_mode = WAL')
  database.pragma('foreign_keys = ON')
  database.pragma('busy_timeout = 5000')
  return database
}

function nowText() {
  return new Date().toISOString()
}

module.exports = { migrateDatabase, nowText, openInitializedDatabase }
