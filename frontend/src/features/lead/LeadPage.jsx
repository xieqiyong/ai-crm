import { useEffect, useState } from 'react'
import {
  ArrowUpRight,
  Building2,
  CalendarDays,
  Edit2,
  Mail,
  Phone,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  Trash2,
  UserPlus,
} from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Drawer,
  Field,
  MarkdownText,
  Modal,
  PageHeader,
  useConfirmDialog,
} from '../../components'
import { customerOptionLabel, useCustomerOptions } from '../../hooks/useCustomerOptions'
import { ownerName, ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'
import {
  customerLevelText,
  customerStatusOptions,
  recommendedCustomerStatus,
  leadStatusOptions,
  leadStatusText as statusText,
  leadStatusTone as statusTone,
  recommendedLeadStatus,
} from '../../models/crmStatus'

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: [],
}

const emptyForm = {
  name: '',
  companyName: '',
  phone: '',
  email: '',
  source: '',
  status: recommendedLeadStatus,
  ownerId: '',
  remark: '',
}

const aiStageText = {
  NEW: '新线索',
  FOLLOWING: '跟进中',
  QUALIFIED: '已确认',
  CONVERTED: '已转化',
  CLOSED: '已关闭',
  UNKNOWN: '未知阶段',
}

const aiPriorityText = {
  HIGH: '高优先级',
  MEDIUM: '中优先级',
  LOW: '低优先级',
}

const aiPriorityTone = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'neutral',
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}

function compactQuery(query) {
  return {
    pageNo: query.pageNo || 1,
    pageSize: query.pageSize || 20,
    keyword: query.keyword || undefined,
    status: query.status || undefined,
  }
}

function toForm(row) {
  return {
    id: row.id,
    name: row.name || '',
    companyName: row.companyName || '',
    phone: row.phone || '',
    email: row.email || '',
    source: row.source || '',
    status: row.status || recommendedLeadStatus,
    ownerId: row.ownerId || '',
    remark: row.remark || '',
  }
}

function toPayload(form) {
  return {
    ...form,
    name: (form.name || '').trim() || null,
    companyName: (form.companyName || '').trim() || null,
    phone: form.phone || null,
    email: form.email || null,
    source: form.source || null,
    status: form.status || recommendedLeadStatus,
    ownerId: form.ownerId || null,
    remark: form.remark || null,
  }
}

function toConvertForm(row) {
  return {
    leadId: row.id,
    convertType: 'CREATE_CUSTOMER',
    customerId: '',
    customerName: row.companyName || row.name || '',
    industry: '',
    contactName: row.name || '',
    contactPhone: row.phone || '',
    contactEmail: row.email || '',
    level: 'NORMAL',
    status: recommendedCustomerStatus,
    ownerId: row.ownerId || '',
    remark: row.remark || '',
  }
}

function toConvertPayload(form) {
  return {
    ...form,
    leadId: form.leadId,
    convertType: form.convertType || 'CREATE_CUSTOMER',
    customerId: form.convertType === 'BIND_CUSTOMER' ? form.customerId || null : null,
    customerName: form.convertType === 'CREATE_CUSTOMER' ? form.customerName || null : null,
    industry: form.convertType === 'CREATE_CUSTOMER' ? form.industry || null : null,
    contactName: form.convertType === 'CREATE_CUSTOMER' ? form.contactName || null : null,
    contactPhone: form.convertType === 'CREATE_CUSTOMER' ? form.contactPhone || null : null,
    contactEmail: form.convertType === 'CREATE_CUSTOMER' ? form.contactEmail || null : null,
    level: form.convertType === 'CREATE_CUSTOMER' ? form.level || 'NORMAL' : null,
    status: form.convertType === 'CREATE_CUSTOMER' ? form.status || recommendedCustomerStatus : null,
    ownerId: form.convertType === 'CREATE_CUSTOMER' ? form.ownerId || null : null,
    remark: form.convertType === 'CREATE_CUSTOMER' ? form.remark || null : null,
  }
}

function toConvertFormFromAi(lead, analysis) {
  const sourceLead = lead || analysis?.lead || { id: analysis?.leadId }
  const draft = analysis?.convertDraft || {}
  const base = toConvertForm(sourceLead)
  return {
    ...base,
    customerName: draft.customerName || base.customerName,
    industry: draft.industry || base.industry,
    contactName: draft.contactName || base.contactName,
    contactPhone: draft.contactPhone || base.contactPhone,
    contactEmail: draft.contactEmail || base.contactEmail,
    level: draft.level || base.level,
    status: draft.status || base.status,
    ownerId: draft.ownerId || base.ownerId,
    remark: draft.remark || base.remark,
  }
}

function formatPercent(value) {
  if (value === undefined || value === null || value === '') return '-'
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) return '-'
  return `${Math.round(numberValue * 100)}%`
}

function formatSeconds(value) {
  const seconds = Number(value || 0)
  if (seconds < 60) return `${seconds} 秒`
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}

export function LeadPage({ can, notify, navigate }) {
  const canManage = can('crm:lead:manage')
  const canCreate = canManage || can('crm:lead:create')
  const canDelete = can('crm:lead:manage')
  const canConvert = canManage && (can('crm:customer:manage') || can('crm:customer:edit'))
  const canBindCustomer = canConvert && can('crm:customer:view')
  const canAnalyze = can('crm:assistant:use') && (can('crm:lead:view') || canManage)
  const ownerOptions = useOwnerOptions(notify)
  const customerOptions = useCustomerOptions(notify, canBindCustomer)
  const { confirm, dialogProps } = useConfirmDialog()
  const [query, setQuery] = useState({ keyword: '', status: '', pageNo: 1, pageSize: 20 })
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(null)
  const [converting, setConverting] = useState(null)
  const [aiAnalysis, setAiAnalysis] = useState(null)
  const [analyzingId, setAnalyzingId] = useState(null)
  const [aiElapsed, setAiElapsed] = useState(0)

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const data = await api.lead.page(compactQuery(nextQuery))
      setPage(data || emptyPage)
      setQuery(nextQuery)
    } catch (err) {
      notify(err.message || '线索数据加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  useEffect(() => {
    if (!analyzingId) {
      setAiElapsed(0)
      return undefined
    }
    const timer = window.setInterval(() => setAiElapsed((value) => value + 1), 1000)
    return () => window.clearInterval(timer)
  }, [analyzingId])

  const search = (event) => {
    event.preventDefault()
    load({ ...query, pageNo: 1 })
  }

  const openDetail = async (row) => {
    setSelected(row)
    try {
      setSelected(await api.lead.detail(row.id))
    } catch (err) {
      notify(err.message || '线索详情加载失败', 'info')
    }
  }

  const saveLead = async (form) => {
    try {
      const saved = await api.lead.save(toPayload(form))
      notify('线索已保存', 'success')
      setEditing(null)
      setSelected(saved)
      load({ ...query, pageNo: form.id ? query.pageNo : 1 })
    } catch (err) {
      notify(err.message || '线索保存失败', 'info')
    }
  }

  const deleteLead = async (row) => {
    const confirmed = await confirm({
      title: '删除线索',
      description: '删除后该线索不会再出现在列表和统计中，请确认当前操作。',
      target: row.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.lead.delete(row.id)
      notify('线索已删除', 'success')
      if (selected?.id === row.id) {
        setSelected(null)
      }
      load({ ...query, pageNo: 1 })
    } catch (err) {
      notify(err.message || '线索删除失败', 'info')
    }
  }

  const convertLead = async (form) => {
    if (!form.leadId) {
      notify('线索编号不能为空', 'info')
      return
    }
    if (form.convertType === 'CREATE_CUSTOMER' && !form.customerName) {
      notify('客户名称不能为空', 'info')
      return
    }
    if (form.convertType === 'BIND_CUSTOMER' && !form.customerId) {
      notify('请选择要绑定的客户', 'info')
      return
    }
    try {
      const response = await api.lead.convertToCustomer(toConvertPayload(form))
      notify('线索已转为客户', 'success')
      setConverting(null)
      setSelected(response.lead)
      load(query)
      if (response.customer?.id && navigate) {
        navigate(`customers/detail/${encodeURIComponent(response.customer.id)}`)
      }
    } catch (err) {
      notify(err.message || '线索转客户失败', 'info')
    }
  }

  const analyzeLead = async (row) => {
    if (!row?.id) {
      notify('请选择要分析的线索', 'info')
      return
    }
    setAiElapsed(0)
    setAnalyzingId(row.id)
    setAiAnalysis({
      loading: true,
      available: true,
      success: false,
      leadId: row.id,
      leadName: row.name,
      message: '正在调用线索分析智能体',
    })
    try {
      const response = await api.assistant.analyzeLead({ leadId: row.id })
      setAiAnalysis(response)
      if (response.lead) {
        setSelected(response.lead)
      }
      notify(response.success ? 'AI 分析完成' : response.message || 'AI 暂不可用', response.success ? 'success' : 'info')
      if (response.lead) {
        load(query)
      }
    } catch (err) {
      setAiAnalysis({
        loading: false,
        available: false,
        success: false,
        leadId: row.id,
        leadName: row.name,
        message: err.message || 'AI 分析失败',
      })
      notify(err.message || 'AI 分析失败', 'info')
    } finally {
      setAnalyzingId(null)
    }
  }

  const applyAiDraft = (analysis) => {
    if (!analysis?.leadId) {
      notify('AI 分析结果缺少线索编号', 'info')
      return
    }
    const lead = analysis.lead || selected || records.find((item) => String(item.id) === String(analysis.leadId))
    setConverting(toConvertFormFromAi(lead, analysis))
    setAiAnalysis(null)
  }

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 20
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  return (
    <div className="page lead-list-page">
      <PageHeader
        title="线索管理"
        description={`当前真实线索 ${page.total || 0} 条`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>
            {canCreate && <Button icon={Plus} onClick={() => setEditing(emptyForm)}>新建线索</Button>}
          </>
        )}
      />

      <form className="filter-card customer-filter-card" onSubmit={search}>
        <div className="filter-search">
          <Search size={17} />
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="搜索姓名、公司、电话、邮箱或来源"
          />
        </div>
        <label>
          <span>状态</span>
          <select value={query.status} onChange={(event) => setQuery({ ...query, status: event.target.value })}>
            <option value="">全部状态</option>
            {leadStatusOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
          </select>
        </label>
        <Button type="submit" variant="secondary" icon={Search}>查询</Button>
      </form>

      <div className="lead-table-layout">
        <Card className="table-card customer-table-card">
          <div className="data-table-wrap">
            <table className="data-table customer-list-table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>公司</th>
                  <th>联系方式</th>
                  <th>来源</th>
                  <th>状态</th>
                  <th>负责人</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((row) => (
                  <tr
                    className={selected?.id === row.id ? 'selected-row' : ''}
                    key={row.id}
                    onClick={() => openDetail(row)}
                  >
                    <td><strong>{row.name}</strong><small>ID：{row.id}</small></td>
                    <td>{row.companyName || '-'}</td>
                    <td><span>{row.phone || '-'}</span><small>{row.email || '-'}</small></td>
                    <td>{row.source || '-'}</td>
                    <td><Badge dot tone={statusTone[row.status] || 'neutral'}>{statusText[row.status] || row.status || '-'}</Badge></td>
                    <td>{ownerName(row)}</td>
                    <td>{formatDateTime(row.updatedAt || row.createdAt)}</td>
                    <td>
                      <div className="table-action-row" onClick={(event) => event.stopPropagation()}>
                        {canManage && <button className="icon-button" onClick={() => setEditing(toForm(row))}><Edit2 size={17} /></button>}
                        {canAnalyze && (
                          <button
                            className="icon-button"
                            title="AI 分析"
                            disabled={String(analyzingId || '') === String(row.id)}
                            onClick={() => analyzeLead(row)}
                          >
                            <Sparkles size={17} />
                          </button>
                        )}
                        {canConvert && row.status !== 'CONVERTED' && (
                          <button className="icon-button" title="转为客户" onClick={() => setConverting(toConvertForm(row))}>
                            <UserPlus size={17} />
                          </button>
                        )}
                        {row.status === 'CONVERTED' && row.customerId && (
                          <button className="icon-button" title="查看客户" onClick={() => navigate(`customers/detail/${encodeURIComponent(row.customerId)}`)}>
                            <ArrowUpRight size={17} />
                          </button>
                        )}
                        {canDelete && <button className="icon-button" onClick={() => deleteLead(row)}><Trash2 size={17} /></button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!loading && !records.length && (
              <div className="empty-table">
                <Search size={26} />
                <b>暂无线索数据</b>
                <span>当前查询条件下没有真实线索记录</span>
              </div>
            )}
            {loading && (
              <div className="empty-table">
                <RefreshCw size={26} />
                <b>正在加载线索数据</b>
                <span>数据来自后台线索接口</span>
              </div>
            )}
          </div>
          <div className="table-footer">
            <span>共 {page.total || 0} 条，当前第 {currentPage} / {totalPages} 页</span>
            <div className="pagination">
              <button type="button" disabled={currentPage <= 1} onClick={() => load({ ...query, pageNo: currentPage - 1 })}>‹</button>
              <button type="button" className="active">{currentPage}</button>
              <button type="button" disabled={currentPage >= totalPages} onClick={() => load({ ...query, pageNo: currentPage + 1 })}>›</button>
            </div>
          </div>
        </Card>
      </div>

      <LeadDetailDrawer
        open={Boolean(selected)}
        data={selected}
        canWrite={canManage}
        canDelete={canDelete}
        canConvert={canConvert}
        canAnalyze={canAnalyze}
        analyzing={String(analyzingId || '') === String(selected?.id || '')}
        onConvert={() => setConverting(toConvertForm(selected))}
        onAnalyze={() => analyzeLead(selected)}
        onOpenCustomer={() => navigate(`customers/detail/${encodeURIComponent(selected.customerId)}`)}
        onEdit={() => setEditing(toForm(selected))}
        onDelete={() => deleteLead(selected)}
        onClose={() => setSelected(null)}
      />

      <LeadFormModal
        open={Boolean(editing)}
        form={editing || emptyForm}
        ownerOptions={ownerOptions}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={saveLead}
      />
      <LeadConvertModal
        open={Boolean(converting)}
        form={converting}
        ownerOptions={ownerOptions}
        customerOptions={customerOptions}
        canBindCustomer={canBindCustomer}
        onChange={setConverting}
        onClose={() => setConverting(null)}
        onSave={convertLead}
      />
      <LeadAiAnalysisModal
        open={Boolean(aiAnalysis)}
        analysis={aiAnalysis}
        elapsed={aiElapsed}
        canConvert={canConvert}
        onApplyDraft={applyAiDraft}
        onClose={() => setAiAnalysis(null)}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function LeadDetailDrawer({
  open,
  data,
  canWrite,
  canDelete,
  canConvert,
  canAnalyze,
  analyzing,
  onConvert,
  onAnalyze,
  onOpenCustomer,
  onEdit,
  onDelete,
  onClose,
}) {
  if (!data) return null
  const footer = (
    <div className="lead-detail-footer-actions">
      {canAnalyze && <Button variant="secondary" icon={Sparkles} onClick={onAnalyze}>{analyzing ? '分析中' : 'AI 分析'}</Button>}
      {canConvert && data.status !== 'CONVERTED' && <Button icon={UserPlus} onClick={onConvert}>转为客户</Button>}
      {data.status === 'CONVERTED' && data.customerId && <Button icon={ArrowUpRight} onClick={onOpenCustomer}>查看客户</Button>}
      {canWrite && <Button variant="secondary" icon={Edit2} onClick={onEdit}>编辑线索</Button>}
      {canDelete && <Button variant="ghost" icon={Trash2} onClick={onDelete}>删除</Button>}
    </div>
  )

  return (
    <Drawer open={open} title="线索详情" onClose={onClose} footer={footer}>
      <div className="lead-detail-panel lead-detail-drawer-body">
        <div className="customer-detail-head">
          <span className="company-avatar large">{(data.name || '?').slice(0, 1)}</span>
          <div>
            <h2>{data.name}</h2>
            <p>ID：{data.id}</p>
          </div>
        </div>
        <div className="channel-detail-grid customer-detail-grid lead-detail-grid">
          <DetailItem icon={Building2} label="公司" value={data.companyName} />
          <DetailItem icon={Phone} label="电话" value={data.phone} />
          <DetailItem icon={Mail} label="邮箱" value={data.email} />
          <DetailItem label="来源" value={data.source} />
          <DetailItem label="状态" value={statusText[data.status] || data.status} />
          <DetailItem label="已转客户" value={data.customerName || data.customerId} />
          <DetailItem label="转化人" value={data.convertedByName} />
          <DetailItem icon={CalendarDays} label="转化时间" value={formatDateTime(data.convertedAt)} />
          <DetailItem label="负责人" value={ownerName(data)} />
          <DetailItem icon={CalendarDays} label="创建时间" value={formatDateTime(data.createdAt)} />
          <DetailItem icon={CalendarDays} label="更新时间" value={formatDateTime(data.updatedAt)} />
        </div>
        <div className="channel-text-block lead-detail-text-block">
          <span>备注</span>
          <p>{data.remark || '暂无备注'}</p>
        </div>
        <div className="channel-text-block lead-detail-text-block lead-ai-section">
          <span>AI 分析</span>
          {data.aiSummary ? (
            <>
              <MarkdownText value={data.aiSummary} empty="暂无 AI 分析结果" />
              <div className="lead-ai-meta">
                <Badge tone="info">置信度 {formatPercent(data.aiConfidence)}</Badge>
                {data.aiSuggestedCustomerName && <Badge tone="success">建议客户：{data.aiSuggestedCustomerName}</Badge>}
                {data.aiSuggestedContactName && <Badge>联系人：{data.aiSuggestedContactName}</Badge>}
                {data.aiAnalyzedAt && <Badge>分析时间：{formatDateTime(data.aiAnalyzedAt)}</Badge>}
              </div>
            </>
          ) : (
            <p>暂无 AI 分析结果，可点击 AI 分析基于当前线索真实数据生成建议。</p>
          )}
        </div>
      </div>
    </Drawer>
  )
}

function LeadAiAnalysisModal({ open, analysis, elapsed, canConvert, onApplyDraft, onClose }) {
  if (!analysis) return null
  const draft = analysis.convertDraft || {}
  const customerProfile = analysis.customerProfile || {}
  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>关闭</Button>
      {canConvert && analysis.success && (
        <Button icon={UserPlus} onClick={() => onApplyDraft(analysis)}>带入转客户表单</Button>
      )}
    </>
  )

  return (
    <Modal open={open} title="线索 AI 分析" onClose={onClose} size="lg" footer={footer}>
      {analysis.loading ? (
        <div className="lead-ai-progress">
          <div className="lead-ai-progress-head">
            <Sparkles size={26} />
            <div>
              <b>{analysis.message || '正在分析线索'}</b>
              <span>{analysis.leadName || `线索 ${analysis.leadId || ''}`} · 已运行 {formatSeconds(elapsed)}</span>
            </div>
          </div>
          <div className="lead-ai-progress-track">
            <span />
          </div>
          <div className="lead-ai-progress-steps">
            <div className={elapsed >= 0 ? 'active' : ''}>
              <i>1</i>
              <span>读取线索真实数据</span>
            </div>
            <div className={elapsed >= 1 ? 'active' : ''}>
              <i>2</i>
              <span>挂载场景 Agent、提示词、Skills 和 MCP</span>
            </div>
            <div className={elapsed >= 3 ? 'active' : ''}>
              <i>3</i>
              <span>调用大模型生成结构化建议</span>
            </div>
            <div className={elapsed >= 6 ? 'active' : ''}>
              <i>4</i>
              <span>保存运行事件并整理转客户草稿</span>
            </div>
          </div>
          <p>当前不会构造假数据。模型无配置、密钥缺失或数据权限不足时，会直接返回失败原因。</p>
        </div>
      ) : !analysis.success ? (
        <div className="lead-ai-unavailable">
          <Sparkles size={24} />
          <b>{analysis.message || 'AI 暂不可用'}</b>
          <p>
            请检查是否已配置默认大模型、模型密钥是否存在，以及当前账号是否有线索数据权限。
            {analysis.runId && ` 运行编号：${analysis.runId}`}
          </p>
        </div>
      ) : (
        <div className="lead-ai-result">
          <div className="lead-ai-run-meta">
            <Badge tone="info">运行编号：{analysis.runId || '-'}</Badge>
            <Badge>会话编号：{analysis.conversationId || '-'}</Badge>
          </div>

          <div className="lead-ai-conclusion-card">
            <span><Sparkles size={22} /></span>
            <div>
              <div className="lead-ai-conclusion-head">
                <h3>{analysis.conclusionTitle || (analysis.recommendConvert ? '建议推进转化' : '暂不建议转化')}</h3>
                <Badge tone={aiPriorityTone[analysis.priority] || 'neutral'}>
                  {aiPriorityText[analysis.priority] || '中优先级'}
                </Badge>
              </div>
              <p>{analysis.salesConclusion || analysis.summary || '暂无销售结论'}</p>
              <div className="lead-ai-conclusion-tags">
                <Badge tone="info">{aiStageText[analysis.stage] || statusText[analysis.lead?.status] || '未知阶段'}</Badge>
                <Badge tone={analysis.recommendConvert ? 'success' : 'warning'}>
                  {analysis.recommendConvert ? '转化机会明确' : '先继续培育'}
                </Badge>
                <Badge>置信度 {formatPercent(analysis.confidence)}</Badge>
              </div>
            </div>
          </div>

          <div className="lead-ai-score-row">
            <div>
              <span>线索评分</span>
              <strong>{analysis.score ?? 0}</strong>
            </div>
            <div>
              <span>阶段判断</span>
              <strong>{aiStageText[analysis.stage] || statusText[analysis.lead?.status] || '-'}</strong>
            </div>
            <div>
              <span>建议动作</span>
              <strong>{analysis.recommendConvert ? '推进成交' : '继续跟进'}</strong>
            </div>
          </div>

          <div className="lead-ai-sales-grid">
            <AiListCard title="关键证据" items={analysis.keyFindings} empty="暂无关键证据" />
            <AiListCard title="下一步动作" items={analysis.nextActions?.length ? analysis.nextActions : [analysis.nextAction]} empty="暂无建议动作" active />
            <AiListCard title="风险提醒" items={analysis.riskWarnings} empty="暂无明显风险" warning />
          </div>

          {analysis.reason && (
            <div className="channel-text-block">
              <span>详细依据</span>
              <MarkdownText value={analysis.reason} empty="暂无详细依据" />
            </div>
          )}

          {analysis.summary && !analysis.salesConclusion && (
            <div className="channel-text-block">
              <span>分析摘要</span>
              <MarkdownText value={analysis.summary} empty="暂无摘要" />
            </div>
          )}

          <div className="lead-ai-function-note">
            <Badge tone="info">标准结果</Badge>
            <span>本次结果按 lead_analysis_result 结构解析，优先展示销售可执行信息。</span>
          </div>

          <div className="lead-ai-draft">
            <span>转客户草稿</span>
            <div>
              <DetailItem label="客户名称" value={draft.customerName} />
              <DetailItem label="行业" value={draft.industry} />
              <DetailItem label="联系人" value={draft.contactName} />
              <DetailItem label="电话" value={draft.contactPhone} />
              <DetailItem label="邮箱" value={draft.contactEmail} />
              <DetailItem label="客户级别" value={customerLevelText[draft.level] || draft.level} />
            </div>
          </div>

          <CustomerProfileCard profile={customerProfile} />
        </div>
      )}
    </Modal>
  )
}

function AiListCard({ title, items = [], empty, active = false, warning = false }) {
  const values = (items || []).filter(Boolean)
  return (
    <div className={`lead-ai-list-card ${active ? 'active' : ''} ${warning ? 'warning' : ''}`}>
      <span>{title}</span>
      {values.length ? (
        <ul>
          {values.map((item, index) => (
            <li key={index}>{item}</li>
          ))}
        </ul>
      ) : (
        <p>{empty}</p>
      )}
    </div>
  )
}

function CustomerProfileCard({ profile }) {
  const sourceUrls = (profile?.sourceUrls || []).filter(Boolean)
  const hasProfile = profile && (
    profile.available !== undefined
    || profile.companyName
    || profile.legalRepresentative
    || profile.keyPerson
    || profile.companyScale
    || profile.industry
    || profile.phone
    || profile.email
    || profile.website
    || profile.address
    || profile.registeredCapital
    || profile.sourceSummary
    || sourceUrls.length
  )
  if (!hasProfile) return null
  return (
    <div className="lead-ai-profile">
      <div className="lead-ai-profile-head">
        <span>AI搜索客户档案</span>
        <Badge tone={profile.available ? 'success' : 'warning'}>
          {profile.available ? '已检索公开信息' : '未检索到可靠公开信息'}
        </Badge>
      </div>
      <div>
        <DetailItem label="公司名称" value={profile.companyName} />
        <DetailItem label="公司负责人" value={profile.legalRepresentative || profile.keyPerson} />
        <DetailItem label="公司规模" value={profile.companyScale} />
        <DetailItem label="公司行业" value={profile.industry} />
        <DetailItem label="电话" value={profile.phone} />
        <DetailItem label="邮箱" value={profile.email} />
        <DetailItem label="官网" value={profile.website} />
        <DetailItem label="地址" value={profile.address} />
        <DetailItem label="注册资本" value={profile.registeredCapital} />
      </div>
      {profile.sourceSummary && (
        <div className="lead-ai-profile-summary">
          <span>来源摘要</span>
          <p>{profile.sourceSummary}</p>
        </div>
      )}
      {sourceUrls.length > 0 && (
        <div className="lead-ai-profile-sources">
          <span>来源链接</span>
          {sourceUrls.map((url, index) => (
            <a href={url} target="_blank" rel="noreferrer" key={index}>{url}</a>
          ))}
        </div>
      )}
    </div>
  )
}

function DetailItem({ icon: Icon, label, value }) {
  return (
    <div>
      <span>{Icon && <Icon size={13} />} {label}</span>
      <b>{value || '-'}</b>
    </div>
  )
}

function LeadConvertModal({ open, form, ownerOptions, customerOptions, canBindCustomer, onChange, onClose, onSave }) {
  const update = (patch) => onChange({ ...form, ...patch })
  const hasSelectedOwner = ownerOptions.some((item) => String(item.id) === String(form?.ownerId || ''))
  const hasSelectedCustomer = customerOptions.some((item) => String(item.id) === String(form?.customerId || ''))
  if (!form) return null

  return (
    <Modal
      open={open}
      title="线索转客户"
      onClose={onClose}
      size="lg"
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button icon={UserPlus} onClick={() => onSave(form)}>确认转客户</Button>
        </>
      )}
    >
      <div className="customer-form-grid">
        <Field label="转化方式">
          <select value={form.convertType || 'CREATE_CUSTOMER'} onChange={(event) => update({ convertType: event.target.value })}>
            <option value="CREATE_CUSTOMER">创建新客户</option>
            {canBindCustomer && <option value="BIND_CUSTOMER">绑定已有客户</option>}
          </select>
        </Field>

        {form.convertType === 'BIND_CUSTOMER' ? (
          <Field label="已有客户" required hint="列表来自真实客户接口，选择后会把线索绑定到该客户。">
            <select value={form.customerId || ''} onChange={(event) => update({ customerId: event.target.value })}>
              <option value="">请选择客户</option>
              {form.customerId && !hasSelectedCustomer && <option value={form.customerId}>当前客户</option>}
              {customerOptions.map((item) => <option value={item.id} key={item.id}>{customerOptionLabel(item)}</option>)}
            </select>
          </Field>
        ) : (
          <>
            <Field label="客户名称" required>
              <input value={form.customerName || ''} onChange={(event) => update({ customerName: event.target.value })} />
            </Field>
            <Field label="客户级别">
              <select value={form.level || 'NORMAL'} onChange={(event) => update({ level: event.target.value })}>
                {Object.entries(customerLevelText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
              </select>
            </Field>
            <Field label="客户状态">
              <select value={form.status || recommendedCustomerStatus} onChange={(event) => update({ status: event.target.value })}>
                {customerStatusOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
              </select>
            </Field>
            <Field label="负责人" hint="不选则由后台设置为当前登录用户">
              <select value={form.ownerId || ''} onChange={(event) => update({ ownerId: event.target.value })}>
                <option value="">默认当前登录用户</option>
                {form.ownerId && !hasSelectedOwner && <option value={form.ownerId}>当前负责人</option>}
                {ownerOptions.map((item) => <option value={item.id} key={item.id}>{ownerOptionLabel(item)}</option>)}
              </select>
            </Field>
            <Field label="行业">
              <input value={form.industry || ''} onChange={(event) => update({ industry: event.target.value })} />
            </Field>
            <Field label="联系人">
              <input value={form.contactName || ''} onChange={(event) => update({ contactName: event.target.value })} />
            </Field>
            <Field label="联系电话">
              <input value={form.contactPhone || ''} onChange={(event) => update({ contactPhone: event.target.value })} />
            </Field>
            <Field label="联系邮箱">
              <input value={form.contactEmail || ''} onChange={(event) => update({ contactEmail: event.target.value })} />
            </Field>
            <Field label="备注">
              <textarea rows="4" value={form.remark || ''} onChange={(event) => update({ remark: event.target.value })} />
            </Field>
          </>
        )}
      </div>
      <div className="channel-text-block">
        <span>AI 预留</span>
        <p>当前转化由人工确认提交，不依赖 AI。后续接入 AI 分析后，可把建议客户名、联系人和置信度带入该表单。</p>
      </div>
    </Modal>
  )
}

function LeadFormModal({ open, form, ownerOptions, onChange, onClose, onSave }) {
  const update = (patch) => onChange({ ...form, ...patch })
  const hasSelectedOwner = ownerOptions.some((item) => String(item.id) === String(form.ownerId || ''))
  return (
    <Modal
      open={open}
      title={form.id ? '编辑线索' : '新建线索'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={() => onSave(form)}>保存</Button>
        </>
      )}
    >
      <div className="customer-form-grid">
        <Field label="名称">
          <input value={form.name || ''} onChange={(event) => update({ name: event.target.value })} />
        </Field>
        <Field label="状态">
          <select value={form.status || recommendedLeadStatus} onChange={(event) => update({ status: event.target.value })}>
            {leadStatusOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
          </select>
        </Field>
        <Field label="公司名称">
          <input value={form.companyName || ''} onChange={(event) => update({ companyName: event.target.value })} />
        </Field>
        <Field label="负责人" hint="不选则由后台设置为当前登录用户">
          <select value={form.ownerId || ''} onChange={(event) => update({ ownerId: event.target.value })}>
            <option value="">默认当前登录用户</option>
            {form.ownerId && !hasSelectedOwner && <option value={form.ownerId}>当前负责人</option>}
            {ownerOptions.map((item) => <option value={item.id} key={item.id}>{ownerOptionLabel(item)}</option>)}
          </select>
        </Field>
        <Field label="联系电话">
          <input value={form.phone || ''} onChange={(event) => update({ phone: event.target.value })} />
        </Field>
        <Field label="联系邮箱">
          <input value={form.email || ''} onChange={(event) => update({ email: event.target.value })} />
        </Field>
        <Field label="线索来源">
          <input value={form.source || ''} onChange={(event) => update({ source: event.target.value })} />
        </Field>
        <Field label="备注">
          <textarea rows="4" value={form.remark || ''} onChange={(event) => update({ remark: event.target.value })} />
        </Field>
      </div>
    </Modal>
  )
}
