import { useEffect, useState } from 'react'
import { Edit2, Package, Plus, RefreshCw, Search, Trash2 } from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Field,
  Modal,
  PageHeader,
  Select,
  useConfirmDialog,
} from '../../components'

const productTypeText = {
  STANDARD: '标准产品',
  SOLUTION: '解决方案',
  SERVICE: '服务包',
}

const productTypeTone = {
  STANDARD: 'info',
  SOLUTION: 'warning',
  SERVICE: 'success',
}

const productCategoryText = {
  AI_AGENT_PLATFORM: '智能体平台',
  INTELLIGENT_MARKETING: '智能营销',
  DATA_KNOWLEDGE: '数据与知识库',
  INDUSTRY_SOLUTION: '行业解决方案',
  IMPLEMENTATION_SERVICE: '实施与技术服务',
  OTHER: '其他',
}

const productCategoryOptions = Object.entries(productCategoryText).map(([value, label]) => ({
  value,
  label,
}))

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: [],
}

const emptyForm = {
  name: '',
  category: 'OTHER',
  productType: 'STANDARD',
  price: '',
  unit: '',
  enabled: true,
  description: '',
  remark: '',
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
    category: query.category || undefined,
    productType: query.productType || undefined,
    enabled: query.enabled === '' ? undefined : query.enabled === 'true',
  }
}

function toForm(row) {
  return {
    id: row.id,
    name: row.name || '',
    category: row.category || 'OTHER',
    productType: row.productType || 'STANDARD',
    price: row.price == null ? '' : row.price,
    unit: row.unit || '',
    enabled: row.enabled !== false,
    description: row.description || '',
    remark: row.remark || '',
  }
}

function toPayload(form) {
  return {
    ...form,
    name: form.name.trim(),
    category: form.category || 'OTHER',
    productType: form.productType || 'STANDARD',
    price: form.price === '' ? null : form.price,
    unit: form.unit || null,
    enabled: form.enabled !== false,
    description: form.description || null,
    remark: form.remark || null,
  }
}

export function ProductPage({ can, notify }) {
  const canManage = can('crm:product:manage')
  const canCreate = canManage || can('crm:product:create')
  const canDelete = canManage
  const { confirm, dialogProps } = useConfirmDialog()
  const [query, setQuery] = useState({
    keyword: '',
    category: '',
    productType: '',
    enabled: '',
    pageNo: 1,
    pageSize: 20,
  })
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const data = await api.product.page(compactQuery(nextQuery))
      setPage(data || emptyPage)
      setQuery(nextQuery)
    } catch (error) {
      notify(error.message || '产品数据加载失败', 'info')
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

  const saveProduct = async (form) => {
    if (!form.name || !form.name.trim()) {
      notify('产品名称不能为空', 'info')
      return
    }
    try {
      await api.product.save(toPayload(form))
      notify('产品已保存', 'success')
      setEditing(null)
      load({ ...query, pageNo: form.id ? query.pageNo : 1 })
    } catch (error) {
      notify(error.message || '产品保存失败', 'info')
    }
  }

  const deleteProduct = async (row) => {
    const confirmed = await confirm({
      title: '删除产品',
      description: '删除后新商机不能再选择该产品，历史商机中的产品快照不受影响。',
      target: row.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.product.delete(row.id)
      notify('产品已删除', 'success')
      load({ ...query, pageNo: 1 })
    } catch (error) {
      notify(error.message || '产品删除失败', 'info')
    }
  }

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 20
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  return (
    <div className="page product-page compact-list-page">
      <PageHeader
        title="产品管理"
        description={`维护商机可选择的产品和服务，当前真实产品 ${page.total || 0} 个`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>
            {canCreate && <Button icon={Plus} onClick={() => setEditing(emptyForm)}>新建产品</Button>}
          </>
        )}
      />

      <form className="filter-card customer-filter-card list-filter-card" onSubmit={search}>
        <div className="filter-search">
          <Search size={17} />
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="搜索产品名称、编码或说明"
          />
        </div>
        <label>
          <span>分类</span>
          <Select
            value={query.category}
            options={[
              { value: '', label: '全部分类' },
              ...productCategoryOptions,
            ]}
            onChange={(value) => setQuery({ ...query, category: value })}
          />
        </label>
        <label>
          <span>类型</span>
          <select
            value={query.productType}
            onChange={(event) => setQuery({ ...query, productType: event.target.value })}
          >
            <option value="">全部类型</option>
            {Object.entries(productTypeText).map(([value, label]) => (
              <option value={value} key={value}>{label}</option>
            ))}
          </select>
        </label>
        <label>
          <span>状态</span>
          <select value={query.enabled} onChange={(event) => setQuery({ ...query, enabled: event.target.value })}>
            <option value="">全部状态</option>
            <option value="true">启用</option>
            <option value="false">停用</option>
          </select>
        </label>
        <Button type="submit" variant="secondary" icon={Search}>查询</Button>
      </form>

      <Card className="table-card">
        <div className="data-table-wrap">
          <table className="data-table product-list-table">
            <thead>
              <tr>
                <th>产品</th>
                <th>分类</th>
                <th>类型</th>
                <th>标准价格</th>
                <th>单位</th>
                <th>状态</th>
                <th>说明</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((row) => (
                <tr key={row.id}>
                  <td><strong>{row.name}</strong><small>{row.code}</small></td>
                  <td>{productCategoryText[row.category] || row.category || '-'}</td>
                  <td>
                    <Badge tone={productTypeTone[row.productType] || 'neutral'}>
                      {productTypeText[row.productType] || row.productType || '-'}
                    </Badge>
                  </td>
                  <td>{row.price == null ? '-' : formatAmount(row.price)}</td>
                  <td>{row.unit || '-'}</td>
                  <td><Badge dot tone={row.enabled ? 'success' : 'neutral'}>{row.enabled ? '启用' : '停用'}</Badge></td>
                  <td><span>{row.description || row.remark || '-'}</span></td>
                  <td>
                    <div className="table-action-row">
                      {canManage && (
                        <button className="icon-button" onClick={() => setEditing(toForm(row))}>
                          <Edit2 size={17} />
                        </button>
                      )}
                      {canDelete && (
                        <button className="icon-button" onClick={() => deleteProduct(row)}>
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
              <Package size={26} />
              <b>暂无产品数据</b>
              <span>先维护产品，后续商机可选择产品明细并自动汇总金额。</span>
            </div>
          )}
          {loading && (
            <div className="empty-table">
              <RefreshCw size={26} />
              <b>正在加载产品数据</b>
              <span>数据来自后台产品接口</span>
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

      <ProductFormModal
        open={Boolean(editing)}
        form={editing || emptyForm}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={saveProduct}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function ProductFormModal({ open, form, onChange, onClose, onSave }) {
  if (!open || !form) return null
  const update = (patch) => onChange({ ...form, ...patch })
  return (
    <Modal
      open={open}
      title={form.id ? '编辑产品' : '新建产品'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={() => onSave(form)}>保存</Button>
        </>
      )}
    >
      <div className="customer-form-grid">
        <Field label="产品名称" required>
          <input value={form.name || ''} onChange={(event) => update({ name: event.target.value })} />
        </Field>
        <Field label="产品类型">
          <select value={form.productType || 'STANDARD'} onChange={(event) => update({ productType: event.target.value })}>
            {Object.entries(productTypeText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </Field>
        <Field label="产品分类">
          <Select
            value={form.category || 'OTHER'}
            options={productCategoryOptions}
            onChange={(value) => update({ category: value })}
          />
        </Field>
        <Field label="标准价格">
          <input
            type="number"
            min="0"
            step="0.01"
            value={form.price || ''}
            onChange={(event) => update({ price: event.target.value })}
          />
        </Field>
        <Field label="计价单位">
          <input value={form.unit || ''} onChange={(event) => update({ unit: event.target.value })} placeholder="例如 套、年、人天" />
        </Field>
        <Field label="状态">
          <select
            value={form.enabled === false ? 'false' : 'true'}
            onChange={(event) => update({ enabled: event.target.value === 'true' })}
          >
            <option value="true">启用</option>
            <option value="false">停用</option>
          </select>
        </Field>
        <Field label="产品说明">
          <textarea rows="4" value={form.description || ''} onChange={(event) => update({ description: event.target.value })} />
        </Field>
        <Field label="备注">
          <textarea rows="3" value={form.remark || ''} onChange={(event) => update({ remark: event.target.value })} />
        </Field>
      </div>
    </Modal>
  )
}
