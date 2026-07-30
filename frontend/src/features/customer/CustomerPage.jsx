import { useEffect, useRef, useState } from 'react'
import {
  Building2,
  Plus,
  RefreshCw,
  Search,
} from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Field,
  Modal,
  OwnerAssignModal,
  PageHeader,
  Select,
  useConfirmDialog,
} from '../../components'
import {
  customerLevelText,
  customerLevelTone,
  customerStatusOptions,
  customerStatusText,
  customerStatusTone,
  recommendedCustomerStatus,
} from '../../models/crmStatus'
import { ownerName, ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'
import { useCustomerIndustryOptions } from '../../hooks/useCustomerIndustryOptions'
import { validateCustomerForm } from '../../models/customerForm'

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 10,
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
    pageSize: query.pageSize || 10,
    keyword: query.keyword || undefined,
    status: query.status || undefined,
  }
}

function customerListStateKey(currentUser) {
  const tenantId = currentUser?.tenantId || 'default'
  const userId = currentUser?.userId || currentUser?.username || 'anonymous'
  return `crm.customer.list.query.${tenantId}.${userId}`
}

function customerLastViewedKey(currentUser) {
  const tenantId = currentUser?.tenantId || 'default'
  const userId = currentUser?.userId || currentUser?.username || 'anonymous'
  return `crm.customer.list.last-viewed.${tenantId}.${userId}`
}

function isPageReload() {
  const navigationEntry = window.performance?.getEntriesByType?.('navigation')?.[0]
  if (navigationEntry) {
    return navigationEntry.type === 'reload'
  }
  return window.performance?.navigation?.type === 1
}

const resetCustomerPageAfterReload = isPageReload()
  && window.location.hash.replace('#/', '').split('?')[0] === 'customers'
let customerPageReloadHandled = false

function readCustomerListQuery(currentUser) {
  const defaultQuery = { keyword: '', status: '', pageNo: 1, pageSize: 10 }
  const resetPageNo = resetCustomerPageAfterReload && !customerPageReloadHandled
  customerPageReloadHandled = true
  try {
    const stored = window.sessionStorage.getItem(customerListStateKey(currentUser))
    if (!stored) return defaultQuery
    const parsed = JSON.parse(stored)
    return {
      keyword: typeof parsed.keyword === 'string' ? parsed.keyword : '',
      status: typeof parsed.status === 'string' ? parsed.status : '',
      pageNo: resetPageNo ? 1 : Math.max(1, Number.parseInt(parsed.pageNo, 10) || 1),
      pageSize: 10,
    }
  } catch {
    return defaultQuery
  }
}

function saveCustomerListQuery(currentUser, query) {
  try {
    window.sessionStorage.setItem(customerListStateKey(currentUser), JSON.stringify({
      keyword: query.keyword || '',
      status: query.status || '',
      pageNo: query.pageNo || 1,
      pageSize: 10,
    }))
  } catch {
    // 浏览器禁用会话存储时不影响客户查询
  }
}

function readLastViewedCustomerId(currentUser) {
  try {
    return window.sessionStorage.getItem(customerLastViewedKey(currentUser)) || ''
  } catch {
    return ''
  }
}

function saveLastViewedCustomerId(currentUser, customerId) {
  try {
    window.sessionStorage.setItem(customerLastViewedKey(currentUser), String(customerId))
  } catch {
    // 浏览器禁用会话存储时不影响客户详情访问
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

export function CustomerPage({ can, notify, navigate, currentUser }) {
  const canWrite = can('crm:customer:manage') || can('crm:customer:edit')
  const canAssign = can('crm:customer:assign')
  const canDelete = can('crm:customer:manage')
  const ownerOptions = useOwnerOptions(notify)
  const industryOptions = useCustomerIndustryOptions(notify, canWrite)
  const { confirm, dialogProps } = useConfirmDialog()
  const lastViewedRowRef = useRef(null)
  const [query, setQuery] = useState(() => readCustomerListQuery(currentUser))
  const [page, setPage] = useState(emptyPage)
  const [jumpPage, setJumpPage] = useState('')
  const [lastViewedCustomerId, setLastViewedCustomerId] = useState(() => readLastViewedCustomerId(currentUser))
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)
  const [assigning, setAssigning] = useState(null)
  const [assignSubmitting, setAssignSubmitting] = useState(false)

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const data = await api.customer.page(compactQuery(nextQuery))
      setPage(data || emptyPage)
      const resolvedQuery = {
        ...nextQuery,
        pageNo: data?.pageNo || nextQuery.pageNo || 1,
        pageSize: 10,
      }
      setQuery(resolvedQuery)
      saveCustomerListQuery(currentUser, resolvedQuery)
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

  const saveCustomer = async (form) => {
    const validationMessage = validateCustomerForm(form, industryOptions)
    if (validationMessage) {
      notify(validationMessage, 'info')
      return
    }
    try {
      await api.customer.save(toPayload(form))
      notify('客户资料已保存', 'success')
      setEditing(null)
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
      load({ ...query, pageNo: 1 })
    } catch (err) {
      notify(err.message || '客户删除失败', 'info')
    }
  }

  const assignCustomer = async (ownerId) => {
    if (!assigning?.id || !ownerId) {
      notify('请选择负责人', 'info')
      return
    }
    setAssignSubmitting(true)
    try {
      await api.customer.assign({ id: assigning.id, ownerId })
      notify('客户已分配', 'success')
      setAssigning(null)
      load(query)
    } catch (err) {
      notify(err.message || '客户分配失败', 'info')
    } finally {
      setAssignSubmitting(false)
    }
  }

  const openFullDetail = (row) => {
    if (!row?.id) return
    setLastViewedCustomerId(String(row.id))
    saveLastViewedCustomerId(currentUser, row.id)
    navigate(`customers/detail/${encodeURIComponent(row.id)}`)
  }

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 10
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  useEffect(() => {
    setJumpPage(String(currentPage))
  }, [currentPage])

  useEffect(() => {
    if (loading || !lastViewedCustomerId || !lastViewedRowRef.current) return undefined
    const timer = window.setTimeout(() => {
      lastViewedRowRef.current?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
        inline: 'nearest',
      })
    }, 80)
    return () => window.clearTimeout(timer)
  }, [loading, lastViewedCustomerId, currentPage])

  const goToPage = (targetPage) => {
    if (loading || targetPage === currentPage) return
    load({ ...query, pageNo: targetPage })
  }

  const jumpToPage = (event) => {
    event.preventDefault()
    const targetPage = Number.parseInt(jumpPage, 10)
    if (!Number.isInteger(targetPage) || targetPage < 1 || targetPage > totalPages) {
      notify(`请输入 1 到 ${totalPages} 之间的页码`, 'info')
      return
    }
    goToPage(targetPage)
  }

  return (
    <div className="page customer-list-page compact-list-page">
      <PageHeader
        title="客户管理"
        description={`从客户列表进入详情，当前真实记录 ${page.total || 0} 条`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>
            {canWrite && (
              <Button
                icon={Plus}
                onClick={() => setEditing({ ...emptyForm, ownerId: currentUser?.userId || '' })}
              >
                新建客户
              </Button>
            )}
          </>
        )}
      />

      <form className="filter-card customer-filter-card list-filter-card" onSubmit={search}>
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
        <Card className="table-card customer-table-card customer-list-table-card">
          <div className="data-table-wrap customer-list-table-wrap">
            <table className="data-table customer-list-table customer-stable-table">
              <colgroup>
                <col className="customer-column-name" />
                <col className="customer-column-industry" />
                <col className="customer-column-contact-name" />
                <col className="customer-column-contact" />
                <col className="customer-column-level" />
                <col className="customer-column-status" />
                <col className="customer-column-owner" />
                <col className="customer-column-created" />
                <col className="customer-column-actions" />
              </colgroup>
              <thead>
                <tr>
                  <th>客户名称</th>
                  <th>行业</th>
                  <th>主要联系人</th>
                  <th>联系方式</th>
                  <th>客户级别</th>
                  <th>状态</th>
                  <th>负责人</th>
                  <th>创建时间</th>
                  <th className="customer-list-actions-column">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((row) => {
                  const isLastViewed = String(row.id) === String(lastViewedCustomerId)
                  return (
                    <tr
                      key={row.id}
                      ref={isLastViewed ? lastViewedRowRef : null}
                      className={isLastViewed ? 'customer-row-last-viewed' : undefined}
                      onClick={() => openFullDetail(row)}
                    >
                      <td><strong>{row.name || '-'}</strong></td>
                      <td>{row.industry || '-'}</td>
                      <td>{row.contactName || '-'}</td>
                      <td>
                        <span>{row.contactPhone || '-'}</span>
                        <small>{row.contactEmail || '-'}</small>
                      </td>
                      <td><Badge tone={customerLevelTone[row.level] || 'neutral'}>{customerLevelText[row.level] || row.level || '-'}</Badge></td>
                      <td><Badge dot tone={customerStatusTone[row.status] || 'neutral'}>{customerStatusText[row.status] || row.status || '-'}</Badge></td>
                      <td>{ownerName(row)}</td>
                      <td>{formatDateTime(row.createdAt)}</td>
                      <td className="customer-list-actions-column">
                        <div className="table-action-row text-actions" onClick={(event) => event.stopPropagation()}>
                          <button
                            type="button"
                            className="table-text-button"
                            onClick={() => openFullDetail(row)}
                          >
                            详情
                          </button>
                          {canWrite && (
                            <button
                              type="button"
                              className="table-text-button"
                              onClick={() => setEditing(toForm(row))}
                            >
                              编辑
                            </button>
                          )}
                          {canAssign && (
                            <button
                              type="button"
                              className="table-text-button"
                              onClick={() => setAssigning(row)}
                            >
                              分配
                            </button>
                          )}
                          {canDelete && (
                            <button
                              type="button"
                              className="table-text-button danger"
                              onClick={() => deleteCustomer(row)}
                            >
                              删除
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {!loading && !records.length && (
              <div className="empty-table">
                <Building2 size={26} />
                <b>暂无客户数据</b>
                <span>当前查询条件下没有真实客户记录</span>
              </div>
            )}
            {loading && !records.length && (
              <div className="empty-table">
                <RefreshCw size={26} />
                <b>正在加载客户数据</b>
                <span>数据来自后台客户接口</span>
              </div>
            )}
          </div>
          <div className="table-footer">
            <span>共 {page.total || 0} 条，当前第 {currentPage} / {totalPages} 页</span>
            <div className="pagination customer-list-pagination">
              <button type="button" disabled={loading || currentPage <= 1} onClick={() => goToPage(currentPage - 1)}>‹</button>
              <button type="button" className="active">{currentPage}</button>
              <button type="button" disabled={loading || currentPage >= totalPages} onClick={() => goToPage(currentPage + 1)}>›</button>
              <form className="customer-page-jump" onSubmit={jumpToPage}>
                <span>跳至</span>
                <input
                  type="number"
                  min="1"
                  max={totalPages}
                  step="1"
                  inputMode="numeric"
                  aria-label="跳转页码"
                  value={jumpPage}
                  onChange={(event) => setJumpPage(event.target.value)}
                />
                <span>页</span>
                <button type="submit" className="customer-page-jump-button" disabled={loading}>跳转</button>
              </form>
            </div>
          </div>
        </Card>

      </div>

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
        open={Boolean(assigning)}
        title="分配客户负责人"
        recordName={assigning?.name}
        currentOwnerId={assigning?.ownerId}
        currentOwnerName={assigning?.ownerId ? ownerName(assigning) : ''}
        ownerOptions={ownerOptions}
        submitting={assignSubmitting}
        onClose={() => setAssigning(null)}
        onConfirm={assignCustomer}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
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
