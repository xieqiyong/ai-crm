import { useEffect, useState } from 'react'
import {
  ArrowUpRight,
  Bot,
  CheckCircle2,
  CloudUpload,
  Copy,
  ExternalLink,
  FileText,
  Link2,
  MessageSquareText,
  MoreHorizontal,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  Trash2,
  Upload,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, ConfirmDialog, Field, Modal, PageHeader, useConfirmDialog } from '../../components'
import { ownerName } from '../../hooks/useOwnerOptions'

const typeOptions = [
  { value: '', label: '全部类型' },
  { value: 'MANUAL', label: '手动渠道' },
  { value: 'FORM', label: '获客表单' },
  { value: 'AUDIO', label: '录音导入' },
  { value: 'VIDEO', label: '视频导入' },
]

const sourceOptions = [
  { value: 'WEBSITE', label: '官网' },
  { value: 'LANDING_PAGE', label: '落地页' },
  { value: 'SMS', label: '短信' },
  { value: 'WECHAT', label: '微信' },
  { value: 'WECHAT_GROUP', label: '微信群' },
  { value: 'PHONE', label: '电话' },
  { value: 'OFFLINE_EVENT', label: '线下活动' },
  { value: 'LIVE', label: '直播' },
  { value: 'REFERRAL', label: '转介绍' },
  { value: 'AD', label: '广告投放' },
  { value: 'OTHER', label: '其他' },
]

const sourceText = sourceOptions.reduce((map, item) => {
  map[item.value] = item.label
  return map
}, {})

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'NEW', label: '新渠道' },
  { value: 'WAITING_TRANSCRIPTION', label: '待转译' },
  { value: 'TRANSCRIBED', label: '已转译' },
  { value: 'WAITING_AI_ANALYSIS', label: '待AI分析' },
  { value: 'ANALYZED', label: '已分析' },
  { value: 'PROMOTED', label: '已晋升线索' },
]

const statusText = {
  NEW: '新渠道',
  WAITING_TRANSCRIPTION: '待转译',
  TRANSCRIBED: '已转译',
  WAITING_AI_ANALYSIS: '待AI分析',
  ANALYZED: '已分析',
  PROMOTED: '已晋升线索',
}

const statusTone = {
  NEW: 'neutral',
  WAITING_TRANSCRIPTION: 'warning',
  TRANSCRIBED: 'info',
  WAITING_AI_ANALYSIS: 'warning',
  ANALYZED: 'success',
  PROMOTED: 'success',
}

const typeText = {
  MANUAL: '手动渠道',
  FORM: '获客表单',
  AUDIO: '录音导入',
  VIDEO: '视频导入',
}

const emptyForm = {
  title: '',
  channelType: 'MANUAL',
  source: 'OTHER',
  contactName: '',
  companyName: '',
  phone: '',
  email: '',
  remark: '',
}

const emptyImportForm = {
  title: '',
  channelType: 'AUDIO',
  source: 'PHONE',
  contactName: '',
  companyName: '',
  phone: '',
  email: '',
  remark: '',
}

const emptyMarketingForm = {
  title: '',
  description: '',
  source: 'LANDING_PAGE',
  submitMessage: '提交成功，我们会尽快联系您。',
  status: 'PUBLISHED',
  autoCreateLead: true,
  fields: [
    { fieldKey: 'name', label: '姓名', fieldType: 'TEXT', requiredField: false, placeholder: '请填写姓名', systemMapping: 'name', sortOrder: 0 },
    { fieldKey: 'companyName', label: '公司名称', fieldType: 'TEXT', requiredField: true, placeholder: '请填写公司名称', systemMapping: 'companyName', sortOrder: 1 },
    { fieldKey: 'phone', label: '手机号', fieldType: 'PHONE', requiredField: true, placeholder: '请填写手机号', systemMapping: 'phone', sortOrder: 2 },
    { fieldKey: 'email', label: '邮箱', fieldType: 'EMAIL', requiredField: false, placeholder: '请填写邮箱', systemMapping: 'email', sortOrder: 3 },
    { fieldKey: 'remark', label: '需求描述', fieldType: 'TEXTAREA', requiredField: false, placeholder: '请简单描述您的需求', systemMapping: 'remark', sortOrder: 4 },
  ],
}

const formStatusText = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CLOSED: '已关闭',
}

const formStatusTone = {
  DRAFT: 'neutral',
  PUBLISHED: 'success',
  CLOSED: 'warning',
}

function formatDateTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatSize(size) {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function compactPayload(query) {
  return {
    pageNo: query.pageNo || 1,
    pageSize: query.pageSize || 20,
    keyword: query.keyword || undefined,
    status: query.status || undefined,
    channelType: query.channelType || undefined,
  }
}

function toEditForm(row) {
  return {
    id: row.id,
    title: row.title || '',
    channelType: row.channelType || 'MANUAL',
    source: row.source || '',
    contactName: row.contactName || '',
    companyName: row.companyName || '',
    phone: row.phone || '',
    email: row.email || '',
    remark: row.remark || '',
  }
}

function marketingFormUrl(row) {
  const path = row?.publicPath || `/public/forms/${row?.formCode || ''}`
  return `${window.location.origin}${window.location.pathname}#${path}`
}

function marketingSmsText(row) {
  return `您好，欢迎填写${row.title || '获客表单'}，我们会尽快联系您：${marketingFormUrl(row)}`
}

function SourceSelect({ value, onChange }) {
  return (
    <select value={value || 'OTHER'} onChange={(event) => onChange(event.target.value)}>
      {sourceOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
    </select>
  )
}

export function ChannelPage({ can, notify }) {
  const canManage = can('crm:channel:manage')
  const canMedia = can('crm:channel:media') || canManage
  const canPromote = can('crm:channel:promote')
  const { confirm, dialogProps } = useConfirmDialog()

  const [query, setQuery] = useState({
    keyword: '',
    status: '',
    channelType: '',
    pageNo: 1,
    pageSize: 20,
  })
  const [page, setPage] = useState({
    total: 0,
    pageNo: 1,
    pageSize: 20,
    records: [],
  })
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)
  const [importing, setImporting] = useState(false)
  const [selected, setSelected] = useState(null)
  const [marketingForms, setMarketingForms] = useState([])
  const [marketingFormsLoading, setMarketingFormsLoading] = useState(true)
  const [marketingFormEditing, setMarketingFormEditing] = useState(null)

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const data = await api.channel.page(compactPayload(nextQuery))
      setPage(data || { total: 0, pageNo: 1, pageSize: 20, records: [] })
      setQuery(nextQuery)
    } catch (err) {
      notify(err.message || '加载渠道失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  const loadMarketingForms = async () => {
    setMarketingFormsLoading(true)
    try {
      const data = await api.channel.formPage({ pageNo: 1, pageSize: 5 })
      setMarketingForms(data?.records || [])
    } catch (err) {
      notify(err.message || '加载获客表单失败', 'info')
    } finally {
      setMarketingFormsLoading(false)
    }
  }

  useEffect(() => {
    load()
    loadMarketingForms()
  }, [])

  const refreshFirstPage = () => load({ ...query, pageNo: 1 })

  const refreshAll = () => {
    refreshFirstPage()
    loadMarketingForms()
  }

  const openDetail = async (row) => {
    setSelected(row)
    try {
      setSelected(await api.channel.detail(row.id))
    } catch (err) {
      notify(err.message || '渠道详情加载失败', 'info')
    }
  }

  const deleteChannel = async (row) => {
    const confirmed = await confirm({
      title: '删除渠道',
      description: '删除后该渠道不会再出现在列表和统计中，请确认当前操作。',
      target: row.title,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.channel.delete(row.id)
      notify('渠道已删除', 'success')
      if (selected?.id === row.id) {
        setSelected(null)
      }
      load({ ...query, pageNo: 1 })
    } catch (err) {
      notify(err.message || '渠道删除失败', 'info')
    }
  }

  const handlePrepareTranscription = async (row) => {
    try {
      await api.channel.prepareTranscription(row.id)
      notify('已标记为待转译，等待接入转译服务', 'success')
      setSelected(null)
      load()
    } catch (err) {
      notify(err.message || '转译状态更新失败', 'info')
    }
  }

  const handlePrepareAnalysis = async (row) => {
    try {
      await api.channel.prepareAnalysis(row.id)
      notify('已标记为待AI分析，等待接入分析服务', 'success')
      setSelected(null)
      load()
    } catch (err) {
      notify(err.message || 'AI分析状态更新失败', 'info')
    }
  }

  const handlePromote = async (row) => {
    try {
      await api.channel.promote({ id: row.id })
      notify('渠道已晋升为线索', 'success')
      setSelected(null)
      load()
    } catch (err) {
      notify(err.message || '渠道晋升线索失败', 'info')
    }
  }

  const openMarketingForm = async (row) => {
    try {
      setMarketingFormEditing(await api.channel.formDetail(row.id))
    } catch (err) {
      notify(err.message || '获客表单详情加载失败', 'info')
    }
  }

  const deleteMarketingForm = async (row) => {
    const confirmed = await confirm({
      title: '删除获客表单',
      description: '删除后公开链接会失效，已经提交的数据仍保留在渠道池和线索中。',
      target: row.title,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.channel.formDelete(row.id)
      notify('获客表单已删除', 'success')
      loadMarketingForms()
    } catch (err) {
      notify(err.message || '获客表单删除失败', 'info')
    }
  }

  const copyMarketingText = async (text, message) => {
    try {
      await navigator.clipboard.writeText(text)
      notify(message, 'success')
    } catch {
      notify('复制失败，请手动复制', 'info')
    }
  }

  const headerActions = (
    <>
      <Button variant="secondary" icon={RefreshCw} onClick={refreshFirstPage}>
        刷新
      </Button>
      {canMedia && (
        <Button variant="secondary" icon={Upload} onClick={() => setImporting(true)}>
          导入音视频
        </Button>
      )}
      {canManage && (
        <Button variant="secondary" icon={Link2} onClick={() => setMarketingFormEditing(emptyMarketingForm)}>
          新建获客表单
        </Button>
      )}
      {canManage && (
        <Button icon={Plus} onClick={() => setEditing(emptyForm)}>
          新增渠道
        </Button>
      )}
    </>
  )

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 20
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))
  const importedCount = records.filter((row) => row.channelType === 'AUDIO' || row.channelType === 'VIDEO').length
  const promotedCount = records.filter((row) => row.leadId).length

  return (
    <div className="page channel-page">
      <PageHeader
        eyebrow="渠道管理"
        title="渠道管理"
        description="沉淀市场、电话、活动和音视频渠道线索，确认有效后可直接晋升为线索"
        actions={headerActions}
      />

      <div className="channel-overview">
        <ChannelStat icon={FileText} label="当前页渠道" value={records.length} />
        <ChannelStat icon={CloudUpload} label="当前页音视频" value={importedCount} />
        <ChannelStat icon={CheckCircle2} label="当前页已晋升" value={promotedCount} />
      </div>

      <MarketingFormPanel
        forms={marketingForms}
        loading={marketingFormsLoading}
        canManage={canManage}
        onCreate={() => setMarketingFormEditing(emptyMarketingForm)}
        onEdit={openMarketingForm}
        onDelete={deleteMarketingForm}
        onCopyLink={(row) => copyMarketingText(marketingFormUrl(row), '表单链接已复制')}
        onCopySms={(row) => copyMarketingText(marketingSmsText(row), '短信文案已复制')}
      />

      <ChannelFilter
        query={query}
        setQuery={setQuery}
        onSearch={refreshFirstPage}
      />

      <div className="channel-layout">
        <ChannelTable
          records={records}
          loading={loading}
          page={page}
          query={query}
          canManage={canManage}
          canMedia={canMedia}
          canPromote={canPromote}
          currentPage={currentPage}
          totalPages={totalPages}
          onLoad={load}
          onEdit={setEditing}
          onSelect={openDetail}
          onPrepareTranscription={handlePrepareTranscription}
          onPrepareAnalysis={handlePrepareAnalysis}
          onPromote={handlePromote}
          onDelete={deleteChannel}
        />

        <ChannelReserveCard />
      </div>

      <ChannelEditModal
        open={Boolean(editing)}
        data={editing}
        onClose={() => setEditing(null)}
        notify={notify}
        reload={refreshFirstPage}
      />
      <ChannelImportModal
        open={importing}
        onClose={() => setImporting(false)}
        notify={notify}
        reload={refreshFirstPage}
      />
      <MarketingFormModal
        open={Boolean(marketingFormEditing)}
        data={marketingFormEditing}
        onClose={() => setMarketingFormEditing(null)}
        notify={notify}
        reload={refreshAll}
      />
      <ChannelDetailModal
        open={Boolean(selected)}
        data={selected}
        canManage={canManage}
        canMedia={canMedia}
        canPromote={canPromote}
        onClose={() => setSelected(null)}
        onPrepareTranscription={handlePrepareTranscription}
        onPrepareAnalysis={handlePrepareAnalysis}
        onPromote={handlePromote}
        onDelete={deleteChannel}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function ChannelStat({ icon: Icon, label, value }) {
  return (
    <Card className="channel-stat">
      <span><Icon size={18} /></span>
      <small>{label}</small>
      <b>{value}</b>
    </Card>
  )
}

function ChannelFilter({ query, setQuery, onSearch }) {
  return (
    <Card className="filter-card channel-filter">
      <div className="filter-search">
        <Search size={17} />
        <input
          value={query.keyword}
          onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
          placeholder="搜索标题、来源、联系人、公司、电话"
          onKeyDown={(event) => event.key === 'Enter' && onSearch()}
        />
      </div>

      <label>
        <span>状态</span>
        <select value={query.status} onChange={(event) => setQuery({ ...query, status: event.target.value })}>
          {statusOptions.map((item) => (
            <option value={item.value} key={item.value}>{item.label}</option>
          ))}
        </select>
      </label>

      <label>
        <span>类型</span>
        <select
          value={query.channelType}
          onChange={(event) => setQuery({ ...query, channelType: event.target.value })}
        >
          {typeOptions.map((item) => (
            <option value={item.value} key={item.value}>{item.label}</option>
          ))}
        </select>
      </label>

      <Button variant="secondary" icon={Search} onClick={onSearch}>
        筛选
      </Button>
    </Card>
  )
}

function ChannelTable({
  records,
  loading,
  page,
  query,
  canManage,
  canMedia,
  canPromote,
  currentPage,
  totalPages,
  onLoad,
  onEdit,
  onSelect,
  onPrepareTranscription,
  onPrepareAnalysis,
  onPromote,
  onDelete,
}) {
  return (
    <Card className="table-card channel-table-card">
      <div className="data-table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>渠道信息</th>
              <th>联系人</th>
              <th>类型</th>
              <th>文件</th>
              <th>状态</th>
              <th>负责人</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {records.map((row) => (
              <ChannelTableRow
                row={row}
                key={row.id}
                canManage={canManage}
                canMedia={canMedia}
                canPromote={canPromote}
                onEdit={onEdit}
                onSelect={onSelect}
                onPrepareTranscription={onPrepareTranscription}
                onPrepareAnalysis={onPrepareAnalysis}
                onPromote={onPromote}
                onDelete={onDelete}
              />
            ))}
          </tbody>
        </table>

        {!loading && records.length === 0 && (
          <div className="empty-table">
            <Search size={26} />
            <b>暂无渠道记录</b>
            <span>新增渠道或导入录音/视频后，这里会展示真实数据。</span>
          </div>
        )}
        {loading && (
          <div className="empty-table">
            <RefreshCw size={26} />
            <b>正在加载</b>
            <span>正在读取渠道记录</span>
          </div>
        )}
      </div>

      <div className="table-footer channel-table-footer">
        <span className="channel-page-info">共 {page.total || 0} 条，当前第 {currentPage} / {totalPages} 页</span>
        <div className="pagination channel-pagination">
          <button
            disabled={currentPage <= 1}
            onClick={() => onLoad({ ...query, pageNo: currentPage - 1 })}
          >
            上一页
          </button>
          <button className="active">{currentPage}</button>
          <button
            disabled={currentPage >= totalPages}
            onClick={() => onLoad({ ...query, pageNo: currentPage + 1 })}
          >
            下一页
          </button>
        </div>
      </div>
    </Card>
  )
}

function ChannelTableRow({
  row,
  canManage,
  canMedia,
  canPromote,
  onEdit,
  onSelect,
  onPrepareTranscription,
  onPrepareAnalysis,
  onPromote,
  onDelete,
}) {
  const promoted = Boolean(row.leadId)

  return (
    <tr onClick={() => onSelect(row)}>
      <td>
        <div className="channel-title-cell">
          <strong>{row.title}</strong>
          <small>
            {sourceText[row.source] || row.source || '未填写来源'}
            {row.leadId ? ` · 线索ID：${row.leadId}` : ''}
          </small>
        </div>
      </td>
      <td>
        <strong>{row.contactName || '-'}</strong>
        <small>{row.companyName || row.phone || row.email || '未填写'}</small>
      </td>
      <td>
        <Badge tone={row.channelType === 'MANUAL' ? 'neutral' : 'info'}>
          {typeText[row.channelType] || row.channelType}
        </Badge>
      </td>
      <td>
        <span>{row.mediaFileName || '-'}</span>
        <small>{formatSize(row.mediaSize)}</small>
      </td>
      <td>
        <Badge dot tone={statusTone[row.status] || 'neutral'}>
          {statusText[row.status] || row.status}
        </Badge>
      </td>
      <td>{ownerName(row)}</td>
      <td>{formatDateTime(row.createdAt)}</td>
      <td onClick={(event) => event.stopPropagation()}>
        <div className="channel-actions">
          <button className="icon-button" onClick={() => onSelect(row)}>
            <MoreHorizontal size={18} />
          </button>
          {canManage && (
            <button className="text-action" onClick={() => onEdit(toEditForm(row))}>
              编辑
            </button>
          )}
          {canManage && (
            <button className="text-action danger" onClick={() => onDelete(row)}>
              删除
            </button>
          )}
          <button
            className="text-action"
            disabled={!canMedia || row.status === 'PROMOTED'}
            onClick={() => onPrepareTranscription(row)}
          >
            转译
          </button>
          <button
            className="text-action"
            disabled={!canMedia || row.status === 'PROMOTED'}
            onClick={() => onPrepareAnalysis(row)}
          >
            AI分析
          </button>
          <button
            className="text-action strong"
            disabled={!canPromote || promoted}
            onClick={() => onPromote(row)}
          >
            晋升线索
          </button>
        </div>
      </td>
    </tr>
  )
}

function ChannelReserveCard() {
  return (
    <Card ai className="channel-sidebar">
      <div className="ai-card-title">
        <span><Bot size={18} /></span>
        <div>
          <h2>转译与AI分析预留</h2>
          <small>当前只做状态流转，不生成伪造内容</small>
        </div>
      </div>

      <div className="channel-stage-list">
        <div className="channel-stage active">
          <b>1. 导入渠道</b>
          <span>录音、视频或手动渠道进入渠道池</span>
        </div>
        <div className="channel-stage">
          <b>2. 转译中文</b>
          <span>后续接入音视频转译服务后写入转译文本</span>
        </div>
        <div className="channel-stage">
          <b>3. AI分析</b>
          <span>后续接入大模型后提取需求、预算、联系人和关键风险</span>
        </div>
        <div className="channel-stage">
          <b>4. 晋升线索</b>
          <span>确认有效后创建真实线索记录并回写线索ID</span>
        </div>
      </div>

      <div className="reserve-note">
        <Sparkles size={16} />
        <span>后续只需要在后端补齐文件存储、转译回调、AI分析服务，即可沿用当前渠道记录和权限体系。</span>
      </div>
    </Card>
  )
}

function MarketingFormPanel({
  forms,
  loading,
  canManage,
  onCreate,
  onEdit,
  onDelete,
  onCopyLink,
  onCopySms,
}) {
  return (
    <Card className="marketing-form-panel">
      <div className="marketing-form-head">
        <div>
          <h2>获客表单</h2>
          <p>生成公开链接或短信文案，客户填写后自动进入渠道池。</p>
        </div>
        {canManage && <Button icon={Link2} onClick={onCreate}>创建表单</Button>}
      </div>

      {loading ? (
        <div className="marketing-form-empty">正在加载获客表单…</div>
      ) : forms.length === 0 ? (
        <div className="marketing-form-empty">
          <b>还没有获客表单</b>
          <span>先创建一个表单，复制链接后就可以投放出去。</span>
        </div>
      ) : (
        <div className="marketing-form-list">
          {forms.map((row) => (
            <div className="marketing-form-item" key={row.id}>
              <div>
                <b>{row.title}</b>
                <small>{marketingFormUrl(row)}</small>
              </div>
              <div className="marketing-form-meta">
                <Badge tone={formStatusTone[row.status] || 'neutral'}>{formStatusText[row.status] || row.status}</Badge>
                <span>访问 {row.viewCount || 0}</span>
                <span>提交 {row.submitCount || 0}</span>
                {row.autoCreateLead && <Badge tone="success">自动转线索</Badge>}
              </div>
              <div className="marketing-form-actions">
                <button className="text-action" onClick={() => onCopyLink(row)}><Copy size={13} />复制链接</button>
                <button className="text-action" onClick={() => onCopySms(row)}><MessageSquareText size={13} />复制短信</button>
                <a className="text-action" href={marketingFormUrl(row)} target="_blank" rel="noreferrer"><ExternalLink size={13} />打开</a>
                {canManage && <button className="text-action strong" onClick={() => onEdit(row)}>编辑</button>}
                {canManage && <button className="text-action danger" onClick={() => onDelete(row)}>删除</button>}
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}

function MarketingFormModal({ open, data, onClose, notify, reload }) {
  const [form, setForm] = useState(emptyMarketingForm)

  useEffect(() => {
    setForm(data ? {
      ...emptyMarketingForm,
      ...data,
      fields: data.fields?.length ? data.fields : emptyMarketingForm.fields,
    } : emptyMarketingForm)
  }, [data, open])

  const updateField = (index, patch) => {
    const fields = [...(form.fields || [])]
    fields[index] = { ...fields[index], ...patch }
    setForm({ ...form, fields })
  }

  const addField = () => {
    const fields = form.fields || []
    setForm({
      ...form,
      fields: [
        ...fields,
        {
          fieldKey: `custom_${Date.now()}`,
          label: '自定义字段',
          fieldType: 'TEXT',
          requiredField: false,
          placeholder: '',
          systemMapping: 'custom',
          sortOrder: fields.length,
        },
      ],
    })
  }

  const removeField = (index) => {
    setForm({ ...form, fields: (form.fields || []).filter((_, itemIndex) => itemIndex !== index) })
  }

  const save = async () => {
    if (!form.title) {
      notify('表单标题不能为空', 'info')
      return
    }
    try {
      await api.channel.formSave({
        ...form,
        fields: (form.fields || []).map((field, index) => ({ ...field, sortOrder: index })),
      })
      notify('获客表单已保存', 'success')
      onClose()
      reload()
    } catch (err) {
      notify(err.message || '获客表单保存失败', 'info')
    }
  }

  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>取消</Button>
      <Button onClick={save}>保存并生成链接</Button>
    </>
  )

  return (
    <Modal open={open} title={form.id ? '编辑获客表单' : '新建获客表单'} onClose={onClose} size="lg" footer={footer}>
      <div className="marketing-form-editor">
        <div className="form-grid">
          <Field label="表单标题" required>
            <input value={form.title || ''} onChange={(event) => setForm({ ...form, title: event.target.value })} placeholder="例如：产品演示预约" />
          </Field>
          <Field label="渠道来源">
            <SourceSelect value={form.source} onChange={(value) => setForm({ ...form, source: value })} />
          </Field>
          <Field label="发布状态">
            <select value={form.status || 'PUBLISHED'} onChange={(event) => setForm({ ...form, status: event.target.value })}>
              <option value="PUBLISHED">发布</option>
              <option value="DRAFT">草稿</option>
              <option value="CLOSED">关闭</option>
            </select>
          </Field>
          <Field label="提交后提示语">
            <input value={form.submitMessage || ''} onChange={(event) => setForm({ ...form, submitMessage: event.target.value })} />
          </Field>
          <Field label="表单说明">
            <textarea rows="3" value={form.description || ''} onChange={(event) => setForm({ ...form, description: event.target.value })} placeholder="展示给外部客户看的说明文案" />
          </Field>
        </div>

        <label className="marketing-form-switch">
          <input
            type="checkbox"
            checked={Boolean(form.autoCreateLead)}
            onChange={(event) => setForm({ ...form, autoCreateLead: event.target.checked })}
          />
          <span>
            <b>提交后自动创建线索</b>
            <small>客户提交后先进入渠道池；满足姓名或公司名称时，同步创建线索。</small>
          </span>
        </label>

        <div className="marketing-field-editor">
          <div className="marketing-field-head">
            <b>表单字段</b>
            <button type="button" className="text-action strong" onClick={addField}>添加字段</button>
          </div>
          {(form.fields || []).map((field, index) => (
            <div className="marketing-field-row" key={`${field.fieldKey}-${index}`}>
              <input value={field.label || ''} onChange={(event) => updateField(index, { label: event.target.value })} placeholder="字段名称" />
              <select value={field.fieldType || 'TEXT'} onChange={(event) => updateField(index, { fieldType: event.target.value })}>
                <option value="TEXT">单行文本</option>
                <option value="TEXTAREA">多行文本</option>
                <option value="PHONE">手机号</option>
                <option value="EMAIL">邮箱</option>
                <option value="SELECT">下拉选择</option>
              </select>
              <select value={field.systemMapping || 'custom'} onChange={(event) => updateField(index, { systemMapping: event.target.value })}>
                <option value="name">姓名</option>
                <option value="companyName">公司名称</option>
                <option value="phone">电话</option>
                <option value="email">邮箱</option>
                <option value="remark">备注</option>
                <option value="custom">自定义</option>
              </select>
              <label>
                <input type="checkbox" checked={Boolean(field.requiredField)} onChange={(event) => updateField(index, { requiredField: event.target.checked })} />
                必填
              </label>
              <button type="button" className="text-action danger" onClick={() => removeField(index)}>删除</button>
              <input className="marketing-field-placeholder" value={field.placeholder || ''} onChange={(event) => updateField(index, { placeholder: event.target.value })} placeholder="占位提示" />
              {field.fieldType === 'SELECT' && (
                <input className="marketing-field-options" value={field.optionsText || ''} onChange={(event) => updateField(index, { optionsText: event.target.value })} placeholder="选项，用逗号或换行分隔" />
              )}
            </div>
          ))}
        </div>
      </div>
    </Modal>
  )
}

function ChannelEditModal({ open, data, onClose, notify, reload }) {
  const [form, setForm] = useState(emptyForm)

  useEffect(() => {
    setForm(data ? toEditForm(data) : emptyForm)
  }, [data, open])

  const save = async () => {
    try {
      await api.channel.save(form)
      notify('渠道已保存', 'success')
      onClose()
      reload()
    } catch (err) {
      notify(err.message || '渠道保存失败', 'info')
    }
  }

  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>取消</Button>
      <Button onClick={save}>保存</Button>
    </>
  )

  return (
    <Modal open={open} title={form.id ? '编辑渠道' : '新增渠道'} onClose={onClose} footer={footer}>
      <div className="form-grid">
        <Field label="渠道标题" required>
          <input
            value={form.title || ''}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
            placeholder="例如：7月线下活动客户咨询"
          />
        </Field>
        <Field label="渠道类型">
          <select
            value={form.channelType || 'MANUAL'}
            onChange={(event) => setForm({ ...form, channelType: event.target.value })}
          >
            <option value="MANUAL">手动渠道</option>
            <option value="FORM">获客表单</option>
            <option value="AUDIO">录音渠道</option>
            <option value="VIDEO">视频渠道</option>
          </select>
        </Field>
        <Field label="渠道来源">
          <SourceSelect value={form.source} onChange={(value) => setForm({ ...form, source: value })} />
        </Field>
        <Field label="联系人">
          <input
            value={form.contactName || ''}
            onChange={(event) => setForm({ ...form, contactName: event.target.value })}
          />
        </Field>
        <Field label="公司名称">
          <input
            value={form.companyName || ''}
            onChange={(event) => setForm({ ...form, companyName: event.target.value })}
          />
        </Field>
        <Field label="手机号">
          <input
            value={form.phone || ''}
            onChange={(event) => setForm({ ...form, phone: event.target.value })}
          />
        </Field>
        <Field label="邮箱">
          <input
            value={form.email || ''}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
          />
        </Field>
        <Field label="备注">
          <textarea
            rows="4"
            value={form.remark || ''}
            onChange={(event) => setForm({ ...form, remark: event.target.value })}
            placeholder="记录真实已知信息，不需要补假数据"
          />
        </Field>
      </div>
    </Modal>
  )
}

function ChannelImportModal({ open, onClose, notify, reload }) {
  const [form, setForm] = useState(emptyImportForm)
  const [file, setFile] = useState(null)

  useEffect(() => {
    if (!open) {
      setForm(emptyImportForm)
      setFile(null)
    }
  }, [open])

  const selectFile = (event) => {
    const nextFile = event.target.files?.[0]
    if (!nextFile) return
    setFile(nextFile)
    setForm({
      ...form,
      title: form.title || nextFile.name,
      channelType: nextFile.type?.startsWith('video/') ? 'VIDEO' : 'AUDIO',
    })
  }

  const submit = async () => {
    if (!file) return
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', form.title || file.name)
    formData.append('channelType', form.channelType || 'AUDIO')
    formData.append('source', form.source || '')
    formData.append('contactName', form.contactName || '')
    formData.append('companyName', form.companyName || '')
    formData.append('phone', form.phone || '')
    formData.append('email', form.email || '')
    formData.append('remark', form.remark || '')
    try {
      await api.channel.importMedia(formData)
      notify('音视频已导入渠道池', 'success')
      onClose()
      reload()
    } catch (err) {
      notify(err.message || '音视频导入失败', 'info')
    }
  }

  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>取消</Button>
      <Button disabled={!file} onClick={submit}>导入</Button>
    </>
  )

  return (
    <Modal open={open} title="导入录音或视频" onClose={onClose} footer={footer}>
      <div className="form-grid">
        <Field
          label="音视频文件"
          required
          hint="当前会上传到后端并登记文件元信息；转译和AI分析服务后续接入。"
        >
          <div className="upload-drop">
            <CloudUpload size={26} />
            <span>{file ? file.name : '选择录音或视频文件'}</span>
            <small>{file ? formatSize(file.size) : '支持 mp3、wav、m4a、mp4、mov 等常见格式'}</small>
            <input type="file" accept="audio/*,video/*" onChange={selectFile} />
          </div>
        </Field>
        <Field label="渠道标题">
          <input value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} />
        </Field>
        <Field label="渠道类型">
          <select value={form.channelType} onChange={(event) => setForm({ ...form, channelType: event.target.value })}>
            <option value="AUDIO">录音导入</option>
            <option value="VIDEO">视频导入</option>
          </select>
        </Field>
        <Field label="渠道来源">
          <SourceSelect value={form.source} onChange={(value) => setForm({ ...form, source: value })} />
        </Field>
        <Field label="联系人">
          <input value={form.contactName} onChange={(event) => setForm({ ...form, contactName: event.target.value })} />
        </Field>
        <Field label="公司名称">
          <input value={form.companyName} onChange={(event) => setForm({ ...form, companyName: event.target.value })} />
        </Field>
        <Field label="手机号">
          <input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} />
        </Field>
        <Field label="邮箱">
          <input value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
        </Field>
        <Field label="备注">
          <textarea rows="4" value={form.remark} onChange={(event) => setForm({ ...form, remark: event.target.value })} />
        </Field>
      </div>
    </Modal>
  )
}

function ChannelDetailModal({
  open,
  data,
  canManage,
  canMedia,
  canPromote,
  onClose,
  onPrepareTranscription,
  onPrepareAnalysis,
  onPromote,
  onDelete,
}) {
  if (!data) return null
  const promoted = Boolean(data.leadId)
  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>关闭</Button>
      <Button
        variant="secondary"
        disabled={!canMedia || promoted}
        icon={FileText}
        onClick={() => onPrepareTranscription(data)}
      >
        转译中文
      </Button>
      <Button
        variant="secondary"
        disabled={!canMedia || promoted}
        icon={Sparkles}
        onClick={() => onPrepareAnalysis(data)}
      >
        AI分析
      </Button>
      <Button disabled={!canPromote || promoted} icon={ArrowUpRight} onClick={() => onPromote(data)}>
        晋升线索
      </Button>
      {canManage && (
        <Button variant="ghost" icon={Trash2} onClick={() => onDelete(data)}>
          删除
        </Button>
      )}
    </>
  )

  return (
    <Modal open={open} title="渠道详情" onClose={onClose} size="lg" footer={footer}>
      <div className="channel-detail-grid">
        <DetailItem label="渠道标题" value={data.title} />
        <DetailItem label="渠道来源" value={sourceText[data.source] || data.source} />
        <DetailItem label="渠道类型" value={typeText[data.channelType] || data.channelType} />
        <DetailItem label="状态" value={statusText[data.status] || data.status} />
        <DetailItem label="联系人" value={data.contactName} />
        <DetailItem label="公司名称" value={data.companyName} />
        <DetailItem label="手机号" value={data.phone} />
        <DetailItem label="邮箱" value={data.email} />
        <DetailItem label="音视频文件" value={data.mediaFileName} />
        <DetailItem label="文件大小" value={formatSize(data.mediaSize)} />
        <DetailItem label="线索ID" value={data.leadId} />
        <DetailItem label="负责人" value={ownerName(data)} />
        <DetailItem label="创建时间" value={formatDateTime(data.createdAt)} />
      </div>
      <TextBlock label="备注" value={data.remark || '暂无备注'} />
      <TextBlock label="转译文本" value={data.transcriptText || '暂无转译文本，等待接入转译服务。'} />
      <TextBlock label="AI总结" value={data.aiSummary || '暂无AI总结，等待接入分析服务。'} />
      <TextBlock label="有用信息" value={data.usefulInfo || '暂无有用信息，等待接入AI分析服务。'} />
    </Modal>
  )
}

function DetailItem({ label, value }) {
  return (
    <div>
      <span>{label}</span>
      <b>{value || '-'}</b>
    </div>
  )
}

function TextBlock({ label, value }) {
  return (
    <div className="channel-text-block">
      <span>{label}</span>
      <p>{value}</p>
    </div>
  )
}
