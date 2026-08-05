import { useEffect, useState } from 'react'
import {
  ArrowLeft,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  CheckCircle2,
  Edit2,
  FileText,
  Mail,
  Network,
  Phone,
  RefreshCw,
  Sparkles,
  Target,
  Trash2,
  UserRound,
  Users,
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
  OwnerAssignModal,
  PageHeader,
  Select,
  useConfirmDialog,
} from '../../components'
import { FollowupPanel } from '../followup/FollowupPanel'
import { ownerName, ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'
import { useProductOptions } from '../../hooks/useProductOptions'
import { useCustomerIndustryOptions } from '../../hooks/useCustomerIndustryOptions'
import { validateCustomerForm } from '../../models/customerForm'
import {
  OpportunityProductEditor,
  normalizeOpportunityProducts,
  toOpportunityProductPayload,
} from '../opportunity/OpportunityProductEditor'
import {
  customerLevelText,
  customerLevelTone,
  customerStatusOptions,
  customerStatusText,
  customerStatusTone,
  recommendedCustomerStatus,
} from '../../models/crmStatus'

const emptyForm = {
  name: '',
  industry: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  level: 'NORMAL',
  status: recommendedCustomerStatus,
  ownerId: '',
  remark: '',
}

const emptyOpportunityForm = {
  name: '',
  amount: '',
  stage: 'DISCOVERY',
  probability: '',
  expectedCloseDate: '',
  products: [],
  remark: '',
}

const opportunityStageText = {
  DISCOVERY: '需求发现',
  QUALIFICATION: '资格确认',
  PROPOSAL: '方案报价',
  NEGOTIATION: '商务谈判',
  WON: '已成交',
  LOST: '已丢单',
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatAmount(value) {
  const amount = Number(value || 0)
  return amount.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })
}

function toForm(row) {
  return {
    id: row.id,
    name: row.name || '',
    industry: row.industry || '',
    contactName: row.contactName || '',
    contactPhone: row.contactPhone || '',
    contactEmail: row.contactEmail || '',
    level: row.level || 'NORMAL',
    status: row.status || recommendedCustomerStatus,
    ownerId: row.ownerId || '',
    remark: row.remark || '',
  }
}

function toPayload(form) {
  return {
    ...form,
    name: form.name.trim(),
    industry: form.industry || null,
    contactName: form.contactName || null,
    contactPhone: form.contactPhone || null,
    contactEmail: form.contactEmail || null,
    level: form.level || 'NORMAL',
    status: form.status || recommendedCustomerStatus,
    ownerId: form.ownerId || null,
    remark: form.remark || null,
  }
}

function buildCustomerTags(data) {
  const tags = []
  if (data.level) {
    tags.push({ text: customerLevelText[data.level] || data.level, tone: customerLevelTone[data.level] || 'neutral' })
  }
  if (data.status) {
    tags.push({ text: customerStatusText[data.status] || data.status, tone: customerStatusTone[data.status] || 'neutral' })
  }
  if (data.industry) {
    tags.push({ text: data.industry, tone: 'info' })
  }
  if (data.contactName) {
    tags.push({ text: '有主联系人', tone: 'success' })
  }
  if (data.ownerId) {
    tags.push({ text: '已分配负责人', tone: 'neutral' })
  }
  return tags
}

export function CustomerDetailPage({ routeParams, can, notify, navigate }) {
  const customerId = routeParams?.id
  const canWrite = can('crm:customer:manage') || can('crm:customer:edit')
  const canAssign = can('crm:customer:assign')
  const canDelete = can('crm:customer:manage')
  const canViewProduct = can('crm:product:view') || can('crm:product:manage')
  const ownerOptions = useOwnerOptions(notify)
  const industryOptions = useCustomerIndustryOptions(notify, canWrite)
  const productOptions = useProductOptions(notify, true, canViewProduct)
  const { confirm, dialogProps } = useConfirmDialog()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState('概览')
  const [editing, setEditing] = useState(null)
  const [assigning, setAssigning] = useState(false)
  const [assignSubmitting, setAssignSubmitting] = useState(false)
  const [opportunities, setOpportunities] = useState([])
  const [opportunityEditing, setOpportunityEditing] = useState(null)
  const [summary, setSummary] = useState(null)
  const [summarizing, setSummarizing] = useState(false)
  const canViewFollowup = can('crm:followup:view')
  const canFollowup = can('crm:followup:manage') || can('crm:followup:create')
  const canViewOpportunity = can('crm:opportunity:view')
  const canCreateOpportunity = can('crm:opportunity:manage') || can('crm:opportunity:create')
  const canUseAi = can('crm:assistant:use') && can('crm:customer:view')

  const applySummaryStatus = (response) => {
    if (!response) return
    setSummary(response)
    setSummarizing(Boolean(response.running))
    if (response.summary) {
      setData((current) => {
        if (!current || String(current.id) !== String(response.customerId)) {
          return current
        }
        return {
          ...current,
          aiSummary: response.summary,
          aiAnalyzedAt: response.analyzedAt,
        }
      })
    }
  }

  const load = async () => {
    if (!customerId) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setData(await api.customer.detail(customerId))
    } catch (err) {
      notify(err.message || '客户详情加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  const loadSummaryStatus = async (id = customerId, silent = true) => {
    if (!id || !canUseAi) return
    try {
      const response = await api.assistant.customerSummaryStatus({ customerId: id })
      applySummaryStatus(response)
    } catch (err) {
      if (!silent) {
        notify(err.message || '客户 AI 深度总结状态加载失败', 'info')
      }
    }
  }

  useEffect(() => {
    load()
  }, [customerId])

  const loadOpportunities = async (id = customerId) => {
    if (!id || !canViewOpportunity) return
    try {
      const page = await api.opportunity.page({ customerId: id, pageNo: 1, pageSize: 20 })
      setOpportunities(page?.records || [])
    } catch (err) {
      notify(err.message || '客户商机加载失败', 'info')
    }
  }

  useEffect(() => {
    if (data?.id) {
      loadOpportunities(data.id)
      setSummary(data.aiSummary ? { summary: data.aiSummary, analyzedAt: data.aiAnalyzedAt } : null)
      loadSummaryStatus(data.id, true)
    }
  }, [data?.id, canUseAi])

  useEffect(() => {
    if (!data?.id || !canUseAi || !summarizing) {
      return undefined
    }
    const timer = window.setInterval(() => {
      loadSummaryStatus(data.id, true)
    }, 5000)
    return () => window.clearInterval(timer)
  }, [data?.id, canUseAi, summarizing])

  const saveCustomer = async (form) => {
    const validationMessage = validateCustomerForm(form, industryOptions)
    if (validationMessage) {
      notify(validationMessage, 'info')
      return
    }
    try {
      const saved = await api.customer.save(toPayload(form))
      setData(saved)
      setEditing(null)
      notify('客户资料已保存', 'success')
    } catch (err) {
      notify(err.message || '客户保存失败', 'info')
    }
  }

  const deleteCustomer = async () => {
    if (!data) return
    const confirmed = await confirm({
      title: '删除客户',
      description: '删除后该客户不会再出现在列表和统计中，请确认当前操作。',
      target: data.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.customer.delete(data.id)
      notify('客户已删除', 'success')
      navigate('customers')
    } catch (err) {
      notify(err.message || '客户删除失败', 'info')
    }
  }

  const assignCustomer = async (ownerId) => {
    if (!data?.id || !ownerId) {
      notify('请选择负责人', 'info')
      return
    }
    setAssignSubmitting(true)
    try {
      const assigned = await api.customer.assign({ id: data.id, ownerId })
      setData(assigned)
      setAssigning(false)
      notify('客户已分配', 'success')
    } catch (err) {
      notify(err.message || '客户分配失败', 'info')
    } finally {
      setAssignSubmitting(false)
    }
  }

  const saveOpportunity = async (form) => {
    if (!form.name || !form.name.trim()) {
      notify('商机名称不能为空', 'info')
      return
    }
    try {
      await api.opportunity.save({
        ...form,
        name: form.name.trim(),
        customerId: data.id,
        amount: form.amount === '' ? null : form.amount,
        probability: form.probability === '' ? null : Number(form.probability),
        expectedCloseDate: form.expectedCloseDate || null,
        remark: form.remark || null,
        products: toOpportunityProductPayload(form.products || []),
      })
      notify('客户商机已保存', 'success')
      setOpportunityEditing(null)
      loadOpportunities(data.id)
    } catch (err) {
      notify(err.message || '客户商机保存失败', 'info')
    }
  }

  const summarizeCustomer = async () => {
    if (!data?.id || summarizing) return
    setSummarizing(true)
    try {
      const response = await api.assistant.summarizeCustomer({ customerId: data.id })
      applySummaryStatus(response)
      notify(response.message || '客户总结已生成', response.success ? 'success' : 'info')
    } catch (err) {
      notify(err.message || '客户 AI 深度总结失败', 'info')
      setSummarizing(false)
    } finally {
      loadSummaryStatus(data.id, true)
    }
  }

  if (!customerId) {
    return (
      <div className="page customer-page">
        <PageHeader
          title="客户详情"
          description="缺少客户编号，无法加载客户详情。"
          actions={<Button icon={ArrowLeft} onClick={() => navigate('customers')}>返回客户列表</Button>}
        />
      </div>
    )
  }

  if (loading) {
    return (
      <div className="page customer-page">
        <PageHeader
          title="客户详情"
          description="正在读取后台客户详情接口"
          actions={<Button variant="secondary" icon={ArrowLeft} onClick={() => navigate('customers')}>返回客户列表</Button>}
        />
        <Card className="tab-placeholder">
          <span><RefreshCw size={26} /></span>
          <h2>正在加载客户详情</h2>
          <p>数据来自真实客户接口。</p>
        </Card>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="page customer-page">
        <PageHeader
          title="客户详情"
          description="当前客户不存在，或者没有权限访问。"
          actions={<Button icon={ArrowLeft} onClick={() => navigate('customers')}>返回客户列表</Button>}
        />
      </div>
    )
  }

  const tabs = ['概览', '基础资料', '联系人', '商机', '跟进记录', '任务', '相关文档', 'AI 智能分析']
  const tags = buildCustomerTags(data)

  return (
    <div className="page customer-page">
      <Card className="customer-hero">
        <div className="customer-identity">
          <span className="company-logo"><Building2 size={29} /></span>
          <div>
            <div className="customer-name">
              <h1>{data.name}</h1>
              <Badge tone={customerLevelTone[data.level] || 'neutral'}>{customerLevelText[data.level] || data.level || '未分级'}</Badge>
              <Badge dot tone={customerStatusTone[data.status] || 'neutral'}>
                {customerStatusText[data.status] || data.status || '未设置状态'}
              </Badge>
            </div>
            <div className="customer-meta">
              <span><Building2 size={15} />{data.industry || '未填写行业'}</span>
              <span><Users size={15} />{data.contactName || '未填写主联系人'}</span>
              <span><Phone size={15} />{data.contactPhone || '未填写电话'}</span>
              <span><Target size={15} />负责人：{ownerName(data)}</span>
              <span><CalendarDays size={15} />更新：{formatDateTime(data.updatedAt || data.createdAt)}</span>
            </div>
          </div>
        </div>
        <div className="customer-actions">
          <Button variant="secondary" icon={ArrowLeft} onClick={() => navigate('customers')}>返回列表</Button>
          <Button variant="secondary" icon={RefreshCw} onClick={load}>刷新</Button>
          {canWrite && <Button variant="secondary" icon={Edit2} onClick={() => setEditing(toForm(data))}>编辑资料</Button>}
          {canAssign && <Button variant="secondary" icon={UserRound} onClick={() => setAssigning(true)}>分配负责人</Button>}
          {canDelete && <Button variant="ghost" icon={Trash2} onClick={deleteCustomer}>删除</Button>}
        </div>
        <div className="customer-tabs">
          {tabs.map((item) => (
            <button className={tab === item ? 'active' : ''} onClick={() => setTab(item)} key={item}>
              {item.startsWith('AI') && <Sparkles size={15} />}
              {item}
            </button>
          ))}
        </div>
      </Card>

      {tab === '概览' ? (
        <CustomerOverview
          data={data}
          tags={tags}
          opportunities={opportunities}
          summary={summary}
          canUseAi={canUseAi}
          summarizing={summarizing}
          onSummarize={summarizeCustomer}
          canFollowup={canFollowup}
          canViewFollowup={canViewFollowup}
          notify={notify}
          onCreateOpportunity={canCreateOpportunity ? () => setOpportunityEditing(emptyOpportunityForm) : null}
        />
      ) : (
        <CustomerTabContent
          tab={tab}
          data={data}
          opportunities={opportunities}
          canFollowup={canFollowup}
          canViewFollowup={canViewFollowup}
          canUseAi={canUseAi}
          canCreateOpportunity={canCreateOpportunity}
          notify={notify}
          summary={summary}
          summarizing={summarizing}
          onSummarize={summarizeCustomer}
          onCreateOpportunity={() => setOpportunityEditing(emptyOpportunityForm)}
        />
      )}

      <CustomerFormModal
        open={Boolean(editing)}
        form={editing || emptyForm}
        ownerOptions={ownerOptions}
        industryOptions={industryOptions}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={saveCustomer}
      />
      <OwnerAssignModal
        open={assigning}
        title="分配客户负责人"
        recordName={data.name}
        currentOwnerId={data.ownerId}
        currentOwnerName={data.ownerId ? ownerName(data) : ''}
        ownerOptions={ownerOptions}
        submitting={assignSubmitting}
        onClose={() => setAssigning(false)}
        onConfirm={assignCustomer}
      />
      <OpportunityFormModal
        open={Boolean(opportunityEditing)}
        form={opportunityEditing || emptyOpportunityForm}
        productOptions={productOptions}
        onChange={setOpportunityEditing}
        onClose={() => setOpportunityEditing(null)}
        onSave={saveOpportunity}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function CustomerOverview({
  data,
  tags,
  opportunities,
  summary,
  canUseAi,
  summarizing,
  onSummarize,
  canFollowup,
  canViewFollowup,
  notify,
  onCreateOpportunity,
}) {
  return (
    <div className="customer-layout">
      <div className="customer-main">
        <div className="customer-top-grid">
          <Card className="relation-card">
            <div className="card-heading">
              <div>
                <h2><Network size={18} />客户联系人与决策链</h2>
                <p>当前仅展示客户主联系人字段，后续接入联系人模块后展示完整关系。</p>
              </div>
            </div>
            {data.contactName ? (
              <div className="relation-map">
                <span className="person ceo">
                  <b>{data.contactName.slice(0, 4)}</b>
                  <small>主联系人</small>
                </span>
              </div>
            ) : (
              <EmptyFeature icon={Users} title="暂无联系人数据" description="当前客户未填写主联系人，也尚未接入联系人列表接口。" />
            )}
          </Card>

          <Card className="profile-card">
            <div className="card-heading">
              <div>
                <h2><Target size={18} />客户画像标签</h2>
                <p>只基于当前真实客户资料生成展示标签。</p>
              </div>
            </div>
            <div className="tag-cloud">
              {tags.length ? tags.map((tag) => <Badge tone={tag.tone} key={tag.text}>{tag.text}</Badge>) : <Badge>暂无标签</Badge>}
            </div>
            <dl className="profile-details">
              <InfoRow label="客户编号" value={data.id} />
              <InfoRow label="租户编号" value={data.tenantId} />
              <InfoRow label="客户级别" value={customerLevelText[data.level] || data.level} />
              <InfoRow label="客户状态" value={customerStatusText[data.status] || data.status} />
              <InfoRow label="负责人" value={ownerName(data)} />
            </dl>
          </Card>
        </div>

        <FollowupPanel
          targetType="CUSTOMER"
          targetId={data.id}
          title="交互时间轴"
          canWrite={canFollowup}
          canView={canViewFollowup}
          notify={notify}
          pageSize={5}
          compact
        />

        <div className="customer-bottom-grid">
          <Card>
            <div className="card-heading">
              <div>
                <h2>关联商机</h2>
                <p>展示当前客户名下的真实商机。</p>
              </div>
              {onCreateOpportunity && <Button icon={BriefcaseBusiness} onClick={onCreateOpportunity}>新建商机</Button>}
            </div>
            <CustomerOpportunityList opportunities={opportunities} compact />
          </Card>
          <Card>
            <div className="card-heading">
              <div>
                <h2>备注与关注点</h2>
                <p>来自客户资料备注字段。</p>
              </div>
            </div>
            <div className="channel-text-block">
              <span>备注</span>
              <p>{data.remark || '暂无备注'}</p>
            </div>
          </Card>
        </div>
      </div>

      <aside className="customer-ai-column">
        <Card ai className="customer-ai-card">
          <div className="ai-card-title">
            <span><Sparkles size={19} /></span>
            <div>
              <h2>AI 客户深度总结</h2>
              <small>基于客户、商机和跟进记录生成</small>
            </div>
          </div>
          <span className="section-caption">客户总结</span>
          {summary?.summary ? (
            <MarkdownText value={summary.summary} />
          ) : (
            <blockquote>
              当前客户名称为 <b>{data.name}</b>，行业为 <b>{data.industry || '未填写'}</b>，主联系人为 <b>{data.contactName || '未填写'}</b>。
            </blockquote>
          )}
          <div className="ai-metrics">
            <div><small>联系电话</small><b>{data.contactPhone || '-'}</b></div>
            <div><small>联系邮箱</small><b>{data.contactEmail || '-'}</b></div>
          </div>
          {canUseAi && (
            <Button icon={Sparkles} onClick={onSummarize} disabled={summarizing}>
              {summarizing ? '生成中，稍后查看' : summary?.summary ? '重新生成深度总结' : '生成深度总结'}
            </Button>
          )}
          {summary?.message && (
            <div className="risk-box">
              <Sparkles size={18} />
              <div>
                <b>生成结果</b>
                <p>{summary.message}</p>
              </div>
            </div>
          )}
        </Card>
        <Card className="timing-card">
          <span><CalendarDays size={20} /></span>
          <div>
            <b>系统信息</b>
            <p>创建时间：{formatDateTime(data.createdAt)}；更新时间：{formatDateTime(data.updatedAt)}。</p>
          </div>
        </Card>
      </aside>
    </div>
  )
}

function CustomerTabContent({
  tab,
  data,
  opportunities,
  canFollowup,
  canViewFollowup,
  canUseAi,
  canCreateOpportunity,
  notify,
  summary,
  summarizing,
  onSummarize,
  onCreateOpportunity,
}) {
  if (tab === '基础资料') {
    return <CustomerProfileMatrix data={data} />
  }
  if (tab === '联系人') {
    return <CustomerContactPanel data={data} />
  }
  if (tab === '商机') {
    return (
      <Card>
        <div className="card-heading">
          <div>
            <h2>客户商机</h2>
            <p>按当前客户编号查询真实商机。</p>
          </div>
          {canCreateOpportunity && <Button icon={BriefcaseBusiness} onClick={onCreateOpportunity}>新建商机</Button>}
        </div>
        <CustomerOpportunityList opportunities={opportunities} />
      </Card>
    )
  }
  if (tab === '跟进记录') {
    return (
      <FollowupPanel
        targetType="CUSTOMER"
        targetId={data.id}
        canWrite={canFollowup}
        canView={canViewFollowup}
        notify={notify}
        pageSize={20}
      />
    )
  }
  if (tab === 'AI 智能分析') {
    return (
      <CustomerAiSummaryPanel
        data={data}
        summary={summary}
        canUseAi={canUseAi}
        summarizing={summarizing}
        onSummarize={onSummarize}
      />
    )
  }

  const map = {
    任务: ['客户任务', '暂无真实任务数据，等待任务模块接入。', CheckCircle2],
    相关文档: ['相关文档', '暂无真实文档数据，等待文档或知识库模块接入。', FileText],
  }
  const [title, desc, Icon] = map[tab] || ['客户信息', '暂无真实数据。', FileText]
  return <Card className="tab-placeholder"><span><Icon size={26} /></span><h2>{title}</h2><p>{desc}</p></Card>
}

function CustomerProfileMatrix({ data }) {
  return (
    <Card>
      <div className="card-heading">
        <div>
          <h2>基础资料</h2>
          <p>展示当前客户表已经接入的全部字段。</p>
        </div>
      </div>
      <div className="channel-detail-grid customer-detail-grid">
        <DetailItem icon={Building2} label="客户名称" value={data.name} />
        <DetailItem icon={Building2} label="行业" value={data.industry} />
        <DetailItem icon={UserRound} label="主联系人" value={data.contactName} />
        <DetailItem icon={Phone} label="联系电话" value={data.contactPhone} />
        <DetailItem icon={Mail} label="联系邮箱" value={data.contactEmail} />
        <DetailItem label="客户级别" value={customerLevelText[data.level] || data.level} />
        <DetailItem label="客户状态" value={customerStatusText[data.status] || data.status} />
        <DetailItem label="负责人" value={ownerName(data)} />
        <DetailItem label="租户编号" value={data.tenantId} />
        <DetailItem icon={CalendarDays} label="创建时间" value={formatDateTime(data.createdAt)} />
        <DetailItem icon={CalendarDays} label="更新时间" value={formatDateTime(data.updatedAt)} />
      </div>
      <div className="channel-text-block">
        <span>备注</span>
        <p>{data.remark || '暂无备注'}</p>
      </div>
    </Card>
  )
}

function CustomerContactPanel({ data }) {
  if (!data.contactName && !data.contactPhone && !data.contactEmail) {
    return <Card className="tab-placeholder"><span><Users size={26} /></span><h2>暂无联系人数据</h2><p>当前客户未填写主联系人，也尚未接入联系人列表模块。</p></Card>
  }
  return (
    <Card>
      <div className="card-heading">
        <div>
          <h2>主联系人</h2>
          <p>来自客户基础资料字段。</p>
        </div>
      </div>
      <div className="channel-detail-grid customer-detail-grid">
        <DetailItem icon={UserRound} label="姓名" value={data.contactName} />
        <DetailItem icon={Phone} label="电话" value={data.contactPhone} />
        <DetailItem icon={Mail} label="邮箱" value={data.contactEmail} />
      </div>
    </Card>
  )
}

function CustomerOpportunityList({ opportunities = [], compact = false }) {
  if (!opportunities.length) {
    return (
      <EmptyFeature
        icon={BriefcaseBusiness}
        title="暂无关联商机"
        description="当前客户还没有真实商机，可在客户详情中创建。"
      />
    )
  }
  return (
    <div className={`customer-opportunity-list ${compact ? 'compact' : ''}`}>
      {opportunities.map((item) => (
        <div className="customer-opportunity-item" key={item.id}>
          <span><BriefcaseBusiness size={18} /></span>
          <div>
            <b>{item.name}</b>
            <small>{opportunityStageText[item.stage] || item.stage || '未设置阶段'} · {formatAmount(item.amount)}</small>
            {item.products?.length > 0 && <small>产品：{item.products.map((product) => product.productName).join('、')}</small>}
          </div>
          <em>{item.probability == null ? '-' : `${item.probability}%`}</em>
        </div>
      ))}
    </div>
  )
}

function CustomerAiSummaryPanel({ data, summary, canUseAi, summarizing, onSummarize }) {
  return (
    <Card ai className="customer-ai-detail-card">
      <div className="ai-card-title">
        <span><Sparkles size={19} /></span>
        <div>
          <h2>AI 客户深度总结</h2>
          <small>基于客户资料、商机和跟进记录生成</small>
        </div>
      </div>
      {summary?.summary || data.aiSummary ? (
        <MarkdownText value={summary?.summary || data.aiSummary} />
      ) : (
        <div className="customer-empty-feature">
          <span><Sparkles size={22} /></span>
          <b>暂无客户深度总结</b>
          <p>点击生成后，会调用客户深度总结接口；未配置智能体时返回真实数据基础总结。</p>
        </div>
      )}
      {summary?.keyFindings?.length > 0 && (
        <div className="customer-ai-list">
          <b>关键事实</b>
          <ul>{summary.keyFindings.map((item, index) => <li key={index}>{item}</li>)}</ul>
        </div>
      )}
      {summary?.risks?.length > 0 && (
        <div className="customer-ai-list warning">
          <b>风险提醒</b>
          <ul>{summary.risks.map((item, index) => <li key={index}>{item}</li>)}</ul>
        </div>
      )}
      {summary?.nextActions?.length > 0 && (
        <div className="customer-ai-list active">
          <b>下一步动作</b>
          <ul>{summary.nextActions.map((item, index) => <li key={index}>{item}</li>)}</ul>
        </div>
      )}
      <div className="customer-detail-actions">
        {canUseAi && (
          <Button icon={Sparkles} onClick={onSummarize} disabled={summarizing}>
            {summarizing ? '总结中' : '生成深度总结'}
          </Button>
        )}
      </div>
    </Card>
  )
}

function InfoRow({ label, value }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value || '-'}</dd>
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

function EmptyFeature({ icon: Icon, title, description }) {
  return (
    <div className="customer-empty-feature">
      <span><Icon size={22} /></span>
      <b>{title}</b>
      <p>{description}</p>
    </div>
  )
}

function OpportunityFormModal({ open, form, productOptions, onChange, onClose, onSave }) {
  if (!open || !form) return null
  const update = (patch) => onChange({ ...form, ...patch })
  return (
    <Modal
      open={open}
      size="xl"
      title="新建客户商机"
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={() => onSave(form)}>保存商机</Button>
        </>
      )}
    >
      <div className="customer-form-grid">
        <Field label="商机名称" required>
          <input value={form.name || ''} onChange={(event) => update({ name: event.target.value })} />
        </Field>
        <Field label="阶段">
          <select value={form.stage || 'DISCOVERY'} onChange={(event) => update({ stage: event.target.value })}>
            {Object.entries(opportunityStageText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </Field>
        <Field label="商机金额">
          <input value={form.amount || ''} onChange={(event) => update({ amount: event.target.value })} />
        </Field>
        <Field label="赢率">
          <input
            type="number"
            min="0"
            max="100"
            value={form.probability || ''}
            onChange={(event) => update({ probability: event.target.value })}
          />
        </Field>
        <Field label="预计成交日期">
          <input
            type="date"
            value={form.expectedCloseDate || ''}
            onChange={(event) => update({ expectedCloseDate: event.target.value })}
          />
        </Field>
        <Field label="产品明细" className="wide-field" as="div">
          <OpportunityProductEditor
            products={normalizeOpportunityProducts(form.products || [])}
            productOptions={productOptions || []}
            onChange={(products) => update({ products })}
          />
        </Field>
        <Field label="备注">
          <textarea rows="4" value={form.remark || ''} onChange={(event) => update({ remark: event.target.value })} />
        </Field>
      </div>
    </Modal>
  )
}

function CustomerFormModal({ open, form, ownerOptions, industryOptions, onChange, onClose, onSave }) {
  const update = (patch) => onChange({ ...form, ...patch })
  const hasSelectedOwner = ownerOptions.some((item) => String(item.id) === String(form.ownerId || ''))
  return (
    <Modal
      open={open}
      title={form.id ? '编辑客户' : '新建客户'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={() => onSave(form)}>保存</Button>
        </>
      )}
    >
      <div className="customer-identity-note">
        <b>客户主体与联系人</b>
        <span>客户名称填写企业、机构或个人客户的主体名称；主要联系人填写该客户内部与销售直接沟通的对接人。</span>
      </div>
      <div className="customer-form-grid">
        <Field label="客户名称" required hint="企业、机构或个人客户的主体名称">
          <input value={form.name || ''} onChange={(event) => update({ name: event.target.value })} />
        </Field>
        <Field label="客户级别" required>
          <select value={form.level || 'NORMAL'} onChange={(event) => update({ level: event.target.value })}>
            <option value="NORMAL">普通客户</option>
            <option value="IMPORTANT">重点客户</option>
            <option value="STRATEGIC">战略客户</option>
          </select>
        </Field>
        <Field label="客户状态" required>
          <select value={form.status || recommendedCustomerStatus} onChange={(event) => update({ status: event.target.value })}>
            {customerStatusOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
          </select>
        </Field>
        <Field label="行业" required>
          <Select
            searchable
            value={form.industry || ''}
            options={industryOptions}
            placeholder="请选择行业"
            searchPlaceholder="搜索行业"
            onChange={(industry) => update({ industry })}
          />
        </Field>
        <Field label="负责人" required>
          <select value={form.ownerId || ''} onChange={(event) => update({ ownerId: event.target.value })}>
            <option value="">请选择负责人</option>
            {form.ownerId && !hasSelectedOwner && <option value={form.ownerId}>当前负责人</option>}
            {ownerOptions.map((item) => <option value={item.id} key={item.id}>{ownerOptionLabel(item)}</option>)}
          </select>
        </Field>
        <Field label="主要联系人" required hint="客户内部与销售直接沟通的主要对接人姓名">
          <input value={form.contactName || ''} onChange={(event) => update({ contactName: event.target.value })} />
        </Field>
        <Field label="联系电话" required>
          <input value={form.contactPhone || ''} onChange={(event) => update({ contactPhone: event.target.value })} />
        </Field>
        <Field label="联系邮箱" required>
          <input type="email" value={form.contactEmail || ''} onChange={(event) => update({ contactEmail: event.target.value })} />
        </Field>
        <Field label="备注">
          <textarea rows="4" value={form.remark || ''} onChange={(event) => update({ remark: event.target.value })} />
        </Field>
      </div>
    </Modal>
  )
}
