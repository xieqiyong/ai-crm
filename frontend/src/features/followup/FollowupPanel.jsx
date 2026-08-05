import { useEffect, useRef, useState } from 'react'
import { CalendarDays, Edit2, FileAudio2, FileVideo2, MessageCircleMore, Paperclip, Plus, RefreshCw, X } from 'lucide-react'
import { api } from '../../api'
import {
  Button,
  Card,
  CollapsibleRichText,
  Field,
  Modal,
  RichTextEditor,
  RichTextViewer,
} from '../../components'
import { ownerName } from '../../hooks/useOwnerOptions'

export const followupTypeText = {
  PHONE: '电话',
  WECHAT: '微信',
  EMAIL: '邮件',
  MEETING: '会议',
  VISIT: '拜访',
  OTHER: '其他',
}

export const targetTypeText = {
  LEAD: '线索',
  CUSTOMER: '客户',
  OPPORTUNITY: '商机',
}

const transcriptionStatusText = {
  UPLOADING: '上传中',
  PENDING: '待处理',
  EXTRACTING: '抽取音频',
  READY: '等待提交',
  SUBMITTED: '已提交',
  PROCESSING: '转写中',
  SUCCESS: '已完成',
  FAILED: '失败',
}

const pendingTranscriptionStatuses = new Set(['PENDING'])

const runningTranscriptionStatuses = new Set(['EXTRACTING', 'READY', 'SUBMITTED', 'PROCESSING'])

const uploadingTranscriptionStatuses = new Set(['UPLOADING'])

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 10,
  records: [],
}

export const emptyFollowupForm = {
  targetType: 'CUSTOMER',
  targetId: '',
  followupType: 'PHONE',
  followupAt: '',
  content: '',
  result: '',
  nextPlan: '',
  nextFollowTime: '',
  ownerId: '',
  mediaFiles: [],
}

export function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatFileSize(value) {
  const size = Number(value || 0)
  if (!size) return '-'
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)}MB`
  if (size >= 1024) return `${(size / 1024).toFixed(1)}KB`
  return `${size}B`
}

function isVideoFile(file) {
  const type = String(file?.type || '').toLowerCase()
  const name = String(file?.name || '').toLowerCase()
  return type.startsWith('video/') || /\.(mp4|mov|mkv|webm|avi)$/.test(name)
}

function hasRunningTranscriptions(records) {
  return (records || []).some((record) => (record.mediaTranscriptions || []).some((item) => (
    runningTranscriptionStatuses.has(item.status) || uploadingTranscriptionStatuses.has(item.status)
  )))
}

function hasPendingTranscriptions(records) {
  return (records || []).some((record) => (record.mediaTranscriptions || []).some((item) => (
    pendingTranscriptionStatuses.has(item.status)
  )))
}

async function uploadFollowupMedia(followupId, mediaFiles = []) {
  const files = Array.from(mediaFiles || [])
  if (!followupId || !files.length) return []
  const results = []
  for (const file of files) {
    results.push(await api.followup.uploadMedia(followupId, file))
  }
  return results
}

export function startFollowupMediaUpload(followupId, mediaFiles = [], notify, onDone, onFailed) {
  const files = Array.from(mediaFiles || [])
  if (!followupId || !files.length) return false
  uploadFollowupMedia(followupId, files)
    .then(() => {
      notify?.('音视频上传完成，转写任务已创建', 'success')
      onDone?.()
    })
    .catch((error) => {
      notify?.(error.message || '音视频上传失败', 'info')
      onFailed?.(error)
    })
  return true
}

export function buildLocalMediaUploads(mediaFiles = [], status = 'UPLOADING', errorMessage = '') {
  const files = Array.from(mediaFiles || [])
  const now = Date.now()
  return files.map((file, index) => ({
    id: `local-media-${now}-${index}-${file.name}`,
    fileName: file.name,
    contentType: file.type,
    fileSize: file.size,
    status,
    progress: status === 'FAILED' ? 100 : 8,
    errorMessage,
    localUpload: true,
  }))
}

export function mergeFollowupMediaUploads(records = [], localMediaUploads = {}) {
  return (records || []).map((record) => {
    const localItems = localMediaUploads[String(record.id)] || []
    if (!localItems.length) {
      return record
    }
    const mediaTranscriptions = Array.isArray(record.mediaTranscriptions) ? record.mediaTranscriptions : []
    return {
      ...record,
      mediaTranscriptions: [...localItems, ...mediaTranscriptions],
    }
  })
}

function toDateTimeInput(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

export function toFollowupForm(row) {
  return {
    id: row.id,
    targetType: row.targetType || 'CUSTOMER',
    targetId: row.targetId || '',
    followupType: row.followupType || 'PHONE',
    followupAt: toDateTimeInput(row.followupAt),
    content: row.content || '',
    result: row.result || '',
    nextPlan: row.nextPlan || '',
    nextFollowTime: toDateTimeInput(row.nextFollowTime),
    ownerId: row.ownerId || '',
    mediaFiles: [],
  }
}

export function toFollowupPayload(form) {
  return {
    id: form.id,
    targetType: form.targetType || 'CUSTOMER',
    targetId: form.targetId || null,
    followupType: form.followupType || 'PHONE',
    followupAt: form.followupAt || null,
    content: (form.content || '').trim(),
    result: form.result || null,
    nextPlan: form.nextPlan || null,
    nextFollowTime: form.nextFollowTime || null,
    ownerId: form.ownerId || null,
  }
}

export function hasRichContent(value) {
  const html = String(value || '')
  if (/<img[\s>]/i.test(html)) return true
  return html
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim()
    .length > 0
}

export function buildTargetFollowupForm(targetType, targetId) {
  return {
    ...emptyFollowupForm,
    targetType,
    targetId,
    followupAt: toDateTimeInput(new Date().toISOString()),
  }
}

export function FollowupPanel({
  targetType,
  targetId,
  title = '跟进记录',
  canView = true,
  canWrite,
  notify,
  pageSize = 8,
  compact = false,
}) {
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)
  const [visibleSize, setVisibleSize] = useState(pageSize)
  const [localMediaUploads, setLocalMediaUploads] = useState({})
  const pendingPollingCountRef = useRef(0)

  const load = async (requestedPageSize = visibleSize, options = {}) => {
    if (!canView || !targetType || !targetId) return
    const silent = Boolean(options.silent)
    if (!silent) {
      setLoading(true)
    }
    try {
      const data = await api.followup.page({
        targetType,
        targetId,
        pageNo: 1,
        pageSize: requestedPageSize,
      })
      setPage(data || emptyPage)
    } catch (error) {
      if (!silent) {
        notify(error.message || '跟进记录加载失败', 'info')
      }
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }

  const appendLocalUploads = (followupId, mediaFiles) => {
    const items = buildLocalMediaUploads(mediaFiles)
    if (!followupId || !items.length) return
    setLocalMediaUploads((current) => ({
      ...current,
      [String(followupId)]: [...(current[String(followupId)] || []), ...items],
    }))
  }

  const clearLocalUploads = (followupId) => {
    if (!followupId) return
    setLocalMediaUploads((current) => {
      const next = { ...current }
      delete next[String(followupId)]
      return next
    })
  }

  const failLocalUploads = (followupId, error) => {
    if (!followupId) return
    setLocalMediaUploads((current) => {
      const items = current[String(followupId)] || []
      if (!items.length) return current
      return {
        ...current,
        [String(followupId)]: items.map((item) => ({
          ...item,
          status: 'FAILED',
          progress: 100,
          errorMessage: error?.message || '音视频上传失败',
        })),
      }
    })
  }

  const startMediaUploadForFollowup = (followupId, mediaFiles) => {
    const files = Array.from(mediaFiles || [])
    if (!followupId || !files.length) return false
    appendLocalUploads(followupId, files)
    return startFollowupMediaUpload(
      followupId,
      files,
      notify,
      () => {
        pendingPollingCountRef.current = 0
        load(visibleSize, { silent: true }).finally(() => clearLocalUploads(followupId))
      },
      (error) => failLocalUploads(followupId, error),
    )
  }

  useEffect(() => {
    pendingPollingCountRef.current = 0
    setVisibleSize(pageSize)
    setLocalMediaUploads({})
    if (!canView || !targetType || !targetId) {
      setPage(emptyPage)
      return
    }
    load(pageSize)
  }, [canView, targetType, targetId, pageSize])

  useEffect(() => {
    const records = mergeFollowupMediaUploads(page.records || [], localMediaUploads)
    const active = hasRunningTranscriptions(records)
    const pending = hasPendingTranscriptions(records)
    if (!canView || !targetType || !targetId || (!active && !pending)) {
      pendingPollingCountRef.current = 0
      return undefined
    }
    if (!active && pendingPollingCountRef.current >= 6) {
      return undefined
    }
    const timer = window.setInterval(() => {
      if (!active) {
        pendingPollingCountRef.current += 1
      }
      load(visibleSize, { silent: true })
    }, 8000)
    return () => window.clearInterval(timer)
  }, [canView, targetType, targetId, visibleSize, page.records, localMediaUploads])

  const save = async (form) => {
    if (!hasRichContent(form.content)) {
      notify('跟进内容不能为空', 'info')
      return
    }
    try {
      const saved = await api.followup.save(toFollowupPayload(form))
      const mediaStarted = startMediaUploadForFollowup(saved?.id || form.id, form.mediaFiles)
      notify(mediaStarted ? '跟进记录已保存，音视频正在后台上传' : '跟进记录已保存', 'success')
      setEditing(null)
      load(visibleSize, { silent: mediaStarted })
    } catch (error) {
      notify(error.message || '跟进记录保存失败', 'info')
    }
  }

  const uploadExistingMedia = async (row, files) => {
    const mediaFiles = Array.from(files || [])
    if (!row?.id || !mediaFiles.length) return
    try {
      startMediaUploadForFollowup(row.id, mediaFiles)
      notify('音视频正在后台上传', 'success')
      load(visibleSize, { silent: true })
    } catch (error) {
      notify(error.message || '音视频上传失败', 'info')
    }
  }

  const records = mergeFollowupMediaUploads(page.records || [], localMediaUploads)
  const total = Number(page.total || 0)
  const hasMore = compact && records.length < total
  const showMore = () => {
    const nextSize = Math.min(100, visibleSize + pageSize)
    if (nextSize <= visibleSize) return
    setVisibleSize(nextSize)
    load(nextSize)
  }

  return (
    <Card className={`followup-panel ${compact ? 'compact' : ''}`}>
      <div className="followup-panel-head">
        <div>
          <h2><MessageCircleMore size={18} />{title}</h2>
          <p>
            记录电话、微信、会议、拜访等真实销售动作。
            {compact && total > 0 && ` 当前展示最新 ${records.length} 条，共 ${total} 条。`}
          </p>
        </div>
        <div>
          <Button variant="secondary" icon={RefreshCw} onClick={() => load(visibleSize)}>刷新</Button>
          {canWrite && (
            <Button icon={Plus} onClick={() => setEditing(buildTargetFollowupForm(targetType, targetId))}>写跟进</Button>
          )}
        </div>
      </div>
      {canView ? (
        <>
          <FollowupTimeline
            records={records}
            loading={loading}
            compact={compact}
            onEdit={canWrite ? setEditing : null}
            onUploadMedia={canWrite ? uploadExistingMedia : null}
          />
          {hasMore && (
            <button
              type="button"
              className="followup-load-more"
              disabled={loading || visibleSize >= 100}
              onClick={showMore}
            >
              {visibleSize >= 100 ? '更多记录请在跟进记录页查看' : `查看更多跟进（剩余 ${total - records.length} 条）`}
            </button>
          )}
        </>
      ) : (
        <div className="followup-empty">
          <MessageCircleMore size={24} />
          <b>暂无查看权限</b>
          <span>需要跟进查看权限后才能读取跟进记录。</span>
        </div>
      )}
      <FollowupFormModal
        open={Boolean(editing)}
        fixedTarget
        form={editing || buildTargetFollowupForm(targetType, targetId)}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={save}
        notify={notify}
      />
    </Card>
  )
}

export function FollowupTimeline({ records = [], loading, compact = false, onEdit, onUploadMedia }) {
  if (loading && !records.length) {
    return (
      <div className="followup-empty">
        <RefreshCw size={24} />
        <b>正在加载跟进记录</b>
      </div>
    )
  }
  if (!records.length) {
    return (
      <div className="followup-empty">
        <MessageCircleMore size={24} />
        <b>暂无跟进记录</b>
        <span>可以先写一次电话、微信或会议跟进。</span>
      </div>
    )
  }
  return (
    <div className="followup-timeline">
      {records.map((row) => (
        <div className="followup-item" key={row.id}>
          <span><CalendarDays size={15} /></span>
          <div>
            <div className="followup-item-head">
              <b>{followupTypeText[row.followupType] || row.followupType || '跟进'}</b>
              <small>{formatDateTime(row.followupAt)}</small>
            </div>
            {compact
              ? <CollapsibleRichText value={row.content} maxHeight={132} />
              : <RichTextViewer value={row.content} />}
            {row.result && <em>结果：{row.result}</em>}
            {row.nextPlan && <em>下次计划：{row.nextPlan}</em>}
            {row.nextFollowTime && <em>下次跟进：{formatDateTime(row.nextFollowTime)}</em>}
            <FollowupMediaTranscriptions items={row.mediaTranscriptions} />
            <div className="followup-meta">
              <small>{targetTypeText[row.targetType] || row.targetType}：{row.targetName || row.targetId}</small>
              <small>负责人：{ownerName(row)}</small>
              {onUploadMedia && (
                <label className="text-action followup-inline-upload">
                  <Paperclip size={14} />上传音视频
                  <input
                    type="file"
                    multiple
                    accept="audio/*,video/*"
                    onChange={(event) => {
                      const files = event.target.files
                      event.target.value = ''
                      onUploadMedia(row, files)
                    }}
                  />
                </label>
              )}
              {onEdit && (
                <button className="text-action" onClick={() => onEdit(toFollowupForm(row))}>
                  <Edit2 size={14} />编辑
                </button>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

export function FollowupMediaTranscriptions({ items = [], compact = false }) {
  const records = Array.isArray(items) ? items : []
  if (!records.length) return null
  return (
    <div className={`followup-media-results ${compact ? 'compact' : ''}`}>
      {records.map((item) => {
        const success = item.status === 'SUCCESS'
        const failed = item.status === 'FAILED'
        const uploading = uploadingTranscriptionStatuses.has(item.status)
        const running = runningTranscriptionStatuses.has(item.status)
          || pendingTranscriptionStatuses.has(item.status)
          || uploading
        const Icon = isVideoFile({ type: item.contentType, name: item.fileName }) ? FileVideo2 : FileAudio2
        return (
          <div
            className={`followup-media-result ${success ? 'success' : ''} ${failed ? 'failed' : ''} ${uploading ? 'uploading' : ''}`}
            key={item.id}
          >
            <div className="followup-media-result-head">
              <span><Icon size={15} />{item.fileName || '音视频文件'}</span>
              <small>{transcriptionStatusText[item.status] || item.status || '待处理'}</small>
            </div>
            {running && (
              <div className="followup-media-progress">
                <i style={{ width: `${Math.max(5, Number(item.progress || 0))}%` }} />
              </div>
            )}
            {failed && item.errorMessage && <p className="followup-media-error">{item.errorMessage}</p>}
            {success && item.transcriptText && !compact && (
              <div className="followup-media-text">
                <b>转写文本</b>
                <p>{item.transcriptText}</p>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

export function FollowupFormModal({
  open,
  form,
  fixedTarget = false,
  onChange,
  onClose,
  onSave,
  notify,
}) {
  if (!open || !form) return null
  const update = (patch) => onChange({ ...form, ...patch })
  const mediaFiles = Array.from(form.mediaFiles || [])
  const chooseMediaFiles = (event) => {
    const files = Array.from(event.target.files || [])
    event.target.value = ''
    if (!files.length) return
    update({ mediaFiles: [...mediaFiles, ...files] })
  }
  const removeMediaFile = (index) => {
    update({ mediaFiles: mediaFiles.filter((_, itemIndex) => itemIndex !== index) })
  }
  return (
    <Modal
      open={open}
      title={form.id ? '编辑跟进记录' : '写跟进记录'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={() => onSave(form)}>保存</Button>
        </>
      )}
    >
      <div className="customer-form-grid followup-form-grid">
        {!fixedTarget && (
          <>
            <Field label="关联对象类型" required>
              <select value={form.targetType || 'CUSTOMER'} onChange={(event) => update({ targetType: event.target.value })}>
                {Object.entries(targetTypeText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
              </select>
            </Field>
            <Field label="关联对象ID" required>
              <input value={form.targetId || ''} onChange={(event) => update({ targetId: event.target.value })} />
            </Field>
          </>
        )}
        <Field label="跟进方式">
          <select value={form.followupType || 'PHONE'} onChange={(event) => update({ followupType: event.target.value })}>
            {Object.entries(followupTypeText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </Field>
        <Field label="跟进时间">
          <input type="datetime-local" value={form.followupAt || ''} onChange={(event) => update({ followupAt: event.target.value })} />
        </Field>
        <Field label="跟进内容" required className="wide-field">
          <div className="followup-content-editor">
            <RichTextEditor
              value={form.content || ''}
              onChange={(content) => update({ content })}
              placeholder="记录本次沟通内容，可插入图片、链接、列表和特殊符号"
              notify={notify}
            />
            <div className="followup-media-picker embedded">
              <label className="followup-media-upload">
                <Paperclip size={16} />
                <span>添加会议录音或视频</span>
                <small>支持 mp3、wav、m4a、mp4、mov 等格式，保存后自动挂到这条跟进并创建转写任务</small>
                <input type="file" multiple accept="audio/*,video/*" onChange={chooseMediaFiles} />
              </label>
              {mediaFiles.length > 0 && (
                <div className="followup-media-files">
                  {mediaFiles.map((file, index) => {
                    const Icon = isVideoFile(file) ? FileVideo2 : FileAudio2
                    return (
                      <div key={`${file.name}-${file.size}-${index}`}>
                        <Icon size={15} />
                        <span>{file.name}</span>
                        <small>{formatFileSize(file.size)}</small>
                        <button type="button" onClick={() => removeMediaFile(index)}><X size={13} /></button>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        </Field>
        <Field label="跟进结果">
          <textarea rows="3" value={form.result || ''} onChange={(event) => update({ result: event.target.value })} />
        </Field>
        <Field label="下次计划">
          <textarea rows="3" value={form.nextPlan || ''} onChange={(event) => update({ nextPlan: event.target.value })} />
        </Field>
        <Field label="下次跟进时间">
          <input
            type="datetime-local"
            value={form.nextFollowTime || ''}
            onChange={(event) => update({ nextFollowTime: event.target.value })}
          />
        </Field>
      </div>
    </Modal>
  )
}
