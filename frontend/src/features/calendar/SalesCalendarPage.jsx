import { useEffect, useMemo, useState } from 'react'
import {
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleUserRound,
  Clock3,
  List,
  Plus,
  RefreshCw,
  Search,
  Users,
} from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  ConfirmDialog,
  Drawer,
  EmptyPermission,
  useConfirmDialog,
} from '../../components'
import { ownerName, ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'
import { SalesTaskFormModal } from '../task/SalesTaskPage'

const weekNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

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

const emptyTaskForm = {
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

function pad(value) {
  return String(value).padStart(2, '0')
}

function dateKey(value) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function toLocalDateTime(value, withSeconds = false) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const base = `${dateKey(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`
  return withSeconds ? `${base}:${pad(date.getSeconds())}` : base
}

function startOfCalendar(month) {
  const value = new Date(month.getFullYear(), month.getMonth(), 1)
  value.setDate(value.getDate() - value.getDay())
  value.setHours(0, 0, 0, 0)
  return value
}

function addDays(value, count) {
  const date = new Date(value)
  date.setDate(date.getDate() + count)
  return date
}

function buildCalendarDays(month) {
  const start = startOfCalendar(month)
  return Array.from({ length: 42 }, (_, index) => addDays(start, index))
}

function formatMonth(value) {
  return `${value.getFullYear()}年${value.getMonth() + 1}月`
}

function formatTime(value) {
  if (!value) return '--:--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--:--'
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}

function isFinished(task) {
  return task.status === 'COMPLETED' || task.status === 'CANCELLED'
}

function isOverdue(task) {
  if (task.status === 'OVERDUE') return true
  if (isFinished(task) || !task.dueAt) return false
  return new Date(task.dueAt).getTime() < Date.now()
}

function taskVisualState(task) {
  if (task.status === 'COMPLETED') return 'completed'
  if (task.status === 'CANCELLED') return 'cancelled'
  if (isOverdue(task)) return 'overdue'
  if (task.priority === 'HIGH') return 'high'
  return 'normal'
}

function createTaskForm(date, userId) {
  const dueAt = new Date(date)
  dueAt.setHours(10, 0, 0, 0)
  const now = new Date()
  if (dateKey(dueAt) === dateKey(now) && dueAt.getTime() <= now.getTime()) {
    dueAt.setHours(now.getHours() + 1, 0, 0, 0)
  }
  return {
    ...emptyTaskForm,
    ownerId: userId || '',
    dueAt: toLocalDateTime(dueAt),
  }
}

function toTaskForm(row) {
  return {
    id: row.id,
    title: row.title || '',
    content: row.content || '',
    targetType: row.targetType || 'GENERAL',
    targetId: row.targetId || '',
    targetName: row.targetName || '',
    ownerId: row.ownerId || '',
    dueAt: toLocalDateTime(row.dueAt),
    reminderAt: toLocalDateTime(row.reminderAt),
    priority: row.priority || 'MEDIUM',
  }
}

function toTaskPayload(form) {
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

function resolveOwnerOptions(rows) {
  return rows.map((item) => ({
    value: item.id,
    label: ownerOptionLabel(item),
    description: item.departmentName || item.username || '',
  }))
}

function taskMatchesFilter(task, filter) {
  if (filter === 'ACTIVE') return !isFinished(task) && !isOverdue(task)
  if (filter === 'OVERDUE') return isOverdue(task)
  if (filter === 'COMPLETED') return task.status === 'COMPLETED'
  return true
}

function CalendarTask({ task, onClick }) {
  return (
    <button
      type="button"
      className={`sales-calendar-task ${taskVisualState(task)}`}
      onClick={(event) => {
        event.stopPropagation()
        onClick(task)
      }}
      title={`${formatTime(task.dueAt)} ${task.title || ''}`}
    >
      <i />
      <span>{formatTime(task.dueAt)}</span>
      <b>{task.title || '未命名任务'}</b>
    </button>
  )
}

export function SalesCalendarPage({ can, notify, navigate, currentUser }) {
  const canView = can('crm:task:view') || can('menu.tasks')
  const canCreate = can('crm:task:create') || can('crm:task:manage')
  const canManage = can('crm:task:manage')
  const canTeamView = canManage || can('crm:task:assign')
  const ownerOptions = useOwnerOptions(notify)
  const ownerSelectOptions = resolveOwnerOptions(ownerOptions)
  const { confirm, dialogProps } = useConfirmDialog()
  const [month, setMonth] = useState(() => {
    const today = new Date()
    return new Date(today.getFullYear(), today.getMonth(), 1)
  })
  const [selectedDate, setSelectedDate] = useState(() => new Date())
  const [viewMode, setViewMode] = useState('MONTH')
  const [scopeMode, setScopeMode] = useState('MY')
  const [filter, setFilter] = useState('ALL')
  const [keyword, setKeyword] = useState('')
  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [tasks, setTasks] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)
  const [selectedTask, setSelectedTask] = useState(null)

  const calendarDays = useMemo(() => buildCalendarDays(month), [month])
  const rangeStart = calendarDays[0]
  const rangeEnd = addDays(calendarDays[calendarDays.length - 1], 1)

  const loadTasks = async () => {
    if (!canView) return
    setLoading(true)
    try {
      const data = await api.task.page({
        pageNo: 1,
        pageSize: 200,
        keyword: appliedKeyword || undefined,
        ownerId: scopeMode === 'MY' ? currentUser?.userId : undefined,
        dueFrom: toLocalDateTime(rangeStart, true),
        dueTo: toLocalDateTime(rangeEnd, true),
      })
      setTasks(data?.records || [])
      setTotal(Number(data?.total || 0))
    } catch (error) {
      setTasks([])
      setTotal(0)
      notify(error.message || '日历任务加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadTasks()
  }, [month.getFullYear(), month.getMonth(), scopeMode, appliedKeyword, currentUser?.userId])

  const visibleTasks = useMemo(
    () => tasks.filter((task) => taskMatchesFilter(task, filter)),
    [tasks, filter],
  )

  const taskMap = useMemo(() => {
    const result = new Map()
    visibleTasks.forEach((task) => {
      const key = dateKey(task.dueAt)
      if (!key) return
      const rows = result.get(key) || []
      rows.push(task)
      rows.sort((left, right) => new Date(left.dueAt).getTime() - new Date(right.dueAt).getTime())
      result.set(key, rows)
    })
    return result
  }, [visibleTasks])

  const counts = useMemo(() => ({
    all: tasks.length,
    active: tasks.filter((task) => !isFinished(task) && !isOverdue(task)).length,
    overdue: tasks.filter(isOverdue).length,
    completed: tasks.filter((task) => task.status === 'COMPLETED').length,
  }), [tasks])

  const listGroups = useMemo(() => {
    const groups = []
    taskMap.forEach((rows, key) => groups.push({ key, rows }))
    return groups.sort((left, right) => left.key.localeCompare(right.key))
  }, [taskMap])

  const goMonth = (offset) => {
    const next = new Date(month.getFullYear(), month.getMonth() + offset, 1)
    setMonth(next)
    setSelectedDate(next)
  }

  const goToday = () => {
    const today = new Date()
    setMonth(new Date(today.getFullYear(), today.getMonth(), 1))
    setSelectedDate(today)
  }

  const openCreate = (date = selectedDate) => {
    if (!canCreate) {
      notify('当前账号没有新建销售任务权限', 'info')
      return
    }
    setEditing(createTaskForm(date, currentUser?.userId))
  }

  const saveTask = async (form) => {
    if (!(form.title || '').trim()) {
      notify('任务标题不能为空', 'info')
      return
    }
    if (!form.ownerId) {
      notify('请选择任务负责人', 'info')
      return
    }
    if (!form.dueAt) {
      notify('到期时间不能为空', 'info')
      return
    }
    try {
      await api.task.save(toTaskPayload(form))
      notify('日程已保存', 'success')
      setEditing(null)
      setSelectedTask(null)
      loadTasks()
    } catch (error) {
      notify(error.message || '日程保存失败', 'info')
    }
  }

  const completeTask = async (task) => {
    const confirmed = await confirm({
      title: '完成销售任务',
      description: '确认完成后，该任务会计入今日任务完成统计。',
      target: task.title,
      tone: 'primary',
      icon: CheckCircle2,
      confirmText: '确认完成',
    })
    if (!confirmed) return
    try {
      await api.task.complete(task.id)
      notify('任务已完成', 'success')
      setSelectedTask(null)
      loadTasks()
    } catch (error) {
      notify(error.message || '任务完成失败', 'info')
    }
  }

  const goFollowup = (task) => {
    setSelectedTask(null)
    if (task.targetType === 'LEAD' && task.targetId) {
      navigate(`leads/detail/${encodeURIComponent(task.targetId)}`)
      return
    }
    if (task.targetType === 'CUSTOMER' && task.targetId) {
      navigate(`customers/detail/${encodeURIComponent(task.targetId)}`)
      return
    }
    if (task.targetType === 'OPPORTUNITY') {
      navigate('opportunities')
      return
    }
    if (task.targetType === 'CHANNEL') {
      navigate('channels')
      return
    }
    navigate('followups')
  }

  if (!canView) {
    return <EmptyPermission onBack={() => navigate('dashboard')} />
  }

  return (
    <div className="page sales-calendar-page">
      <div className="sales-calendar-shell">
        <aside className="sales-calendar-sidebar">
          <div className="sales-calendar-search-row">
            <form
              className="sales-calendar-search"
              onSubmit={(event) => {
                event.preventDefault()
                setAppliedKeyword(keyword.trim())
              }}
            >
              <Search size={16} />
              <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索日程" />
            </form>
            {canCreate && (
              <button type="button" className="sales-calendar-add" onClick={() => openCreate()} aria-label="新建日程">
                <Plus size={20} />
              </button>
            )}
          </div>

          <MiniCalendar
            month={month}
            days={calendarDays}
            taskMap={taskMap}
            selectedDate={selectedDate}
            onSelect={(date) => setSelectedDate(date)}
            onPrevious={() => goMonth(-1)}
            onNext={() => goMonth(1)}
          />

          <div className="sales-calendar-sidebar-section">
            <strong>我的日历</strong>
            <CalendarFilterButton
              active={filter === 'ALL'}
              color="blue"
              label="全部日程"
              count={counts.all}
              onClick={() => setFilter('ALL')}
            />
            <CalendarFilterButton
              active={filter === 'ACTIVE'}
              color="orange"
              label="待处理"
              count={counts.active}
              onClick={() => setFilter('ACTIVE')}
            />
            <CalendarFilterButton
              active={filter === 'OVERDUE'}
              color="red"
              label="已逾期"
              count={counts.overdue}
              onClick={() => setFilter('OVERDUE')}
            />
            <CalendarFilterButton
              active={filter === 'COMPLETED'}
              color="green"
              label="已完成"
              count={counts.completed}
              onClick={() => setFilter('COMPLETED')}
            />
          </div>

          {canTeamView && (
            <div className="sales-calendar-sidebar-section">
              <strong>查看范围</strong>
              <button
                type="button"
                className={`sales-calendar-scope ${scopeMode === 'MY' ? 'active' : ''}`}
                onClick={() => setScopeMode('MY')}
              >
                <CircleUserRound size={16} />
                <span>我的日程</span>
              </button>
              <button
                type="button"
                className={`sales-calendar-scope ${scopeMode === 'TEAM' ? 'active' : ''}`}
                onClick={() => setScopeMode('TEAM')}
              >
                <Users size={16} />
                <span>团队日程</span>
              </button>
            </div>
          )}

          <button type="button" className="sales-calendar-task-link" onClick={() => navigate('tasks')}>
            <List size={16} />
            <span>进入销售任务列表</span>
          </button>
        </aside>

        <section className="sales-calendar-main">
          <div className="sales-calendar-toolbar">
            <div className="sales-calendar-toolbar-nav">
              <Button variant="secondary" onClick={goToday}>今天</Button>
              <button type="button" className="icon-button" onClick={() => goMonth(-1)} aria-label="上个月"><ChevronLeft size={20} /></button>
              <button type="button" className="icon-button" onClick={() => goMonth(1)} aria-label="下个月"><ChevronRight size={20} /></button>
              <h1>{formatMonth(month)}</h1>
              {loading && <RefreshCw className="sales-calendar-loading" size={16} />}
            </div>
            <div className="sales-calendar-toolbar-actions">
              {total > 200 && <span className="sales-calendar-limit">当前范围共 {total} 条，仅展示前 200 条</span>}
              <div className="sales-calendar-view-switch">
                <button type="button" className={viewMode === 'MONTH' ? 'active' : ''} onClick={() => setViewMode('MONTH')}>月</button>
                <button type="button" className={viewMode === 'LIST' ? 'active' : ''} onClick={() => setViewMode('LIST')}>列表</button>
              </div>
              <button type="button" className="icon-button" onClick={loadTasks} aria-label="刷新日历"><RefreshCw size={17} /></button>
            </div>
          </div>

          {viewMode === 'MONTH' ? (
            <MonthCalendar
              month={month}
              days={calendarDays}
              taskMap={taskMap}
              selectedDate={selectedDate}
              canCreate={canCreate}
              onSelectDate={setSelectedDate}
              onCreate={openCreate}
              onOpenTask={setSelectedTask}
            />
          ) : (
            <CalendarList
              groups={listGroups}
              loading={loading}
              canCreate={canCreate}
              onOpenTask={setSelectedTask}
              onCreate={openCreate}
            />
          )}
        </section>
      </div>

      <SalesTaskFormModal
        open={Boolean(editing)}
        form={editing || emptyTaskForm}
        ownerOptions={ownerSelectOptions}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={saveTask}
      />

      <Drawer
        open={Boolean(selectedTask)}
        title="日程详情"
        onClose={() => setSelectedTask(null)}
        footer={selectedTask && (
          <>
            <Button variant="secondary" onClick={() => goFollowup(selectedTask)}>去跟进</Button>
            {canManage && (
              <Button variant="secondary" onClick={() => {
                setEditing(toTaskForm(selectedTask))
                setSelectedTask(null)
              }}>编辑</Button>
            )}
            {!isFinished(selectedTask) && <Button onClick={() => completeTask(selectedTask)}>标记完成</Button>}
          </>
        )}
      >
        {selectedTask && <TaskDetail task={selectedTask} />}
      </Drawer>
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function MiniCalendar({ month, days, taskMap, selectedDate, onSelect, onPrevious, onNext }) {
  return (
    <div className="sales-mini-calendar">
      <div className="sales-mini-calendar-head">
        <strong>{formatMonth(month)}</strong>
        <span>
          <button type="button" onClick={onPrevious} aria-label="上个月"><ChevronLeft size={16} /></button>
          <button type="button" onClick={onNext} aria-label="下个月"><ChevronRight size={16} /></button>
        </span>
      </div>
      <div className="sales-mini-calendar-week">
        {weekNames.map((name) => <span key={name}>{name.slice(1)}</span>)}
      </div>
      <div className="sales-mini-calendar-grid">
        {days.map((date) => {
          const key = dateKey(date)
          const outside = date.getMonth() !== month.getMonth()
          const selected = key === dateKey(selectedDate)
          const today = key === dateKey(new Date())
          return (
            <button
              type="button"
              className={`${outside ? 'outside' : ''} ${selected ? 'selected' : ''} ${today ? 'today' : ''}`}
              key={key}
              onClick={() => onSelect(date)}
            >
              <span>{date.getDate()}</span>
              {taskMap.has(key) && <i />}
            </button>
          )
        })}
      </div>
    </div>
  )
}

function CalendarFilterButton({ active, color, label, count, onClick }) {
  return (
    <button type="button" className={`sales-calendar-filter ${active ? 'active' : ''}`} onClick={onClick}>
      <i className={color} />
      <span>{label}</span>
      <b>{count}</b>
    </button>
  )
}

function MonthCalendar({ month, days, taskMap, selectedDate, canCreate, onSelectDate, onCreate, onOpenTask }) {
  return (
    <div className="sales-month-calendar">
      <div className="sales-month-week-head">
        {weekNames.map((name) => <div key={name}>{name}</div>)}
      </div>
      <div className="sales-month-grid">
        {days.map((date) => {
          const key = dateKey(date)
          const rows = taskMap.get(key) || []
          const outside = date.getMonth() !== month.getMonth()
          const selected = key === dateKey(selectedDate)
          const today = key === dateKey(new Date())
          return (
            <div
              className={`sales-month-cell ${outside ? 'outside' : ''} ${selected ? 'selected' : ''}`}
              key={key}
              onClick={() => onSelectDate(date)}
            >
              <div className="sales-month-cell-head">
                <span className={today ? 'today' : ''}>{date.getDate()}</span>
                {canCreate && (
                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation()
                      onCreate(date)
                    }}
                    aria-label={`${key} 新建日程`}
                  ><Plus size={14} /></button>
                )}
              </div>
              <div className="sales-month-cell-tasks">
                {rows.slice(0, 3).map((task) => <CalendarTask task={task} onClick={onOpenTask} key={task.id} />)}
                {rows.length > 3 && <button type="button" className="sales-calendar-more" onClick={() => onSelectDate(date)}>还有 {rows.length - 3} 条</button>}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function CalendarList({ groups, loading, canCreate, onOpenTask, onCreate }) {
  if (!groups.length) {
    return (
      <div className="sales-calendar-empty">
        <span><CalendarDays size={29} /></span>
        <b>{loading ? '正在加载日程' : '当前月份暂无日程'}</b>
        <p>新建的销售待办、跟进计划和系统任务会统一显示在这里。</p>
        {!loading && canCreate && <Button icon={Plus} onClick={() => onCreate(new Date())}>新建日程</Button>}
      </div>
    )
  }
  return (
    <div className="sales-calendar-list">
      {groups.map((group) => {
        const date = new Date(`${group.key}T00:00:00`)
        return (
          <section key={group.key}>
            <div className="sales-calendar-list-date">
              <strong>{date.getDate()}</strong>
              <span>{date.toLocaleDateString('zh-CN', { month: 'long', weekday: 'long' })}</span>
            </div>
            <div className="sales-calendar-list-items">
              {group.rows.map((task) => (
                <button type="button" key={task.id} onClick={() => onOpenTask(task)}>
                  <i className={taskVisualState(task)} />
                  <time>{formatTime(task.dueAt)}</time>
                  <div>
                    <strong>{task.title || '未命名任务'}</strong>
                    <small>{task.targetName || targetTypeText[task.targetType] || '通用任务'}</small>
                  </div>
                  <Badge tone={statusTone[task.status] || 'neutral'}>{statusText[task.status] || task.status || '-'}</Badge>
                  <span>{ownerName(task)}</span>
                </button>
              ))}
            </div>
          </section>
        )
      })}
    </div>
  )
}

function TaskDetail({ task }) {
  return (
    <div className="sales-calendar-detail">
      <div className={`sales-calendar-detail-hero ${taskVisualState(task)}`}>
        <span><CalendarDays size={21} /></span>
        <div>
          <div>
            <Badge tone={statusTone[task.status] || 'neutral'}>{statusText[task.status] || task.status || '-'}</Badge>
            <Badge tone={priorityTone[task.priority] || 'neutral'}>{priorityText[task.priority] || task.priority || '-'}</Badge>
          </div>
          <h3>{task.title || '未命名任务'}</h3>
          <p><Clock3 size={14} />{formatDateTime(task.dueAt)}</p>
        </div>
      </div>
      <dl className="sales-calendar-detail-grid">
        <div><dt>负责人</dt><dd>{ownerName(task)}</dd></div>
        <div><dt>关联类型</dt><dd>{targetTypeText[task.targetType] || task.targetType || '-'}</dd></div>
        <div><dt>关联对象</dt><dd>{task.targetName || '-'}</dd></div>
        <div><dt>提醒时间</dt><dd>{formatDateTime(task.reminderAt)}</dd></div>
        <div><dt>任务来源</dt><dd>{sourceText[task.source] || task.source || '-'}</dd></div>
        <div><dt>创建时间</dt><dd>{formatDateTime(task.createdAt)}</dd></div>
      </dl>
      <div className="sales-calendar-detail-content">
        <span>任务内容</span>
        <p>{task.content || '暂无补充内容'}</p>
      </div>
    </div>
  )
}
