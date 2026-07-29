import { useEffect, useState } from 'react'
import {
  ArrowLeft,
  ArrowUpRight,
  Building2,
  CalendarDays,
  Edit2,
  Mail,
  Phone,
  RefreshCw,
  Sparkles,
  Trash2,
  UserPlus,
  UserRound,
} from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  CollapsibleMarkdown,
  ConfirmDialog,
  OwnerAssignModal,
  PageHeader,
  useConfirmDialog,
} from '../../components'
import { useCustomerOptions } from '../../hooks/useCustomerOptions'
import { ownerName, useOwnerOptions } from '../../hooks/useOwnerOptions'
import {
  leadStatusText,
  leadStatusTone,
} from '../../models/crmStatus'
import { leadSourceText } from '../../models/crmSource'
import { FollowupPanel } from '../followup/FollowupPanel'
import {
  LeadAiAnalysisModal,
  LeadConvertModal,
  LeadFormModal,
  formatDateTime,
  formatPercent,
  toConvertForm,
  toConvertFormFromAi,
  toConvertPayload,
  toForm,
  toPayload,
} from './LeadPage'

export function LeadDetailPage({ routeParams, can, notify, navigate }) {
  const leadId = routeParams?.id
  const canManage = can('crm:lead:manage')
  const canAssign = can('crm:lead:assign')
  const canDelete = canManage
  const canConvert = canManage && (can('crm:customer:manage') || can('crm:customer:edit'))
  const canBindCustomer = canConvert && can('crm:customer:view')
  const canAnalyze = can('crm:assistant:use') && (can('crm:lead:view') || canManage)
  const canViewFollowup = can('crm:followup:view')
  const canFollowup = can('crm:followup:manage') || can('crm:followup:create')
  const ownerOptions = useOwnerOptions(notify)
  const customerOptions = useCustomerOptions(notify, canBindCustomer)
  const { confirm, dialogProps } = useConfirmDialog()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)
  const [assigning, setAssigning] = useState(false)
  const [assignSubmitting, setAssignSubmitting] = useState(false)
  const [converting, setConverting] = useState(null)
  const [aiAnalysis, setAiAnalysis] = useState(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [aiElapsed, setAiElapsed] = useState(0)

  const load = async () => {
    if (!leadId) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setData(await api.lead.detail(leadId))
    } catch (err) {
      setData(null)
      notify(err.message || '线索详情加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [leadId])

  useEffect(() => {
    if (!analyzing) {
      setAiElapsed(0)
      return undefined
    }
    const timer = window.setInterval(() => setAiElapsed((value) => value + 1), 1000)
    return () => window.clearInterval(timer)
  }, [analyzing])

  const saveLead = async (form) => {
    try {
      const saved = await api.lead.save(toPayload(form))
      setData(saved)
      setEditing(null)
      notify('线索资料已保存', 'success')
    } catch (err) {
      notify(err.message || '线索保存失败', 'info')
    }
  }

  const deleteLead = async () => {
    if (!data) return
    const confirmed = await confirm({
      title: '删除线索',
      description: '删除后该线索不会再出现在列表和统计中，请确认当前操作。',
      target: data.name || data.companyName,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.lead.delete(data.id)
      notify('线索已删除', 'success')
      navigate('leads')
    } catch (err) {
      notify(err.message || '线索删除失败', 'info')
    }
  }

  const assignLead = async (ownerId) => {
    if (!data?.id || !ownerId) {
      notify('请选择负责人', 'info')
      return
    }
    setAssignSubmitting(true)
    try {
      const assigned = await api.lead.assign({ id: data.id, ownerId })
      setData(assigned)
      setAssigning(false)
      notify('线索已分配', 'success')
    } catch (err) {
      notify(err.message || '线索分配失败', 'info')
    } finally {
      setAssignSubmitting(false)
    }
  }

  const convertLead = async (form) => {
    if (!form?.leadId) {
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
      setData(response.lead)
      setConverting(null)
      notify('线索已转为客户', 'success')
      if (response.customer?.id) {
        navigate(`customers/detail/${encodeURIComponent(response.customer.id)}`)
      }
    } catch (err) {
      notify(err.message || '线索转客户失败', 'info')
    }
  }

  const analyzeLead = async () => {
    if (!data?.id || analyzing) return
    setAiElapsed(0)
    setAnalyzing(true)
    setAiAnalysis({
      loading: true,
      available: true,
      success: false,
      leadId: data.id,
      leadName: data.name,
      message: '正在调用线索分析智能体',
    })
    try {
      const response = await api.assistant.analyzeLead({ leadId: data.id })
      const normalizedResponse = {
        ...response,
        leadId: response.leadId || data.id,
        leadName: response.leadName || data.name,
        lead: response.lead || data,
      }
      setAiAnalysis(normalizedResponse)
      if (response.lead) {
        setData(response.lead)
      }
      notify(
        response.success ? 'AI 分析完成' : response.message || 'AI 暂不可用',
        response.success ? 'success' : 'info',
      )
    } catch (err) {
      setAiAnalysis({
        loading: false,
        available: false,
        success: false,
        leadId: data.id,
        leadName: data.name,
        message: err.message || 'AI 分析失败',
      })
      notify(err.message || 'AI 分析失败', 'info')
    } finally {
      setAnalyzing(false)
    }
  }

  const applyAiDraft = (analysis) => {
    setConverting(toConvertFormFromAi(analysis?.lead || data, analysis))
    setAiAnalysis(null)
  }

  if (!leadId) {
    return (
      <div className="page lead-detail-page">
        <PageHeader
          title="线索详情"
          description="缺少线索编号，无法加载线索详情。"
          actions={<Button icon={ArrowLeft} onClick={() => navigate('leads')}>返回线索列表</Button>}
        />
      </div>
    )
  }

  if (loading) {
    return (
      <div className="page lead-detail-page">
        <PageHeader
          title="线索详情"
          description="正在读取后台线索详情接口"
          actions={<Button variant="secondary" icon={ArrowLeft} onClick={() => navigate('leads')}>返回线索列表</Button>}
        />
        <Card className="tab-placeholder">
          <span><RefreshCw size={26} /></span>
          <h2>正在加载线索详情</h2>
          <p>数据来自真实线索接口。</p>
        </Card>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="page lead-detail-page">
        <PageHeader
          title="线索详情"
          description="当前线索不存在，或者没有权限访问。"
          actions={<Button icon={ArrowLeft} onClick={() => navigate('leads')}>返回线索列表</Button>}
        />
      </div>
    )
  }

  return (
    <div className="page lead-detail-page">
      <Card className="customer-hero lead-detail-hero">
        <div className="customer-identity">
          <span className="company-logo lead-logo">{String(data.name || data.companyName || '线').slice(0, 1)}</span>
          <div>
            <div className="customer-name">
              <h1>{data.name || data.companyName || '未命名线索'}</h1>
              <Badge dot tone={leadStatusTone[data.status] || 'neutral'}>
                {leadStatusText[data.status] || data.status || '未设置状态'}
              </Badge>
              <Badge tone="info">{leadSourceText[data.source] || data.source || '未填写来源'}</Badge>
            </div>
            <div className="customer-meta">
              <span><Building2 size={15} />{data.companyName || '未填写公司'}</span>
              <span><Phone size={15} />{data.phone || '未填写电话'}</span>
              <span><UserRound size={15} />负责人：{ownerName(data)}</span>
              <span><CalendarDays size={15} />更新：{formatDateTime(data.updatedAt || data.createdAt)}</span>
            </div>
          </div>
        </div>
        <div className="customer-actions lead-detail-actions">
          <Button variant="secondary" icon={ArrowLeft} onClick={() => navigate('leads')}>返回列表</Button>
          <Button variant="secondary" icon={RefreshCw} onClick={load}>刷新</Button>
          {canAnalyze && (
            <Button variant="secondary" icon={Sparkles} disabled={analyzing} onClick={analyzeLead}>
              {analyzing ? '分析中' : 'AI 分析'}
            </Button>
          )}
          {canConvert && data.status !== 'CONVERTED' && (
            <Button icon={UserPlus} onClick={() => setConverting(toConvertForm(data))}>转为客户</Button>
          )}
          {data.status === 'CONVERTED' && data.customerId && (
            <Button icon={ArrowUpRight} onClick={() => navigate(`customers/detail/${encodeURIComponent(data.customerId)}`)}>
              查看客户
            </Button>
          )}
          {canManage && <Button variant="secondary" icon={Edit2} onClick={() => setEditing(toForm(data))}>编辑资料</Button>}
          {canAssign && <Button variant="secondary" icon={UserPlus} onClick={() => setAssigning(true)}>分配负责人</Button>}
          {canDelete && <Button variant="ghost" icon={Trash2} onClick={deleteLead}>删除</Button>}
        </div>
      </Card>

      <Card className="lead-detail-overview">
        <div className="card-heading">
          <div>
            <h2>线索基础资料</h2>
            <p>关键字段在首屏平铺展示，长编号按字符串完整保留。</p>
          </div>
        </div>
        <div className="lead-detail-info-grid">
          <InfoCell label="线索编号" value={data.id} />
          <InfoCell label="名称" value={data.name} />
          <InfoCell label="公司名称" value={data.companyName} icon={Building2} />
          <InfoCell label="负责人" value={ownerName(data)} icon={UserRound} />
          <InfoCell label="联系电话" value={data.phone} icon={Phone} />
          <InfoCell label="联系邮箱" value={data.email} icon={Mail} />
          <InfoCell label="线索来源" value={leadSourceText[data.source] || data.source} />
          <InfoCell label="当前状态" value={leadStatusText[data.status] || data.status} />
          <InfoCell label="关联客户" value={data.customerName || data.customerId} />
          <InfoCell label="转化负责人" value={data.convertedByName} />
          <InfoCell label="转化时间" value={formatDateTime(data.convertedAt)} icon={CalendarDays} />
          <InfoCell label="转化方式" value={data.convertedType} />
          <InfoCell label="创建时间" value={formatDateTime(data.createdAt)} icon={CalendarDays} />
          <InfoCell label="更新时间" value={formatDateTime(data.updatedAt)} icon={CalendarDays} />
        </div>
      </Card>

      <div className="lead-detail-content-grid">
        <Card className="lead-detail-content-card">
          <div className="card-heading">
            <div>
              <h2>备注与导入信息</h2>
              <p>Excel 其余业务字段会按 Markdown 结构保留在这里。</p>
            </div>
          </div>
          <CollapsibleMarkdown
            value={data.remark}
            empty="暂无备注"
            maxHeight={238}
            expandText="展开完整备注"
            collapseText="收起备注"
          />
        </Card>
        <Card ai className="lead-detail-content-card lead-detail-ai-card">
          <div className="card-heading">
            <div>
              <h2><Sparkles size={18} />最近 AI 分析</h2>
              <p>展示最近一次已保存的真实分析结论。</p>
            </div>
            {canAnalyze && (
              <Button variant="secondary" icon={Sparkles} disabled={analyzing} onClick={analyzeLead}>
                {data.aiSummary ? '重新分析' : '开始分析'}
              </Button>
            )}
          </div>
          <CollapsibleMarkdown
            value={data.aiSummary}
            empty="暂无 AI 分析结果"
            maxHeight={238}
            expandText="展开完整分析"
            collapseText="收起分析"
          />
          {data.aiSummary && (
            <div className="lead-ai-meta">
              <Badge tone="info">置信度 {formatPercent(data.aiConfidence)}</Badge>
              {data.aiSuggestedCustomerName && <Badge tone="success">建议客户：{data.aiSuggestedCustomerName}</Badge>}
              {data.aiSuggestedContactName && <Badge>联系人：{data.aiSuggestedContactName}</Badge>}
              {data.aiAnalyzedAt && <Badge>分析时间：{formatDateTime(data.aiAnalyzedAt)}</Badge>}
            </div>
          )}
        </Card>
      </div>

      <FollowupPanel
        targetType="LEAD"
        targetId={data.id}
        title="线索交互时间轴"
        canWrite={canFollowup}
        canView={canViewFollowup}
        notify={notify}
        pageSize={8}
      />

      <LeadFormModal
        open={Boolean(editing)}
        form={editing || toForm(data)}
        ownerOptions={ownerOptions}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={saveLead}
      />
      <OwnerAssignModal
        open={assigning}
        title="分配线索负责人"
        recordName={data.companyName || data.name}
        currentOwnerId={data.ownerId}
        currentOwnerName={data.ownerId ? ownerName(data) : ''}
        ownerOptions={ownerOptions}
        submitting={assignSubmitting}
        onClose={() => setAssigning(false)}
        onConfirm={assignLead}
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

function InfoCell({ icon: Icon, label, value }) {
  return (
    <div className="lead-detail-info-cell">
      <span>{Icon && <Icon size={14} />}{label}</span>
      <strong title={value || ''}>{value || '-'}</strong>
    </div>
  )
}
