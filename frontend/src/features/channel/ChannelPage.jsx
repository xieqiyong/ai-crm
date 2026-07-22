import { useEffect, useState } from 'react'
import {
  ArrowUpRight,
  Bot,
  CheckCircle2,
  CloudUpload,
  FileText,
  MoreHorizontal,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  Upload,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, Field, Modal, PageHeader } from '../../components'

const typeOptions = [
  { value: '', label: '全部类型' },
  { value: 'MANUAL', label: '手动渠道' },
  { value: 'AUDIO', label: '录音导入' },
  { value: 'VIDEO', label: '视频导入' },
]

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
  AUDIO: '录音导入',
  VIDEO: '视频导入',
}

const emptyForm = {
  title: '',
  channelType: 'MANUAL',
  source: '',
  contactName: '',
  companyName: '',
  phone: '',
  email: '',
  remark: '',
}

const emptyImportForm = {
  title: '',
  channelType: 'AUDIO',
  source: '',
  contactName: '',
  companyName: '',
  phone: '',
  email: '',
  remark: '',
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

export function ChannelPage({ can, notify }) {
  const canManage = can('crm:channel:manage')
  const canMedia = can('crm:channel:media') || canManage
  const canPromote = can('crm:channel:promote')

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

  useEffect(() => {
    load()
  }, [])

  const refreshFirstPage = () => load({ ...query, pageNo: 1 })

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
          onSelect={setSelected}
          onPrepareTranscription={handlePrepareTranscription}
          onPrepareAnalysis={handlePrepareAnalysis}
          onPromote={handlePromote}
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
      <ChannelDetailModal
        open={Boolean(selected)}
        data={selected}
        canMedia={canMedia}
        canPromote={canPromote}
        onClose={() => setSelected(null)}
        onPrepareTranscription={handlePrepareTranscription}
        onPrepareAnalysis={handlePrepareAnalysis}
        onPromote={handlePromote}
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
  canPromote,
  currentPage,
  totalPages,
  onLoad,
  onEdit,
  onSelect,
  onPrepareTranscription,
  onPrepareAnalysis,
  onPromote,
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

      <div className="table-footer">
        <span>共 {page.total || 0} 条，当前第 {currentPage} / {totalPages} 页</span>
        <div className="pagination">
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
}) {
  const promoted = Boolean(row.leadId)

  return (
    <tr onClick={() => onSelect(row)}>
      <td>
        <div className="channel-title-cell">
          <strong>{row.title}</strong>
          <small>
            {row.source || '未填写来源'}
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
            <option value="AUDIO">录音渠道</option>
            <option value="VIDEO">视频渠道</option>
          </select>
        </Field>
        <Field label="渠道来源">
          <input
            value={form.source || ''}
            onChange={(event) => setForm({ ...form, source: event.target.value })}
            placeholder="官网、活动、电话、转介绍等"
          />
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
          <input
            value={form.source}
            onChange={(event) => setForm({ ...form, source: event.target.value })}
            placeholder="例如：电话录音、会销视频、直播回放"
          />
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
  canMedia,
  canPromote,
  onClose,
  onPrepareTranscription,
  onPrepareAnalysis,
  onPromote,
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
    </>
  )

  return (
    <Modal open={open} title="渠道详情" onClose={onClose} size="lg" footer={footer}>
      <div className="channel-detail-grid">
        <DetailItem label="渠道标题" value={data.title} />
        <DetailItem label="渠道来源" value={data.source} />
        <DetailItem label="渠道类型" value={typeText[data.channelType] || data.channelType} />
        <DetailItem label="状态" value={statusText[data.status] || data.status} />
        <DetailItem label="联系人" value={data.contactName} />
        <DetailItem label="公司名称" value={data.companyName} />
        <DetailItem label="手机号" value={data.phone} />
        <DetailItem label="邮箱" value={data.email} />
        <DetailItem label="音视频文件" value={data.mediaFileName} />
        <DetailItem label="文件大小" value={formatSize(data.mediaSize)} />
        <DetailItem label="线索ID" value={data.leadId} />
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
