import { useEffect, useState } from 'react'
import {
  ArrowUpRight,
  CheckCircle2,
  CloudUpload,
  Copy,
  ExternalLink,
  FileText,
  Link2,
  MessageSquareText,
  MessagesSquare,
  MoreHorizontal,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  Trash2,
  Upload,
} from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Field,
  MarkdownText,
  Modal,
  PageHeader,
  useConfirmDialog,
} from '../../components'
import { ownerName } from '../../hooks/useOwnerOptions'
import { leadSourceOptions as sourceOptions, leadSourceText as sourceText } from '../../models/crmSource'
import { WecomSyncModal } from './WecomSyncModal'

const typeOptions = [
  { value: '', label: '全部类型' },
  { value: 'MANUAL', label: '手动渠道' },
  { value: 'FORM', label: '获客表单' },
  { value: 'AUDIO', label: '录音导入' },
  { value: 'VIDEO', label: '视频导入' },
  { value: 'DOCUMENT', label: '文档导入' },
  { value: 'WECOM', label: '企业微信' },
]

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'NEW', label: '新渠道' },
  { value: 'WAITING_TRANSCRIPTION', label: '待转译' },
  { value: 'TRANSCRIBED', label: '已转译' },
  { value: 'WAITING_AI_ANALYSIS', label: '待AI整理' },
  { value: 'ANALYZED', label: 'AI已整理' },
  { value: 'PROMOTED', label: '已晋升线索' },
]

const statusText = {
  NEW: '新渠道',
  WAITING_TRANSCRIPTION: '待转译',
  TRANSCRIBED: '已转译',
  WAITING_AI_ANALYSIS: '待AI整理',
  ANALYZED: 'AI已整理',
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
  DOCUMENT: '文档导入',
  WECOM: '企业微信',
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
  channelType: 'DOCUMENT',
  source: 'PHONE',
  contactName: '',
  companyName: '',
  phone: '',
  email: '',
}

const emptyMarketingForm = {
  title: '',
  description: '',
  source: 'LANDING_PAGE',
  submitMessage: '提交成功，我们会尽快联系您。',
  status: 'PUBLISHED',
  autoCreateLead: true,
  fields: [
    {
      fieldKey: 'name',
      label: '姓名',
      fieldType: 'TEXT',
      requiredField: false,
      placeholder: '请填写姓名',
      systemMapping: 'name',
      sortOrder: 0,
    },
    {
      fieldKey: 'companyName',
      label: '公司名称',
      fieldType: 'TEXT',
      requiredField: true,
      placeholder: '请填写公司名称',
      systemMapping: 'companyName',
      sortOrder: 1,
    },
    {
      fieldKey: 'phone',
      label: '手机号',
      fieldType: 'PHONE',
      requiredField: true,
      placeholder: '请填写手机号',
      systemMapping: 'phone',
      sortOrder: 2,
    },
    {
      fieldKey: 'email',
      label: '邮箱',
      fieldType: 'EMAIL',
      requiredField: false,
      placeholder: '请填写邮箱',
      systemMapping: 'email',
      sortOrder: 3,
    },
    {
      fieldKey: 'remark',
      label: '需求描述',
      fieldType: 'TEXTAREA',
      requiredField: false,
      placeholder: '请简单描述您的需求',
      systemMapping: 'remark',
      sortOrder: 4,
    },
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

function resolveImportType(file) {
  const type = file?.type || ''
  const name = (file?.name || '').toLowerCase()
  if (type.startsWith('video/') || /\.(mp4|mov|avi|mkv|webm)$/.test(name)) {
    return 'VIDEO'
  }
  if (type.startsWith('audio/') || /\.(mp3|wav|m4a|aac|flac)$/.test(name)) {
    return 'AUDIO'
  }
  return 'DOCUMENT'
}

function isDocumentImport(channelType) {
  return channelType === 'DOCUMENT'
}

function isImportedMaterial(row) {
  return Boolean(row?.mediaFileName)
    && ['DOCUMENT', 'AUDIO', 'VIDEO'].includes(row?.channelType)
}

function requiresAiAnalysis(row) {
  return isImportedMaterial(row)
}

function supportsAiAnalysis(row) {
  return requiresAiAnalysis(row) || row?.channelType === 'WECOM'
}

function isSameId(first, second) {
  if (first === undefined || first === null || second === undefined || second === null) {
    return false
  }
  return String(first) === String(second)
}

function channelStatusLabel(row) {
  if (row?.channelType === 'WECOM' && row?.status === 'WAITING_AI_ANALYSIS') {
    return '待跟进'
  }
  if (requiresAiAnalysis(row) && row?.status === 'ANALYZED' && !row?.promotionReady) {
    return '待AI重整'
  }
  return statusText[row?.status] || row?.status
}

function channelStatusTone(row) {
  if (row?.channelType === 'WECOM' && row?.status === 'WAITING_AI_ANALYSIS') {
    return 'info'
  }
  if (requiresAiAnalysis(row) && row?.status === 'ANALYZED' && !row?.promotionReady) {
    return 'warning'
  }
  return statusTone[row?.status] || 'neutral'
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
    importedMaterial: Boolean(row.importedMaterial) || requiresAiAnalysis(row),
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
  const canAnalyze = can('crm:channel:analyze') || canManage
  const canPromote = can('crm:channel:promote')
  const canWecomView = can('crm:wecom:view') || can('crm:channel:view') || canManage
  const canWecomManage = can('crm:wecom:manage') || canManage
  const canWecomSync = can('crm:wecom:sync') || canManage
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
  const [analyzingId, setAnalyzingId] = useState(null)
  const [wecomOpen, setWecomOpen] = useState(false)

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
    setAnalyzingId(row.id)
    try {
      const result = await api.channel.analyze(row.id)
      notify('渠道智能体已完成整理，分析备注已回填', 'success')
      if (isSameId(selected?.id, row.id)) {
        setSelected(result)
      }
      await load()
    } catch (err) {
      notify(err.message || '渠道智能体分析失败', 'info')
    } finally {
      setAnalyzingId(null)
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
          导入渠道材料
        </Button>
      )}
      {canWecomView && (
        <Button variant="secondary" icon={MessagesSquare} onClick={() => setWecomOpen(true)}>
          企业微信同步
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
  const importedCount = records.filter((row) => (
    row.channelType === 'AUDIO' || row.channelType === 'VIDEO' || row.channelType === 'DOCUMENT'
  )).length
  const promotedCount = records.filter((row) => row.leadId).length

  return (
    <div className="page channel-page">
      <PageHeader
        eyebrow="渠道管理"
        title="渠道管理"
        description="统一沉淀渠道材料，先由渠道智能体整理销售备注，再将有效信息晋升为线索"
        actions={headerActions}
      />

      <div className="channel-overview">
        <ChannelStat icon={FileText} label="当前页渠道" value={records.length} />
        <ChannelStat icon={CloudUpload} label="当前页导入材料" value={importedCount} />
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
          canAnalyzePermission={canAnalyze}
          canPromote={canPromote}
          analyzingId={analyzingId}
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
        canAnalyzePermission={canAnalyze}
        canPromote={canPromote}
        analyzing={isSameId(analyzingId, selected?.id)}
        onClose={() => setSelected(null)}
        onPrepareTranscription={handlePrepareTranscription}
        onPrepareAnalysis={handlePrepareAnalysis}
        onPromote={handlePromote}
        onDelete={deleteChannel}
      />
      <ConfirmDialog {...dialogProps} />
      <WecomSyncModal
        open={wecomOpen}
        canManage={canWecomManage}
        canSync={canWecomSync}
        onClose={() => setWecomOpen(false)}
        notify={notify}
        onSynced={refreshFirstPage}
      />
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
  canAnalyzePermission,
  canPromote,
  analyzingId,
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
              <th>材料</th>
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
                canAnalyzePermission={canAnalyzePermission}
                canPromote={canPromote}
                analyzing={isSameId(analyzingId, row.id)}
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
            <span>新增渠道或导入渠道材料后，这里会展示真实数据。</span>
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
  canAnalyzePermission,
  canPromote,
  analyzing,
  onEdit,
  onSelect,
  onPrepareTranscription,
  onPrepareAnalysis,
  onPromote,
  onDelete,
}) {
  const promoted = Boolean(row.leadId)
  const documentImported = row.channelType === 'DOCUMENT'
  const aiManaged = supportsAiAnalysis(row)
  const analysisReady = aiManaged
    && !promoted
    && row.status !== 'ANALYZED'
    && (row.channelType === 'WECOM'
      || documentImported
      || row.status === 'TRANSCRIBED'
      || row.status === 'WAITING_AI_ANALYSIS')

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
        <span>{row.mediaFileName || (row.channelType === 'WECOM' ? '企业微信客户' : '-')}</span>
        <small>{formatSize(row.mediaSize)}</small>
      </td>
      <td>
        <Badge dot tone={channelStatusTone(row)}>
          {channelStatusLabel(row)}
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
          {(row.channelType === 'AUDIO' || row.channelType === 'VIDEO') && (
            <button
              className="text-action"
              disabled={!canMedia || row.status === 'PROMOTED'}
              onClick={() => onPrepareTranscription(row)}
            >
              转译
            </button>
          )}
          {analysisReady && (
            <button
              className="text-action"
              disabled={!canAnalyzePermission || analyzing}
              onClick={() => onPrepareAnalysis(row)}
            >
              {analyzing ? 'AI整理中…' : 'AI整理'}
            </button>
          )}
          {row.promotionReady && !promoted && (
            <button
              className="text-action strong"
              disabled={!canPromote}
              onClick={() => onPromote(row)}
            >
              晋升线索
            </button>
          )}
        </div>
      </td>
    </tr>
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
                <button className="text-action" onClick={() => onCopyLink(row)}>
                  <Copy size={13} />
                  复制链接
                </button>
                <button className="text-action" onClick={() => onCopySms(row)}>
                  <MessageSquareText size={13} />
                  复制短信
                </button>
                <a className="text-action" href={marketingFormUrl(row)} target="_blank" rel="noreferrer">
                  <ExternalLink size={13} />
                  打开
                </a>
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
            <input
              value={form.title || ''}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              placeholder="例如：产品演示预约"
            />
          </Field>
          <Field label="渠道来源">
            <SourceSelect value={form.source} onChange={(value) => setForm({ ...form, source: value })} />
          </Field>
          <Field label="发布状态">
            <select
              value={form.status || 'PUBLISHED'}
              onChange={(event) => setForm({ ...form, status: event.target.value })}
            >
              <option value="PUBLISHED">发布</option>
              <option value="DRAFT">草稿</option>
              <option value="CLOSED">关闭</option>
            </select>
          </Field>
          <Field label="提交后提示语">
            <input
              value={form.submitMessage || ''}
              onChange={(event) => setForm({ ...form, submitMessage: event.target.value })}
            />
          </Field>
          <Field label="表单说明">
            <textarea
              rows="3"
              value={form.description || ''}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
              placeholder="展示给外部客户看的说明文案"
            />
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
              <input
                value={field.label || ''}
                onChange={(event) => updateField(index, { label: event.target.value })}
                placeholder="字段名称"
              />
              <select
                value={field.fieldType || 'TEXT'}
                onChange={(event) => updateField(index, { fieldType: event.target.value })}
              >
                <option value="TEXT">单行文本</option>
                <option value="TEXTAREA">多行文本</option>
                <option value="PHONE">手机号</option>
                <option value="EMAIL">邮箱</option>
                <option value="SELECT">下拉选择</option>
              </select>
              <select
                value={field.systemMapping || 'custom'}
                onChange={(event) => updateField(index, { systemMapping: event.target.value })}
              >
                <option value="name">姓名</option>
                <option value="companyName">公司名称</option>
                <option value="phone">电话</option>
                <option value="email">邮箱</option>
                <option value="remark">备注</option>
                <option value="custom">自定义</option>
              </select>
              <label>
                <input
                  type="checkbox"
                  checked={Boolean(field.requiredField)}
                  onChange={(event) => updateField(index, { requiredField: event.target.checked })}
                />
                必填
              </label>
              <button type="button" className="text-action danger" onClick={() => removeField(index)}>删除</button>
              <input
                className="marketing-field-placeholder"
                value={field.placeholder || ''}
                onChange={(event) => updateField(index, { placeholder: event.target.value })}
                placeholder="占位提示"
              />
              {field.fieldType === 'SELECT' && (
                <input
                  className="marketing-field-options"
                  value={field.optionsText || ''}
                  onChange={(event) => updateField(index, { optionsText: event.target.value })}
                  placeholder="选项，用逗号或换行分隔"
                />
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
      const payload = { ...form }
      delete payload.importedMaterial
      await api.channel.save(payload)
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

  const aiManagedRemark = Boolean(form.importedMaterial)

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
            {form.channelType === 'WECOM' && <option value="WECOM">企业微信同步</option>}
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
        {aiManagedRemark ? (
          <div className="channel-ai-note">
            <Sparkles size={18} />
            <div>
              <b>备注由渠道智能体整理</b>
              <span>当前备注来自渠道智能体，导入记录不能手动覆盖。</span>
            </div>
          </div>
        ) : (
          <Field label="备注">
            <textarea
              rows="4"
              value={form.remark || ''}
              onChange={(event) => setForm({ ...form, remark: event.target.value })}
              placeholder="记录真实已知信息，不需要补假数据"
            />
          </Field>
        )}
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
      channelType: resolveImportType(nextFile),
    })
  }

  const submit = async () => {
    if (!file) return
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', form.title || file.name)
    formData.append('source', form.source || '')
    formData.append('contactName', form.contactName || '')
    formData.append('companyName', form.companyName || '')
    formData.append('phone', form.phone || '')
    formData.append('email', form.email || '')
    try {
      if (isDocumentImport(form.channelType)) {
        await api.channel.importDocument(formData)
        notify('文档已提取，请使用渠道智能体完成整理后再晋升线索', 'success')
      } else {
        formData.append('channelType', form.channelType || 'AUDIO')
        await api.channel.importMedia(formData)
        notify('音视频已导入，请先完成中文转译，再使用渠道智能体整理', 'success')
      }
      onClose()
      reload()
    } catch (err) {
      notify(err.message || '渠道材料导入失败', 'info')
    }
  }

  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>取消</Button>
      <Button disabled={!file} onClick={submit}>导入</Button>
    </>
  )

  return (
    <Modal open={open} title="导入渠道材料" onClose={onClose} footer={footer}>
      <div className="form-grid">
        <Field
          label="渠道材料"
          required
          hint="HTML、TXT、MD、DOCX 会提取关键信息；音视频暂登记文件并等待转译。"
        >
          <div className="upload-drop">
            <CloudUpload size={26} />
            <span>{file ? file.name : '选择文档、HTML、录音或视频'}</span>
            <small>{file ? formatSize(file.size) : '支持 html、txt、md、docx、mp3、wav、mp4、mov 等格式'}</small>
            <input
              type="file"
              accept="audio/*,video/*,.html,.htm,.txt,.md,.markdown,.docx"
              onChange={selectFile}
            />
          </div>
        </Field>
        <Field label="渠道标题">
          <input value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} />
        </Field>
        <Field label="渠道类型">
          <select value={form.channelType} onChange={(event) => setForm({ ...form, channelType: event.target.value })}>
            <option value="DOCUMENT">文档导入</option>
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
        <div className="channel-ai-note">
          <Sparkles size={18} />
          <div>
            <b>导入阶段不填写备注</b>
            <span>材料提取后由渠道智能体生成产品定位、购买意向、基础信息和风险备注。</span>
          </div>
        </div>
      </div>
    </Modal>
  )
}

function ChannelDetailModal({
  open,
  data,
  canManage,
  canMedia,
  canAnalyzePermission,
  canPromote,
  analyzing,
  onClose,
  onPrepareTranscription,
  onPrepareAnalysis,
  onPromote,
  onDelete,
}) {
  if (!data) return null
  const promoted = Boolean(data.leadId)
  const documentImported = data.channelType === 'DOCUMENT'
  const aiManaged = supportsAiAnalysis(data)
  const analysisRequired = requiresAiAnalysis(data)
  const analysisReady = aiManaged
    && !promoted
    && data.status !== 'ANALYZED'
    && (data.channelType === 'WECOM'
      || documentImported
      || data.status === 'TRANSCRIBED'
      || data.status === 'WAITING_AI_ANALYSIS')
  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>关闭</Button>
      {(data.channelType === 'AUDIO' || data.channelType === 'VIDEO') && (
        <Button
          variant="secondary"
          disabled={!canMedia || promoted}
          icon={FileText}
          onClick={() => onPrepareTranscription(data)}
        >
          转译中文
        </Button>
      )}
      {analysisReady && (
        <Button
          variant="secondary"
          disabled={!canAnalyzePermission || analyzing}
          icon={Sparkles}
          onClick={() => onPrepareAnalysis(data)}
        >
          {analyzing ? 'AI整理中…' : 'AI整理'}
        </Button>
      )}
      {data.promotionReady && !promoted && (
        <Button disabled={!canPromote} icon={ArrowUpRight} onClick={() => onPromote(data)}>
          晋升线索
        </Button>
      )}
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
        <DetailItem label="状态" value={channelStatusLabel(data)} />
        <DetailItem label="联系人" value={data.contactName} />
        <DetailItem label="公司名称" value={data.companyName} />
        <DetailItem label="手机号" value={data.phone} />
        <DetailItem label="邮箱" value={data.email} />
        <DetailItem label="渠道材料" value={data.mediaFileName} />
        <DetailItem label="文件大小" value={formatSize(data.mediaSize)} />
        <DetailItem label="线索ID" value={data.leadId} />
        <DetailItem label="负责人" value={ownerName(data)} />
        <DetailItem label="创建时间" value={formatDateTime(data.createdAt)} />
        <DetailItem label="AI分析时间" value={formatDateTime(data.aiAnalyzedAt)} />
      </div>
      {analysisRequired && !data.promotionReady && !promoted && (
        <div className="channel-promotion-gate">
          <Sparkles size={18} />
          <div>
            <b>完成 AI 整理后才能晋升线索</b>
            <span>{data.promotionBlockReason || '渠道智能体会生成销售可用的结构化备注。'}</span>
          </div>
        </div>
      )}
      <TextBlock label="AI渠道备注" value={data.remark || '等待渠道智能体生成备注。'} markdown />
      <TextBlock label="提取文本" value={data.transcriptText || '暂无提取文本。'} />
      <TextBlock label="结构化摘要" value={data.aiSummary || '暂无结构化摘要。'} />
      <TextBlock label="有用信息" value={data.usefulInfo || '暂无有用信息。'} />
      {data.channelType === 'WECOM' && (
        <WecomSourceSnapshot value={data.sourceSnapshot} />
      )}
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

function TextBlock({ label, value, markdown = false }) {
  return (
    <div className="channel-text-block">
      <span>{label}</span>
      {markdown ? <MarkdownText value={value} /> : <p>{value}</p>}
    </div>
  )
}

function WecomSourceSnapshot({ value }) {
  if (!value) {
    return (
      <section className="wecom-snapshot">
        <div className="wecom-snapshot-head">
          <div>
            <span>企业微信原始信息</span>
            <h3>暂无同步信息</h3>
          </div>
        </div>
      </section>
    )
  }

  let snapshot
  try {
    snapshot = JSON.parse(value)
  } catch {
    return <TextBlock label="企业微信原始信息" value={value} />
  }
  if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) {
    return <TextBlock label="企业微信原始信息" value={value} />
  }

  const profile = snapshot['客户基础信息'] || {}
  const summary = snapshot['同步摘要'] || {}
  const externalProfile = Array.isArray(snapshot['客户对外资料']) ? snapshot['客户对外资料'] : []
  const follows = Array.isArray(snapshot['好友关系']) ? snapshot['好友关系'] : []
  const groups = Array.isArray(snapshot['客户群关系']) ? snapshot['客户群关系'] : []

  return (
    <section className="wecom-snapshot">
      <div className="wecom-snapshot-head">
        <div>
          <span>企业微信原始信息</span>
          <h3>{profile['姓名'] || profile['企业名称'] || '企业微信客户资料'}</h3>
          <p>{snapshot['数据来源'] || '企业微信同步'}</p>
        </div>
        {hasSnapshotValue(snapshot['企业ID']) && (
          <small>企业ID：{formatSnapshotValue('企业ID', snapshot['企业ID'])}</small>
        )}
      </div>

      <SnapshotSection title="同步摘要" count={snapshotEntries(summary).length}>
        <div className="wecom-profile-grid">
          {snapshotEntries(summary).map(([label, fieldValue]) => (
            <SnapshotField label={label} value={fieldValue} key={label} />
          ))}
        </div>
      </SnapshotSection>

      <SnapshotSection title="客户基础资料" count={snapshotEntries(profile).length}>
        <div className="wecom-profile-grid">
          {snapshotEntries(profile).map(([label, fieldValue]) => (
            <SnapshotField label={label} value={fieldValue} key={label} />
          ))}
        </div>
      </SnapshotSection>

      <SnapshotSection title="客户对外资料" count={externalProfile.length}>
        <div className="wecom-relation-list">
          {externalProfile.map((item, index) => (
            <SnapshotRelationCard
              data={item}
              title={item?.['资料名称'] || `对外资料 ${index + 1}`}
              titleLabel="资料名称"
              key={`${item?.['资料名称'] || 'external-profile'}-${index}`}
            />
          ))}
        </div>
      </SnapshotSection>

      <SnapshotSection title="好友关系" count={follows.length}>
        <div className="wecom-relation-list">
          {follows.map((item, index) => (
            <SnapshotRelationCard
              data={item}
              title={item?.['添加员工'] || `好友关系 ${index + 1}`}
              titleLabel="添加员工"
              key={`${item?.['添加员工'] || 'follow'}-${index}`}
            />
          ))}
        </div>
      </SnapshotSection>

      <SnapshotSection title="客户群关系" count={groups.length}>
        <div className="wecom-relation-list">
          {groups.map((item, index) => (
            <SnapshotRelationCard
              data={item}
              title={item?.['客户群名称'] || `客户群 ${index + 1}`}
              titleLabel="客户群名称"
              key={`${item?.['客户群ID'] || 'group'}-${index}`}
            />
          ))}
        </div>
      </SnapshotSection>
    </section>
  )
}

function SnapshotSection({ title, count, children }) {
  return (
    <div className="wecom-snapshot-section">
      <div className="wecom-snapshot-section-head">
        <h4>{title}</h4>
        <Badge tone={count ? 'info' : 'neutral'}>{count || 0}</Badge>
      </div>
      {count ? children : <div className="wecom-snapshot-empty">暂无{title}</div>}
    </div>
  )
}

function SnapshotRelationCard({ data, title, titleLabel }) {
  const entries = snapshotEntries(data).filter(([label]) => label !== titleLabel)
  return (
    <article className="wecom-relation-card">
      <div className="wecom-relation-card-head">
        <b>{title}</b>
      </div>
      <div className="wecom-relation-fields">
        {entries.map(([label, value]) => (
          <SnapshotField label={label} value={value} key={label} />
        ))}
      </div>
    </article>
  )
}

function SnapshotField({ label, value }) {
  return (
    <div className="wecom-snapshot-field">
      <span>{label}</span>
      <b>{formatSnapshotValue(label, value)}</b>
    </div>
  )
}

function snapshotEntries(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return []
  return Object.entries(data).filter(([, value]) => hasSnapshotValue(value))
}

function hasSnapshotValue(value) {
  if (value === null || value === undefined || value === '') return false
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.keys(value).length > 0
  return true
}

function formatSnapshotValue(label, value) {
  if (label === '性别') {
    if (Number(value) === 1) return '男'
    if (Number(value) === 2) return '女'
    return '未设置'
  }
  if (label === '添加方式' || label === '入群方式') {
    if (typeof value === 'number' || /^\d+$/.test(String(value))) {
      return `方式代码 ${value}`
    }
    return String(value)
  }
  if (label.includes('时间') && typeof value === 'string') {
    return formatDateTime(value)
  }
  if (Array.isArray(value)) {
    return value.map((item) => formatSnapshotArrayItem(item)).filter(Boolean).join('、')
  }
  if (value && typeof value === 'object') {
    return Object.entries(value)
      .filter(([, item]) => hasSnapshotValue(item))
      .map(([key, item]) => `${key}：${formatSnapshotValue(key, item)}`)
      .join('；')
  }
  return String(value)
}

function formatSnapshotArrayItem(item) {
  if (!item || typeof item !== 'object') return item === null || item === undefined ? '' : String(item)
  return item.tag_name
    || item.name
    || Object.entries(item)
      .filter(([, value]) => hasSnapshotValue(value))
      .map(([key, value]) => `${key}：${formatSnapshotValue(key, value)}`)
      .join('；')
}
