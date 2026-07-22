import { useEffect, useMemo, useState } from 'react'
import {
  BriefcaseBusiness,
  CalendarDays,
  CircleDollarSign,
  Edit2,
  Plus,
  RefreshCw,
  Search,
  Target,
  Trash2,
  X,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, ConfirmDialog, Field, Modal, PageHeader, useConfirmDialog } from '../../components'
import { ownerName, ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'

const stageText = {
  DISCOVERY: '需求发现',
  QUALIFICATION: '资格确认',
  PROPOSAL: '方案报价',
  NEGOTIATION: '商务谈判',
  WON: '已成交',
  LOST: '已丢单',
}

const stageTone = {
  DISCOVERY: 'neutral',
  QUALIFICATION: 'info',
  PROPOSAL: 'warning',
  NEGOTIATION: 'warning',
  WON: 'success',
  LOST: 'danger',
}

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: [],
}

const emptyForm = {
  name: '',
  customerId: '',
  amount: '',
  stage: 'DISCOVERY',
  probability: '',
  expectedCloseDate: '',
  ownerId: '',
  remark: '',
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

function compactQuery(query) {
  return {
    pageNo: query.pageNo || 1,
    pageSize: query.pageSize || 20,
    keyword: query.keyword || undefined,
    stage: query.stage || undefined,
  }
}

function toForm(row) {
  return {
    id: row.id,
    name: row.name || '',
    customerId: row.customerId || '',
    amount: row.amount || '',
    stage: row.stage || 'DISCOVERY',
    probability: row.probability || '',
    expectedCloseDate: row.expectedCloseDate || '',
    ownerId: row.ownerId || '',
    remark: row.remark || '',
  }
}

function toPayload(form) {
  return {
    ...form,
    name: form.name.trim(),
    customerId: form.customerId || null,
    amount: form.amount === '' ? null : form.amount,
    stage: form.stage || 'DISCOVERY',
    probability: form.probability === '' ? null : Number(form.probability),
    expectedCloseDate: form.expectedCloseDate || null,
    ownerId: form.ownerId || null,
    remark: form.remark || null,
  }
}

export function OpportunityPage({ can, notify }) {
  const canManage = can('crm:opportunity:manage')
  const canCreate = canManage || can('crm:opportunity:create')
  const canDelete = can('crm:opportunity:manage')
  const ownerOptions = useOwnerOptions(notify)
  const { confirm, dialogProps } = useConfirmDialog()
  const [query, setQuery] = useState({ keyword: '', stage: '', pageNo: 1, pageSize: 20 })
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(null)

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const data = await api.opportunity.page(compactQuery(nextQuery))
      setPage(data || emptyPage)
      setQuery(nextQuery)
    } catch (err) {
      notify(err.message || '商机数据加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const stageSummary = useMemo(() => {
    const records = page.records || []
    return Object.keys(stageText).map((stage) => ({
      stage,
      label: stageText[stage],
      count: records.filter((row) => row.stage === stage).length,
    }))
  }, [page.records])

  const search = (event) => {
    event.preventDefault()
    load({ ...query, pageNo: 1 })
  }

  const openDetail = async (row) => {
    setSelected(row)
    try {
      setSelected(await api.opportunity.detail(row.id))
    } catch (err) {
      notify(err.message || '商机详情加载失败', 'info')
    }
  }

  const saveOpportunity = async (form) => {
    if (!form.name || !form.name.trim()) {
      notify('商机名称不能为空', 'info')
      return
    }
    try {
      const saved = await api.opportunity.save(toPayload(form))
      notify('商机已保存', 'success')
      setEditing(null)
      setSelected(saved)
      load({ ...query, pageNo: form.id ? query.pageNo : 1 })
    } catch (err) {
      notify(err.message || '商机保存失败', 'info')
    }
  }

  const deleteOpportunity = async (row) => {
    const confirmed = await confirm({
      title: '删除商机',
      description: '删除后该商机不会再出现在列表和统计中，请确认当前操作。',
      target: row.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.opportunity.delete(row.id)
      notify('商机已删除', 'success')
      if (selected?.id === row.id) {
        setSelected(null)
      }
      load({ ...query, pageNo: 1 })
    } catch (err) {
      notify(err.message || '商机删除失败', 'info')
    }
  }

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 20
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  return (
    <div className="page opportunity-list-page">
      <PageHeader
        title="商机管理"
        description={`当前真实商机 ${page.total || 0} 条`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>
            {canCreate && <Button icon={Plus} onClick={() => setEditing(emptyForm)}>新建商机</Button>}
          </>
        )}
      />

      <div className="module-stats opportunity-stage-stats">
        {stageSummary.map((item) => (
          <Card key={item.stage}>
            <span><Target size={18} /></span>
            <small>{item.label}</small>
            <b>{item.count}</b>
          </Card>
        ))}
      </div>

      <form className="filter-card customer-filter-card" onSubmit={search}>
        <div className="filter-search">
          <Search size={17} />
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="搜索商机名称或备注"
          />
        </div>
        <label>
          <span>阶段</span>
          <select value={query.stage} onChange={(event) => setQuery({ ...query, stage: event.target.value })}>
            <option value="">全部阶段</option>
            {Object.entries(stageText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
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
                  <th>商机名称</th>
                  <th>客户ID</th>
                  <th>阶段</th>
                  <th>金额</th>
                  <th>赢率</th>
                  <th>预计成交</th>
                  <th>负责人</th>
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
                    <td>{row.customerId || '-'}</td>
                    <td><Badge dot tone={stageTone[row.stage] || 'neutral'}>{stageText[row.stage] || row.stage || '-'}</Badge></td>
                    <td>{formatAmount(row.amount)}</td>
                    <td>{row.probability == null ? '-' : `${row.probability}%`}</td>
                    <td>{row.expectedCloseDate || '-'}</td>
                    <td>{ownerName(row)}</td>
                    <td>
                      <div className="table-action-row" onClick={(event) => event.stopPropagation()}>
                        {canManage && <button className="icon-button" onClick={() => setEditing(toForm(row))}><Edit2 size={17} /></button>}
                        {canDelete && <button className="icon-button" onClick={() => deleteOpportunity(row)}><Trash2 size={17} /></button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!loading && !records.length && (
              <div className="empty-table">
                <BriefcaseBusiness size={26} />
                <b>暂无商机数据</b>
                <span>当前查询条件下没有真实商机记录</span>
              </div>
            )}
            {loading && (
              <div className="empty-table">
                <RefreshCw size={26} />
                <b>正在加载商机数据</b>
                <span>数据来自后台商机接口</span>
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

        <OpportunityDetailCard
          data={selected}
          canWrite={canManage}
          canDelete={canDelete}
          onEdit={() => setEditing(toForm(selected))}
          onDelete={() => deleteOpportunity(selected)}
          onClose={() => setSelected(null)}
        />
      </div>

      <OpportunityFormModal
        open={Boolean(editing)}
        form={editing || emptyForm}
        ownerOptions={ownerOptions}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={saveOpportunity}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function OpportunityDetailCard({ data, canWrite, canDelete, onEdit, onDelete, onClose }) {
  if (!data) {
    return (
      <Card className="customer-detail-panel empty">
        <span><BriefcaseBusiness size={24} /></span>
        <h2>请选择商机</h2>
        <p>先从左侧商机列表选择一条真实记录，再查看详情。</p>
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
        {canWrite && <Button variant="secondary" icon={Edit2} onClick={onEdit}>编辑商机</Button>}
        {canDelete && <Button variant="ghost" icon={Trash2} onClick={onDelete}>删除</Button>}
      </div>
      <div className="channel-detail-grid customer-detail-grid">
        <DetailItem icon={CircleDollarSign} label="金额" value={formatAmount(data.amount)} />
        <DetailItem icon={Target} label="阶段" value={stageText[data.stage] || data.stage} />
        <DetailItem label="赢率" value={data.probability == null ? '-' : `${data.probability}%`} />
        <DetailItem label="客户ID" value={data.customerId} />
        <DetailItem label="负责人" value={ownerName(data)} />
        <DetailItem icon={CalendarDays} label="预计成交" value={data.expectedCloseDate} />
        <DetailItem icon={CalendarDays} label="创建时间" value={formatDateTime(data.createdAt)} />
        <DetailItem icon={CalendarDays} label="更新时间" value={formatDateTime(data.updatedAt)} />
      </div>
      <div className="channel-text-block">
        <span>备注</span>
        <p>{data.remark || '暂无备注'}</p>
      </div>
      <div className="channel-text-block">
        <span>AI 分析</span>
        <p>暂无 AI 分析结果，后续可基于客户、线索、跟进记录和阶段变化生成赢率建议。</p>
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

function OpportunityFormModal({ open, form, ownerOptions, onChange, onClose, onSave }) {
  const update = (patch) => onChange({ ...form, ...patch })
  const hasSelectedOwner = ownerOptions.some((item) => String(item.id) === String(form.ownerId || ''))
  return (
    <Modal
      open={open}
      title={form.id ? '编辑商机' : '新建商机'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={() => onSave(form)}>保存</Button>
        </>
      )}
    >
      <div className="customer-form-grid">
        <Field label="商机名称" required>
          <input value={form.name || ''} onChange={(event) => update({ name: event.target.value })} />
        </Field>
        <Field label="阶段">
          <select value={form.stage || 'DISCOVERY'} onChange={(event) => update({ stage: event.target.value })}>
            {Object.entries(stageText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </Field>
        <Field label="客户ID">
          <input value={form.customerId || ''} onChange={(event) => update({ customerId: event.target.value })} />
        </Field>
        <Field label="负责人" hint="不选则由后台设置为当前登录用户">
          <select value={form.ownerId || ''} onChange={(event) => update({ ownerId: event.target.value })}>
            <option value="">默认当前登录用户</option>
            {form.ownerId && !hasSelectedOwner && <option value={form.ownerId}>当前负责人</option>}
            {ownerOptions.map((item) => <option value={item.id} key={item.id}>{ownerOptionLabel(item)}</option>)}
          </select>
        </Field>
        <Field label="商机金额">
          <input value={form.amount || ''} onChange={(event) => update({ amount: event.target.value })} placeholder="例如 120000" />
        </Field>
        <Field label="赢率">
          <input type="number" min="0" max="100" value={form.probability || ''} onChange={(event) => update({ probability: event.target.value })} />
        </Field>
        <Field label="预计成交日期">
          <input type="date" value={form.expectedCloseDate || ''} onChange={(event) => update({ expectedCloseDate: event.target.value })} />
        </Field>
        <Field label="备注">
          <textarea rows="4" value={form.remark || ''} onChange={(event) => update({ remark: event.target.value })} />
        </Field>
      </div>
    </Modal>
  )
}
