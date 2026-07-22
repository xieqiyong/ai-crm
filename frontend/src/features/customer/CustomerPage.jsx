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
  UserRound,
  X,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, ConfirmDialog, Field, Modal, PageHeader, useConfirmDialog } from '../../components'
import {
  customerLevelText,
  customerLevelTone,
  customerStatusOptions,
  customerStatusText,
  customerStatusTone,
  recommendedCustomerStatus,
} from '../../models/crmStatus'
import { ownerName, ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: [],
}

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

export function CustomerPage({ can, notify, navigate }) {
  const canWrite = can('crm:customer:manage') || can('crm:customer:edit')
  const canDelete = can('crm:customer:manage')
  const ownerOptions = useOwnerOptions(notify)
  const { confirm, dialogProps } = useConfirmDialog()
  const [query, setQuery] = useState({ keyword: '', status: '', pageNo: 1, pageSize: 20 })
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(null)

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const data = await api.customer.page(compactQuery(nextQuery))
      setPage(data || emptyPage)
      setQuery(nextQuery)
    } catch (err) {
      notify(err.message || '客户数据加载失败', 'info')
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
      setSelected(await api.customer.detail(row.id))
    } catch (err) {
      notify(err.message || '客户详情加载失败', 'info')
    }
  }

  const saveCustomer = async (form) => {
    if (!form.name || !form.name.trim()) {
      notify('客户名称不能为空', 'info')
      return
    }
    try {
      const saved = await api.customer.save(toPayload(form))
      notify('客户资料已保存', 'success')
      setEditing(null)
      setSelected(saved)
      load({ ...query, pageNo: form.id ? query.pageNo : 1 })
    } catch (err) {
      notify(err.message || '客户保存失败', 'info')
    }
  }

  const deleteCustomer = async (row) => {
    const confirmed = await confirm({
      title: '删除客户',
      description: '删除后该客户不会再出现在列表和统计中，请确认当前操作。',
      target: row.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.customer.delete(row.id)
      notify('客户已删除', 'success')
      if (selected?.id === row.id) {
        setSelected(null)
      }
      load({ ...query, pageNo: 1 })
    } catch (err) {
      notify(err.message || '客户删除失败', 'info')
    }
  }

  const openFullDetail = (row) => {
    if (!row?.id) return
    navigate(`customers/detail/${encodeURIComponent(row.id)}`)
  }

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 20
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  return (
    <div className="page customer-list-page">
      <PageHeader
        title="客户管理"
        description={`从客户列表进入详情，当前真实记录 ${page.total || 0} 条`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>
            {canWrite && <Button icon={Plus} onClick={() => setEditing(emptyForm)}>新建客户</Button>}
          </>
        )}
      />

      <form className="filter-card customer-filter-card" onSubmit={search}>
        <div className="filter-search">
          <Search size={17} />
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="搜索客户名称、行业、联系人、电话或邮箱"
          />
        </div>
        <label>
          <span>状态</span>
          <select value={query.status} onChange={(event) => setQuery({ ...query, status: event.target.value })}>
            <option value="">全部状态</option>
            {customerStatusOptions.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
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
                  <th>客户名称</th>
                  <th>行业</th>
                  <th>联系人</th>
                  <th>联系方式</th>
                  <th>客户级别</th>
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
                    <td>{row.industry || '-'}</td>
                    <td>{row.contactName || '-'}</td>
                    <td>
                      <span>{row.contactPhone || '-'}</span>
                      <small>{row.contactEmail || '-'}</small>
                    </td>
                    <td><Badge tone={customerLevelTone[row.level] || 'neutral'}>{customerLevelText[row.level] || row.level || '-'}</Badge></td>
                    <td><Badge dot tone={customerStatusTone[row.status] || 'neutral'}>{customerStatusText[row.status] || row.status || '-'}</Badge></td>
                    <td>{ownerName(row)}</td>
                    <td>{formatDateTime(row.updatedAt || row.createdAt)}</td>
                    <td>
                      <div className="table-action-row" onClick={(event) => event.stopPropagation()}>
                        {canWrite && (
                          <button className="icon-button" onClick={() => setEditing(toForm(row))}>
                            <Edit2 size={17} />
                          </button>
                        )}
                        <button className="icon-button" title="查看完整详情" onClick={() => openFullDetail(row)}>
                          <ArrowUpRight size={17} />
                        </button>
                        {canDelete && (
                          <button className="icon-button" onClick={() => deleteCustomer(row)}>
                            <Trash2 size={17} />
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
                <Building2 size={26} />
                <b>暂无客户数据</b>
                <span>当前查询条件下没有真实客户记录</span>
              </div>
            )}
            {loading && (
              <div className="empty-table">
                <RefreshCw size={26} />
                <b>正在加载客户数据</b>
                <span>数据来自后台客户接口</span>
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

        <CustomerDetailCard
          data={selected}
          canWrite={canWrite}
          canDelete={canDelete}
          onEdit={() => setEditing(toForm(selected))}
          onDelete={() => deleteCustomer(selected)}
          onOpenFull={() => openFullDetail(selected)}
          onClose={() => setSelected(null)}
        />
      </div>

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

function CustomerDetailCard({ data, canWrite, canDelete, onEdit, onDelete, onOpenFull, onClose }) {
  if (!data) {
    return (
      <Card className="customer-detail-panel empty">
        <span><Building2 size={24} /></span>
        <h2>请选择客户</h2>
        <p>先从左侧客户列表选择一条真实客户记录，再查看详情。</p>
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
        <Button icon={ArrowUpRight} onClick={onOpenFull}>完整详情</Button>
        {canWrite && <Button variant="secondary" icon={Edit2} onClick={onEdit}>编辑客户</Button>}
        {canDelete && <Button variant="ghost" icon={Trash2} onClick={onDelete}>删除</Button>}
      </div>
      <div className="channel-detail-grid customer-detail-grid">
        <DetailItem icon={Building2} label="行业" value={data.industry} />
        <DetailItem icon={UserRound} label="联系人" value={data.contactName} />
        <DetailItem icon={Phone} label="电话" value={data.contactPhone} />
        <DetailItem icon={Mail} label="邮箱" value={data.contactEmail} />
        <DetailItem label="客户级别" value={customerLevelText[data.level] || data.level} />
        <DetailItem label="客户状态" value={customerStatusText[data.status] || data.status} />
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
        <p>暂无 AI 分析结果，等待接入真实客户交互数据后生成。</p>
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
