import { useEffect, useState } from 'react'
import { CheckCircle2, ClipboardCheck, Plus, RefreshCw, Search } from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyPermission,
  Field,
  Modal,
  OwnerAssignModal,
  PageHeader,
  Select,
  useConfirmDialog,
} from '../../components'
import { ownerName, ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 10,
  records: [],
}

const emptyForm = {
  title: '',
  content: '',
  targetType: 'GENERAL',
  targetId: '',
  targetName: '',
  ownerId: '',
  dueAt: '',
  reminderAt: '',
  priority: 'MEDIUM',
}

const statusText = {
  PENDING: '待处理',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  OVERDUE: '已逾期',
  CANCELLED: '已取消',
}

const statusTone = {
  PENDING: 'neutral',
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  OVERDUE: 'danger',
  CANCELLED: 'neutral',
}

const priorityText = {
  HIGH: '高优先级',
  MEDIUM: '中优先级',
  LOW: '低优先级',
}

const priorityTone = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'neutral',
}

const targetTypeText = {
  LEAD: '线索',
  CUSTOMER: '客户',
  OPPORTUNITY: '商机',
  CHANNEL: '渠道',
  GENERAL: '通用任务',
}

const sourceText = {
  MANUAL: '手动创建',
  FOLLOWUP: '跟进生成',
  AI_SUGGESTION: 'AI建议',
  SYSTEM: '系统生成',
}

const statusOptions = Object.entries(statusText).map(([value, label]) => ({ value, label }))
const priorityOptions = Object.entries(priorityText).map(([value, label]) => ({ value, label }))
const targetTypeOptions = Object.entries(targetTypeText).map(([value, label]) => ({ value, label }))

function formatDateTime(value) {
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

function defaultDueAt() {
  const date = new Date()
  date.setHours(date.getHours() + 2)
  date.setMinutes(0, 0, 0)
  return toDateTimeInput(date.toISOString())
}

function compactQuery(query) {
  return {
    pageNo: query.pageNo || 1,
    pageSize: query.pageSize || 10,
    keyword: query.keyword || undefined,
    status: query.status || undefined,
    priority: query.priority || undefined,
    ownerId: query.ownerId || undefined,
  }
}

function toForm(row) {
  return {
    id: row.id,
    title: row.title || '',
    content: row.content || '',
    targetType: row.targetType || 'GENERAL',
    targetId: row.targetId || '',
    targetName: row.targetName || '',
    ownerId: row.ownerId || '',
    dueAt: toDateTimeInput(row.dueAt),
    reminderAt: toDateTimeInput(row.reminderAt),
    priority: row.priority || 'MEDIUM',
  }
}

function toPayload(form) {
  return {
    id: form.id || null,
    title: (form.title || '').trim(),
    content: (form.content || '').trim() || null,
    targetType: form.targetType || 'GENERAL',
    targetId: form.targetId || null,
    targetName: (form.targetName || '').trim() || null,
    ownerId: form.ownerId || null,
    dueAt: form.dueAt || null,
    reminderAt: form.reminderAt || null,
    priority: form.priority || 'MEDIUM',
  }
}

function isFinished(row) {
  return row.status === 'COMPLETED' || row.status === 'CANCELLED'
}

function resolveOwnerOptions(ownerOptions) {
  return ownerOptions.map((item) => ({
    value: item.id,
    label: ownerOptionLabel(item),
    description: item.departmentName || item.username || '',
  }))
}

export function SalesTaskPage({ can, notify, navigate, currentUser }) {
  const canView = can('crm:task:view')
  const canCreate = can('crm:task:create') || can('crm:task:manage')
  const canManage = can('crm:task:manage')
  const canAssign = can('crm:task:assign') || canManage
  const ownerOptions = useOwnerOptions(notify)
  const ownerSelectOptions = resolveOwnerOptions(ownerOptions)
  const { confirm, dialogProps } = useConfirmDialog()
  const [query, setQuery] = useState({ keyword: '', status: '', priority: '', ownerId: '', pageNo: 1, pageSize: 10 })
  const [page, setPage] = useState(emptyPage)
  const [jumpPage, setJumpPage] = useState('1')
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)
  const [assigning, setAssigning] = useState(null)
  const [assignSubmitting, setAssignSubmitting] = useState(false)

  const load = async (nextQuery = query) => {
    if (!canView) return
    setLoading(true)
    try {
      const data = await api.task.page(compactQuery(nextQuery))
      setPage(data || emptyPage)
      setQuery({
        ...nextQuery,
        pageNo: data?.pageNo || nextQuery.pageNo || 1,
        pageSize: data?.pageSize || 10,
      })
    } catch (error) {
      notify(error.message || '销售任务加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 10
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  useEffect(() => {
    setJumpPage(String(currentPage))
  }, [currentPage])

  const search = (event) => {
    event.preventDefault()
    load({ ...query, pageNo: 1 })
  }

  const goToPage = (targetPage) => {
    if (loading || targetPage === currentPage) return
    load({ ...query, pageNo: targetPage })
  }

  const jumpToPage = (event) => {
    event.preventDefault()
    const targetPage = Number.parseInt(jumpPage, 10)
    if (!Number.isInteger(targetPage) || targetPage < 1 || targetPage > totalPages) {
      notify(`请输入 1 到 ${totalPages} 之间的页码`, 'info')
      return
    }
    goToPage(targetPage)
  }

  const openCreate = () => {
    setEditing({
      ...emptyForm,
      ownerId: currentUser?.userId || '',
      dueAt: defaultDueAt(),
    })
  }

  const save = async (form) => {
    if (!(form.title || '').trim()) {
      notify('任务标题不能为空', 'info')
      return
    }
    if (!form.dueAt) {
      notify('到期时间不能为空', 'info')
      return
    }
    try {
      await api.task.save(toPayload(form))
      notify('销售任务已保存', 'success')
      setEditing(null)
      load({ ...query, pageNo: form.id ? query.pageNo : 1 })
    } catch (error) {
      notify(error.message || '销售任务保存失败', 'info')
    }
  }

  const start = async (row) => {
    try {
      await api.task.start(row.id)
      notify('任务已开始', 'success')
      load(query)
    } catch (error) {
      notify(error.message || '任务开始失败', 'info')
    }
  }

  const complete = async (row) => {
    const confirmed = await confirm({
      title: '完成销售任务',
      description: '完成后会计入今日任务完成排行。',
      target: row.title,
      tone: 'primary',
      icon: CheckCircle2,
      confirmText: '确认完成',
    })
    if (!confirmed) return
    try {
      await api.task.complete(row.id)
      notify('任务已完成', 'success')
      load(query)
    } catch (error) {
      notify(error.message || '任务完成失败', 'info')
    }
  }

  const cancel = async (row) => {
    const confirmed = await confirm({
      title: '取消销售任务',
      description: '取消后任务不会再进入待办统计。',
      target: row.title,
      confirmText: '确认取消',
    })
    if (!confirmed) return
    try {
      await api.task.cancel({ id: row.id, cancelReason: '手动取消' })
      notify('任务已取消', 'success')
      load(query)
    } catch (error) {
      notify(error.message || '任务取消失败', 'info')
    }
  }

  const remove = async (row) => {
    const confirmed = await confirm({
      title: '删除销售任务',
      description: '删除后该任务不会再出现在任务列表和工作台统计中。',
      target: row.title,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.task.delete(row.id)
      notify('销售任务已删除', 'success')
      load({ ...query, pageNo: records.length <= 1 ? Math.max(1, currentPage - 1) : currentPage })
    } catch (error) {
      notify(error.message || '销售任务删除失败', 'info')
    }
  }

  const assignTask = async (ownerId) => {
    if (!assigning?.id || !ownerId) {
      notify('请选择负责人', 'info')
      return
    }
    setAssignSubmitting(true)
    try {
      await api.task.assign({ id: assigning.id, ownerId })
      notify('任务已分配', 'success')
      setAssigning(null)
      load(query)
    } catch (error) {
      notify(error.message || '任务分配失败', 'info')
    } finally {
      setAssignSubmitting(false)
    }
  }

  if (!canView) {
    return <EmptyPermission onBack={() => navigate('dashboard')} />
  }

  return (
    <div className="page sales-task-page compact-list-page">
      <PageHeader
        title="销售任务"
        description={`沉淀销售待办、跟进提醒和AI建议任务，当前 ${page.total || 0} 条`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>
            {canCreate && <Button icon={Plus} onClick={openCreate}>新建任务</Button>}
          </>
        )}
      />

      <form className="filter-card customer-filter-card list-filter-card" onSubmit={search}>
        <div className="filter-search">
          <Search size={17} />
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="搜索任务标题、关联对象或任务内容"
          />
        </div>
        <label>
          <span>状态</span>
          <select value={query.status} onChange={(event) => setQuery({ ...query, status: event.target.value })}>
            <option value="">全部状态</option>
            {statusOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
          </select>
        </label>
        <label>
          <span>优先级</span>
          <select value={query.priority} onChange={(event) => setQuery({ ...query, priority: event.target.value })}>
            <option value="">全部优先级</option>
            {priorityOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
          </select>
        </label>
        <label>
          <span>负责人</span>
          <Select
            value={query.ownerId}
            options={[{ value: '', label: '全部负责人' }, ...ownerSelectOptions]}
            searchable
            searchPlaceholder="搜索负责人"
            onChange={(value) => setQuery({ ...query, ownerId: value })}
          />
        </label>
        <Button type="submit" variant="secondary" icon={Search}>查询</Button>
      </form>

      <Card className="table-card sales-task-table-card">
        <div className="data-table-wrap">
          <table className="data-table sales-task-table">
            <colgroup>
              <col className="task-column-title" />
              <col className="task-column-target" />
              <col className="task-column-status" />
              <col className="task-column-priority" />
              <col className="task-column-owner" />
              <col className="task-column-due" />
              <col className="task-column-source" />
              <col className="task-column-actions" />
            </colgroup>
            <thead>
              <tr>
                <th>任务</th>
                <th>关联对象</th>
                <th>状态</th>
                <th>优先级</th>
                <th>负责人</th>
                <th>到期时间</th>
                <th>来源</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((row) => (
                <tr key={row.id}>
                  <td>
                    <strong>{row.title || '-'}</strong>
                    <small>{row.content || '-'}</small>
                  </td>
                  <td>
                    <strong>{row.targetName || '-'}</strong>
                    <small>{targetTypeText[row.targetType] || row.targetType || '-'}</small>
                  </td>
                  <td><Badge dot tone={statusTone[row.status] || 'neutral'}>{statusText[row.status] || row.status || '-'}</Badge></td>
                  <td><Badge tone={priorityTone[row.priority] || 'neutral'}>{priorityText[row.priority] || row.priority || '-'}</Badge></td>
                  <td>{ownerName(row)}</td>
                  <td>
                    <span>{formatDateTime(row.dueAt)}</span>
                    <small>{row.reminderAt ? `提醒：${formatDateTime(row.reminderAt)}` : '未设置提醒'}</small>
                  </td>
                  <td>{sourceText[row.source] || row.source || '-'}</td>
                  <td>
                    <div className="table-action-row text-actions">
                      {!isFinished(row) && row.status !== 'IN_PROGRESS' && row.status !== 'OVERDUE' && (
                        <button type="button" className="table-text-button" onClick={() => start(row)}>
                          开始
                        </button>
                      )}
                      {!isFinished(row) && (
                        <button type="button" className="table-text-button primary" onClick={() => complete(row)}>
                          完成
                        </button>
                      )}
                      {canManage && (
                        <button type="button" className="table-text-button" onClick={() => setEditing(toForm(row))}>
                          编辑
                        </button>
                      )}
                      {canAssign && !isFinished(row) && (
                        <button type="button" className="table-text-button" onClick={() => setAssigning(row)}>
                          分配
                        </button>
                      )}
                      {canManage && !isFinished(row) && (
                        <button type="button" className="table-text-button danger" onClick={() => cancel(row)}>
                          取消
                        </button>
                      )}
                      {canManage && (
                        <button type="button" className="table-text-button danger" onClick={() => remove(row)}>
                          删除
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!loading && !records.length && (
            <div className="empty-table">
              <ClipboardCheck size={26} />
              <b>暂无销售任务</b>
              <span>可以手动新建，也可以在跟进记录中填写下次跟进时间自动生成。</span>
            </div>
          )}
          {loading && !records.length && (
            <div className="empty-table">
              <RefreshCw size={26} />
              <b>正在加载销售任务</b>
              <span>数据来自后台销售任务接口</span>
            </div>
          )}
        </div>
        <div className="table-footer">
          <span>共 {page.total || 0} 条，当前第 {currentPage} / {totalPages} 页</span>
          <div className="pagination lead-list-pagination">
            <button type="button" disabled={loading || currentPage <= 1} onClick={() => goToPage(currentPage - 1)}>‹</button>
            <button type="button" className="active">{currentPage}</button>
            <button type="button" disabled={loading || currentPage >= totalPages} onClick={() => goToPage(currentPage + 1)}>›</button>
            <form className="lead-page-jump" onSubmit={jumpToPage}>
              <span>跳至</span>
              <input
                type="number"
                min="1"
                max={totalPages}
                step="1"
                inputMode="numeric"
                aria-label="跳转页码"
                value={jumpPage}
                onChange={(event) => setJumpPage(event.target.value)}
              />
              <span>页</span>
              <button type="submit" className="lead-page-jump-button" disabled={loading}>跳转</button>
            </form>
          </div>
        </div>
      </Card>

      <SalesTaskFormModal
        open={Boolean(editing)}
        form={editing || emptyForm}
        ownerOptions={ownerSelectOptions}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={save}
      />
      <OwnerAssignModal
        open={Boolean(assigning)}
        title="分配销售任务"
        recordName={assigning?.title}
        currentOwnerId={assigning?.ownerId}
        currentOwnerName={assigning?.ownerId ? ownerName(assigning) : ''}
        ownerOptions={ownerOptions}
        submitting={assignSubmitting}
        onClose={() => setAssigning(null)}
        onConfirm={assignTask}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function SalesTaskFormModal({ open, form, ownerOptions, onChange, onClose, onSave }) {
  const [targetKeyword, setTargetKeyword] = useState('')
  const [targetOptions, setTargetOptions] = useState([])
  const [targetLoading, setTargetLoading] = useState(false)

  useEffect(() => {
    if (!open || !form || form.targetType === 'GENERAL') {
      setTargetOptions([])
      setTargetKeyword('')
      return undefined
    }
    let mounted = true
    const timer = window.setTimeout(async () => {
      setTargetLoading(true)
      try {
        const data = await api.task.targetOptions({
          targetType: form.targetType,
          keyword: targetKeyword || undefined,
          limit: 20,
        })
        if (mounted) {
          setTargetOptions((data || []).map((item) => ({
            value: item.id,
            label: item.name || '-',
            description: [item.description, item.ownerName ? `负责人：${item.ownerName}` : '']
              .filter(Boolean)
              .join(' ｜ '),
            raw: item,
          })))
        }
      } catch {
        if (mounted) {
          setTargetOptions([])
        }
      } finally {
        if (mounted) {
          setTargetLoading(false)
        }
      }
    }, 260)
    return () => {
      mounted = false
      window.clearTimeout(timer)
    }
  }, [open, form?.targetType, targetKeyword])

  if (!open || !form) return null
  const update = (patch) => onChange({ ...form, ...patch })
  const chooseTarget = (value, option) => {
    update({
      targetId: value || '',
      targetName: option?.raw?.name || '',
    })
  }
  const changeTargetType = (value) => {
    update({
      targetType: value,
      targetId: '',
      targetName: '',
    })
    setTargetKeyword('')
  }
  return (
    <Modal
      open={open}
      title={form.id ? '编辑销售任务' : '新建销售任务'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={() => onSave(form)}>保存</Button>
        </>
      )}
    >
      <div className="customer-form-grid sales-task-form-grid">
        <Field label="任务标题" required>
          <input value={form.title || ''} onChange={(event) => update({ title: event.target.value })} placeholder="例如：回访客户确认采购计划" />
        </Field>
        <Field label="负责人" required>
          <Select
            value={form.ownerId}
            options={ownerOptions}
            searchable
            placeholder="请选择负责人"
            searchPlaceholder="搜索负责人"
            onChange={(value) => update({ ownerId: value })}
          />
        </Field>
        <Field label="优先级">
          <select value={form.priority || 'MEDIUM'} onChange={(event) => update({ priority: event.target.value })}>
            {priorityOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
          </select>
        </Field>
        <Field label="关联类型">
          <select value={form.targetType || 'GENERAL'} onChange={(event) => changeTargetType(event.target.value)}>
            {targetTypeOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
          </select>
        </Field>
        {form.targetType !== 'GENERAL' && (
          <Field label="关联对象">
            <div className="task-target-picker">
              <div className="task-target-search">
                <Search size={15} />
                <input
                  value={targetKeyword}
                  onChange={(event) => setTargetKeyword(event.target.value)}
                  placeholder={`搜索${targetTypeText[form.targetType] || '对象'}名称、联系人或电话`}
                />
              </div>
              <Select
                value={form.targetId}
                options={targetOptions}
                searchable
                placeholder={targetLoading ? '正在搜索对象' : '请选择关联对象'}
                searchPlaceholder="在结果中搜索"
                emptyText={targetLoading ? '正在搜索对象' : '没有匹配对象'}
                onChange={chooseTarget}
              />
            </div>
          </Field>
        )}
        {form.targetType !== 'GENERAL' && (
          <Field label="已选对象">
            <input value={form.targetName || ''} readOnly placeholder="选择关联对象后自动填充" />
          </Field>
        )}
        <Field label="到期时间" required>
          <input type="datetime-local" value={form.dueAt || ''} onChange={(event) => update({ dueAt: event.target.value })} />
        </Field>
        <Field label="提醒时间">
          <input type="datetime-local" value={form.reminderAt || ''} onChange={(event) => update({ reminderAt: event.target.value })} />
        </Field>
        <Field label="任务内容" className="wide-field">
          <textarea
            rows="4"
            value={form.content || ''}
            onChange={(event) => update({ content: event.target.value })}
            placeholder="记录任务背景、目标动作和注意事项"
          />
        </Field>
      </div>
    </Modal>
  )
}
