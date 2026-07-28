import { useEffect, useState } from 'react'
import { CalendarDays, Edit2, MessageCircleMore, Plus, RefreshCw } from 'lucide-react'
import { api } from '../../api'
import { Button, Card, Field, Modal, RichTextEditor, RichTextViewer } from '../../components'
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
}

export function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
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
  }
}

export function toFollowupPayload(form) {
  return {
    ...form,
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
}) {
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)

  const load = async () => {
    if (!canView || !targetType || !targetId) return
    setLoading(true)
    try {
      const data = await api.followup.page({ targetType, targetId, pageNo: 1, pageSize })
      setPage(data || emptyPage)
    } catch (error) {
      notify(error.message || '跟进记录加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [canView, targetType, targetId])

  const save = async (form) => {
    if (!hasRichContent(form.content)) {
      notify('跟进内容不能为空', 'info')
      return
    }
    try {
      await api.followup.save(toFollowupPayload(form))
      notify('跟进记录已保存', 'success')
      setEditing(null)
      load()
    } catch (error) {
      notify(error.message || '跟进记录保存失败', 'info')
    }
  }

  const records = page.records || []

  return (
    <Card className="followup-panel">
      <div className="followup-panel-head">
        <div>
          <h2><MessageCircleMore size={18} />{title}</h2>
          <p>记录电话、微信、会议、拜访等真实销售动作。</p>
        </div>
        <div>
          <Button variant="secondary" icon={RefreshCw} onClick={load}>刷新</Button>
          {canWrite && (
            <Button icon={Plus} onClick={() => setEditing(buildTargetFollowupForm(targetType, targetId))}>写跟进</Button>
          )}
        </div>
      </div>
      {canView ? (
        <FollowupTimeline records={records} loading={loading} onEdit={canWrite ? setEditing : null} />
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

export function FollowupTimeline({ records = [], loading, onEdit }) {
  if (loading) {
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
            <RichTextViewer value={row.content} />
            {row.result && <em>结果：{row.result}</em>}
            {row.nextPlan && <em>下次计划：{row.nextPlan}</em>}
            {row.nextFollowTime && <em>下次跟进：{formatDateTime(row.nextFollowTime)}</em>}
            <div className="followup-meta">
              <small>{targetTypeText[row.targetType] || row.targetType}：{row.targetName || row.targetId}</small>
              <small>负责人：{ownerName(row)}</small>
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
          <RichTextEditor
            value={form.content || ''}
            onChange={(content) => update({ content })}
            placeholder="记录本次沟通内容，可插入图片、链接、列表和特殊符号"
            notify={notify}
          />
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
