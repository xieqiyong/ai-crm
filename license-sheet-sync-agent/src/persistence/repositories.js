const { nowText } = require('./database')

class SyncRepository {
  constructor(database) {
    this.database = database
  }

  startRun(sourceId, triggerType) {
    const result = this.database.prepare(`
      insert into sync_run(source_id, trigger_type, status, started_at)
      values (?, ?, 'RUNNING', ?)
    `).run(sourceId, triggerType, nowText())
    return Number(result.lastInsertRowid)
  }

  finishRun(runId, status, values = {}) {
    this.database.prepare(`
      update sync_run
      set status = ?, file_path = ?, file_hash = ?, total_count = ?, created_count = ?,
          changed_count = ?, skipped_count = ?, invalid_count = ?, baselined_count = ?,
          error_message = ?, finished_at = ?
      where id = ?
    `).run(
      status,
      values.filePath || null,
      values.fileHash || null,
      Number(values.totalCount || 0),
      Number(values.createdCount || 0),
      Number(values.changedCount || 0),
      Number(values.skippedCount || 0),
      Number(values.invalidCount || 0),
      Number(values.baselinedCount || 0),
      values.errorMessage || null,
      nowText(),
      runId,
    )
  }

  latestRun(sourceId) {
    return this.database.prepare(`
      select * from sync_run where source_id = ? order by started_at desc limit 1
    `).get(sourceId)
  }
}

class SourceRowRepository {
  constructor(database) {
    this.database = database
  }

  ingest(sourceId, item, options = {}) {
    const baseline = Boolean(options.baseline)
    const existing = this.database.prepare(`
      select r.id, r.content_hash, r.status,
             (select o.status from license_outbox o where o.source_row_id = r.id limit 1) as outbox_status
      from source_row r
      where r.source_id = ? and r.row_key = ?
      limit 1
    `).get(sourceId, item.rowKey)
    const now = nowText()
    if (!existing) {
      const result = this.database.prepare(`
        insert into source_row(
          source_id, row_key, content_hash, row_number, raw_json, status,
          validation_error, first_seen_at, last_seen_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(
        sourceId,
        item.rowKey,
        item.contentHash,
        item.rowNumber,
        JSON.stringify(item.raw),
        item.validationError ? 'INVALID' : (baseline ? 'BASELINED' : 'DISCOVERED'),
        item.validationError || null,
        now,
        now,
        now,
      )
      return {
        action: item.validationError ? 'INVALID' : (baseline ? 'BASELINED' : 'CREATED'),
        id: Number(result.lastInsertRowid),
      }
    }
    if (existing.content_hash === item.contentHash) {
      if (baseline && existing.status === 'DISCOVERED' && !existing.outbox_status) {
        this.database.prepare(`
          update source_row set status = 'BASELINED', last_seen_at = ?, updated_at = ? where id = ?
        `).run(now, now, existing.id)
        return { action: 'BASELINED', id: existing.id }
      }
      this.database.prepare('update source_row set last_seen_at = ? where id = ?').run(now, existing.id)
      return { action: existing.status === 'INVALID' ? 'INVALID' : 'SKIPPED', id: existing.id }
    }
    const nextStatus = item.validationError
      ? 'INVALID'
      : (existing.outbox_status ? 'CHANGED' : (baseline ? 'BASELINED' : 'DISCOVERED'))
    this.database.transaction(() => {
      this.database.prepare(`
        update source_row
        set content_hash = ?, row_number = ?, raw_json = ?, status = ?,
            validation_error = ?, last_seen_at = ?, updated_at = ?
        where id = ?
      `).run(
        item.contentHash,
        item.rowNumber,
        JSON.stringify(item.raw),
        nextStatus,
        item.validationError || null,
        now,
        now,
        existing.id,
      )
      if (existing.outbox_status && existing.outbox_status !== 'SUCCESS') {
        this.database.prepare(`
          update license_outbox
          set status = 'MANUAL_REVIEW', last_error = '源数据在提交前发生变化，请人工确认', updated_at = ?
          where source_row_id = ?
        `).run(now, existing.id)
      }
    })()
    return {
      action: item.validationError ? 'INVALID' : (baseline && !existing.outbox_status ? 'BASELINED' : 'CHANGED'),
      id: existing.id,
    }
  }

  rowsWaitingForOutbox(sourceId, limit = 500) {
    return this.database.prepare(`
      select r.*
      from source_row r
      left join license_outbox o on o.source_row_id = r.id
      where r.source_id = ? and r.status = 'DISCOVERED' and o.id is null
      order by r.id asc
      limit ?
    `).all(sourceId, limit)
  }
}

class LicenseOutboxRepository {
  constructor(database) {
    this.database = database
  }

  enqueue(sourceRowId, idempotencyKey, payload) {
    const now = nowText()
    this.database.transaction(() => {
      this.database.prepare(`
        insert into license_outbox(
          source_row_id, idempotency_key, request_json, status,
          attempt_count, created_at, updated_at
        ) values (?, ?, ?, 'PENDING', 0, ?, ?)
      `).run(sourceRowId, idempotencyKey, JSON.stringify(payload), now, now)
      this.database.prepare(`
        update source_row set status = 'PENDING', updated_at = ? where id = ?
      `).run(now, sourceRowId)
    })()
  }

  recoverStaleSubmitting(staleMinutes = 10) {
    const threshold = new Date(Date.now() - Math.max(1, Number(staleMinutes)) * 60 * 1000).toISOString()
    const now = nowText()
    return this.database.transaction(() => {
      const rows = this.database.prepare(`
        select id, source_row_id
        from license_outbox
        where status = 'SUBMITTING' and updated_at <= ?
      `).all(threshold)
      if (!rows.length) return 0
      const updateOutbox = this.database.prepare(`
        update license_outbox
        set status = 'RETRY_WAIT', next_retry_at = ?,
            last_error = '上次提交过程异常中断，已进入幂等重试', updated_at = ?
        where id = ? and status = 'SUBMITTING'
      `)
      const updateSource = this.database.prepare(`
        update source_row set status = 'RETRY_WAIT', updated_at = ? where id = ?
      `)
      let recovered = 0
      for (const row of rows) {
        const result = updateOutbox.run(now, now, row.id)
        if (result.changes > 0) {
          updateSource.run(now, row.source_row_id)
          recovered += 1
        }
      }
      return recovered
    })()
  }

  claimBatch(limit = 20, force = false) {
    const now = nowText()
    const retryCondition = force ? '' : 'and (o.next_retry_at is null or o.next_retry_at <= ?)'
    const allowedStatuses = force ? "('PENDING', 'RETRY_WAIT', 'FAILED')" : "('PENDING', 'RETRY_WAIT')"
    const statement = this.database.prepare(`
      select o.*, r.source_id, r.row_key
      from license_outbox o
      inner join source_row r on r.id = o.source_row_id
      where o.status in ${allowedStatuses}
        ${retryCondition}
      order by o.id asc
      limit ?
    `)
    const rows = force ? statement.all(limit) : statement.all(now, limit)
    const update = this.database.prepare(`
      update license_outbox
      set status = 'SUBMITTING', attempt_count = attempt_count + 1, updated_at = ?
      where id = ? and status in ${allowedStatuses}
    `)
    return this.database.transaction(() => {
      const claimed = []
      for (const row of rows) {
        const result = update.run(now, row.id)
        if (result.changes > 0) {
          claimed.push({ ...row, attempt_count: Number(row.attempt_count || 0) + 1 })
        }
      }
      return claimed
    })()
  }

  markSuccess(item, applicationNo, response) {
    const now = nowText()
    this.database.transaction(() => {
      this.database.prepare(`
        update license_outbox
        set status = 'SUCCESS', next_retry_at = null, last_error = null, updated_at = ?
        where id = ?
      `).run(now, item.id)
      this.database.prepare(`
        update source_row set status = 'APPLIED', updated_at = ? where id = ?
      `).run(now, item.source_row_id)
      this.database.prepare(`
        insert into license_application(outbox_id, source_row_id, application_no, response_json, created_at)
        values (?, ?, ?, ?, ?)
      `).run(item.id, item.source_row_id, applicationNo || null, JSON.stringify(response), now)
    })()
  }

  markFailure(item, errorMessage, maxAttempts) {
    const attempts = Number(item.attempt_count || 0)
    const exhausted = attempts >= maxAttempts
    const retryAt = exhausted ? null : new Date(Date.now() + retryDelayMs(attempts)).toISOString()
    const status = exhausted ? 'FAILED' : 'RETRY_WAIT'
    const now = nowText()
    this.database.transaction(() => {
      this.database.prepare(`
        update license_outbox
        set status = ?, next_retry_at = ?, last_error = ?, updated_at = ?
        where id = ?
      `).run(status, retryAt, shrink(errorMessage, 2000), now, item.id)
      this.database.prepare(`
        update source_row set status = ?, updated_at = ? where id = ?
      `).run(status, now, item.source_row_id)
    })()
  }
}

function retryDelayMs(attempt) {
  const minutes = Math.min(5 * Math.pow(2, Math.max(attempt - 1, 0)), 360)
  return minutes * 60 * 1000
}

function shrink(value, length) {
  const text = String(value || '')
  return text.length > length ? text.slice(0, length) : text
}

module.exports = {
  LicenseOutboxRepository,
  SourceRowRepository,
  SyncRepository,
}
