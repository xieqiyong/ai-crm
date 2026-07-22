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
  MessageCircleMore,
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
import { Badge, Button, Card, ConfirmDialog, Field, Modal, PageHeader, useConfirmDialog } from '../../components'
import { ownerName, ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'
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

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
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
  const canDelete = can('crm:customer:manage')
  const ownerOptions = useOwnerOptions(notify)
  const { confirm, dialogProps } = useConfirmDialog()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState('概览')
  const [editing, setEditing] = useState(null)

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

  useEffect(() => {
    load()
  }, [customerId])

  const saveCustomer = async (form) => {
    if (!form.name || !form.name.trim()) {
      notify('客户名称不能为空', 'info')
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
              <Badge dot tone={customerStatusTone[data.status] || 'neutral'}>{customerStatusText[data.status] || data.status || '未设置状态'}</Badge>
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
        <CustomerOverview data={data} tags={tags} />
      ) : (
        <CustomerTabContent tab={tab} data={data} />
      )}

      <CustomerFormModal
        open={Boolean(editing)}
        form={editing || emptyForm}
        ownerOptions={ownerOptions}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={saveCustomer}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function CustomerOverview({ data, tags }) {
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

        <Card className="timeline-card">
          <div className="card-heading">
            <div>
              <h2><MessageCircleMore size={18} />交互时间轴</h2>
              <p>等待接入真实跟进记录、电话、邮件和会议数据。</p>
            </div>
          </div>
          <EmptyFeature icon={MessageCircleMore} title="暂无真实交互记录" description="客户基础资料已接入，交互数据需要后续跟进记录模块提供。" />
        </Card>

        <div className="customer-bottom-grid">
          <Card>
            <div className="card-heading">
              <div>
                <h2>关联商机</h2>
                <p>等待商机模块支持按客户聚合后展示。</p>
              </div>
            </div>
            <EmptyFeature icon={BriefcaseBusiness} title="暂无关联商机数据" description="当前不会展示非真实商机，后续接真实商机接口。" />
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
              <small>等待真实交互数据和 AI 分析接口</small>
            </div>
          </div>
          <span className="section-caption">当前客户资料</span>
          <blockquote>
            当前客户名称为 <b>{data.name}</b>，行业为 <b>{data.industry || '未填写'}</b>，主联系人为 <b>{data.contactName || '未填写'}</b>。
          </blockquote>
          <div className="ai-metrics">
            <div><small>联系电话</small><b>{data.contactPhone || '-'}</b></div>
            <div><small>联系邮箱</small><b>{data.contactEmail || '-'}</b></div>
          </div>
          <div className="risk-box">
            <Sparkles size={18} />
            <div>
              <b>AI 分析未生成</b>
              <p>暂未接入客户跟进、商机和行为数据，不生成非真实洞察。</p>
            </div>
          </div>
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

function CustomerTabContent({ tab, data }) {
  if (tab === '基础资料') {
    return <CustomerProfileMatrix data={data} />
  }
  if (tab === '联系人') {
    return <CustomerContactPanel data={data} />
  }

  const map = {
    商机: ['客户商机', '暂无真实关联商机数据，等待商机模块提供按客户查询接口。', BriefcaseBusiness],
    跟进记录: ['跟进记录', '暂无真实跟进记录，等待跟进记录模块接入。', MessageCircleMore],
    任务: ['客户任务', '暂无真实任务数据，等待任务模块接入。', CheckCircle2],
    相关文档: ['相关文档', '暂无真实文档数据，等待文档或知识库模块接入。', FileText],
    'AI 智能分析': ['AI 智能分析', '暂无真实 AI 分析结果，等待客户交互数据与 Agent 分析能力接入。', Sparkles],
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

function CustomerFormModal({ open, form, ownerOptions, onChange, onClose, onSave }) {
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
      <div className="customer-form-grid">
        <Field label="客户名称" required>
          <input value={form.name || ''} onChange={(event) => update({ name: event.target.value })} />
        </Field>
        <Field label="客户级别">
          <select value={form.level || 'NORMAL'} onChange={(event) => update({ level: event.target.value })}>
            <option value="NORMAL">普通客户</option>
            <option value="IMPORTANT">重点客户</option>
            <option value="STRATEGIC">战略客户</option>
          </select>
        </Field>
        <Field label="客户状态">
          <select value={form.status || recommendedCustomerStatus} onChange={(event) => update({ status: event.target.value })}>
            {customerStatusOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
          </select>
        </Field>
        <Field label="行业">
          <input value={form.industry || ''} onChange={(event) => update({ industry: event.target.value })} />
        </Field>
        <Field label="负责人" hint="不选则由后台设置为当前登录用户">
          <select value={form.ownerId || ''} onChange={(event) => update({ ownerId: event.target.value })}>
            <option value="">默认当前登录用户</option>
            {form.ownerId && !hasSelectedOwner && <option value={form.ownerId}>当前负责人</option>}
            {ownerOptions.map((item) => <option value={item.id} key={item.id}>{ownerOptionLabel(item)}</option>)}
          </select>
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
      </div>
    </Modal>
  )
}
