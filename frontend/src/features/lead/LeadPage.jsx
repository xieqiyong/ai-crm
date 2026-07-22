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
  Trash2,
  UserPlus,
  UserRound,
  X,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, ConfirmDialog, Field, Modal, PageHeader, useConfirmDialog } from '../../components'
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
    name: form.name.trim(),
    companyName: form.companyName || null,
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

export function LeadPage({ can, notify, navigate }) {
  const canManage = can('crm:lead:manage')
  const canCreate = canManage || can('crm:lead:create')
  const canDelete = can('crm:lead:manage')
  const canConvert = canManage && (can('crm:customer:manage') || can('crm:customer:edit'))
  const canBindCustomer = canConvert && can('crm:customer:view')
  const ownerOptions = useOwnerOptions(notify)
  const customerOptions = useCustomerOptions(notify, canBindCustomer)
  const { confirm, dialogProps } = useConfirmDialog()
  const [query, setQuery] = useState({ keyword: '', status: '', pageNo: 1, pageSize: 20 })
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(null)
  const [converting, setConverting] = useState(null)

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
    if (!form.name || !form.name.trim()) {
      notify('线索名称不能为空', 'info')
      return
    }
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

      <div className="customer-list-layout">
        <Card className="table-card customer-table-card">
          <div className="data-table-wrap">
            <table className="data-table customer-list-table">
              <thead>
                <tr>
                  <th>线索名称</th>
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
              <button disabled={currentPage <= 1} onClick={() => load({ ...query, pageNo: currentPage - 1 })}>‹</button>
              <button className="active">{currentPage}</button>
              <button disabled={currentPage >= totalPages} onClick={() => load({ ...query, pageNo: currentPage + 1 })}>›</button>
            </div>
          </div>
        </Card>

        <LeadDetailCard
          data={selected}
          canWrite={canManage}
          canDelete={canDelete}
          canConvert={canConvert}
          onConvert={() => setConverting(toConvertForm(selected))}
          onOpenCustomer={() => navigate(`customers/detail/${encodeURIComponent(selected.customerId)}`)}
          onEdit={() => setEditing(toForm(selected))}
          onDelete={() => deleteLead(selected)}
          onClose={() => setSelected(null)}
        />
      </div>

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
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function LeadDetailCard({ data, canWrite, canDelete, canConvert, onConvert, onOpenCustomer, onEdit, onDelete, onClose }) {
  if (!data) {
    return (
      <Card className="customer-detail-panel empty">
        <span><UserRound size={24} /></span>
        <h2>请选择线索</h2>
        <p>先从左侧线索列表选择一条真实记录，再查看详情。</p>
      </Card>
    )
  }

  return (
    <Card className="customer-detail-panel">
      <div className="customer-detail-head">
        <span className="company-avatar large">{(data.name || '?').slice(0, 1)}</span>
        <div>
          <h2>{data.name}</h2>
          <p>ID：{data.id}</p>
        </div>
        <button className="icon-button" onClick={onClose}><X size={18} /></button>
      </div>
      <div className="customer-detail-actions">
        {canConvert && data.status !== 'CONVERTED' && <Button icon={UserPlus} onClick={onConvert}>转为客户</Button>}
        {data.status === 'CONVERTED' && data.customerId && <Button icon={ArrowUpRight} onClick={onOpenCustomer}>查看客户</Button>}
        {canWrite && <Button variant="secondary" icon={Edit2} onClick={onEdit}>编辑线索</Button>}
        {canDelete && <Button variant="ghost" icon={Trash2} onClick={onDelete}>删除</Button>}
      </div>
      <div className="channel-detail-grid customer-detail-grid">
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
      <div className="channel-text-block">
        <span>备注</span>
        <p>{data.remark || '暂无备注'}</p>
      </div>
      <div className="channel-text-block">
        <span>AI 分析</span>
        <p>暂无 AI 分析结果，后续可基于线索来源、沟通记录和渠道分析结果生成。</p>
      </div>
    </Card>
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
        <Field label="线索名称" required>
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
