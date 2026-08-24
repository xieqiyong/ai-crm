import { useEffect, useMemo, useState } from 'react'
import {
  Archive,
  Building2,
  CalendarClock,
  RefreshCw,
  Search,
  Sparkles,
  UserPlus,
} from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  Drawer,
  MarkdownText,
  OwnerAssignModal,
  PageHeader,
} from '../../components'
import { useOwnerOptions } from '../../hooks/useOwnerOptions'
import { leadSourceText } from '../../models/crmSource'

const typeOptions = [
  { value: '', label: '全部渠道场景' },
  { value: 'WECOM', label: '企业微信' },
  { value: 'FORM', label: '获客表单' },
  { value: 'DOCUMENT', label: '文档导入' },
  { value: 'AUDIO', label: '录音导入' },
  { value: 'VIDEO', label: '视频导入' },
  { value: 'MANUAL', label: '手动录入' },
]

const typeText = {
  WECOM: '企业微信',
  FORM: '获客表单',
  DOCUMENT: '文档导入',
  AUDIO: '录音导入',
  VIDEO: '视频导入',
  MANUAL: '手动录入',
}

function formatDateTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function missingFields(row) {
  const fields = []
  if (!row?.companyName) fields.push('公司名称')
  if (!row?.contactName) fields.push('联系人')
  if (!row?.phone) fields.push('联系电话')
  return fields
}

function parseSourceFields(value) {
  if (!value) return []
  try {
    const source = JSON.parse(value)
    if (!source || Array.isArray(source) || typeof source !== 'object') return []
    return Object.entries(source)
      .filter(([, fieldValue]) => (
        fieldValue !== undefined && fieldValue !== null && String(fieldValue).trim()
      ))
      .map(([label, fieldValue]) => ({ label, value: String(fieldValue) }))
  } catch {
    return []
  }
}

export function PublicPoolPage({ can, notify }) {
  const canAssign = can('crm:public-pool:assign')
  const canAnalyze = can('crm:channel:analyze') || can('crm:channel:manage')
  const ownerOptions = useOwnerOptions(notify)
  const [query, setQuery] = useState({
    pageNo: 1,
    pageSize: 10,
    keyword: '',
    channelType: '',
  })
  const [page, setPage] = useState({ total: 0, pageNo: 1, pageSize: 10, records: [] })
  const [loading, setLoading] = useState(true)
  const [assigning, setAssigning] = useState(null)
  const [assignSubmitting, setAssignSubmitting] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])
  const [selected, setSelected] = useState(null)
  const [analyzingId, setAnalyzingId] = useState(null)

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const payload = {
        ...nextQuery,
        keyword: nextQuery.keyword || undefined,
        channelType: nextQuery.channelType || undefined,
      }
      const data = await api.channel.publicPoolPage(payload)
      setPage(data || { total: 0, pageNo: 1, pageSize: 10, records: [] })
      setQuery(nextQuery)
      setSelectedIds([])
    } catch (error) {
      notify(error.message || '加载公海数据失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 10
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))
  const assignableCount = useMemo(
    () => records.filter((row) => row.promotionReady).length,
    [records],
  )
  const assignableIds = useMemo(
    () => records.filter((row) => row.promotionReady).map((row) => String(row.id)),
    [records],
  )
  const selectedIdSet = useMemo(() => new Set(selectedIds), [selectedIds])
  const allAssignableSelected = assignableIds.length > 0
    && assignableIds.every((id) => selectedIdSet.has(id))

  const search = (event) => {
    event.preventDefault()
    load({ ...query, pageNo: 1 })
  }

  const assign = async (ownerId) => {
    if (!assigning || !ownerId) return
    const ids = assigning.batch
      ? assigning.ids
      : [String(assigning.id)]
    if (!ids.length) return
    setAssignSubmitting(true)
    try {
      if (assigning.batch) {
        await api.channel.batchAssignPublicPool({ ids, ownerId })
      } else {
        await api.channel.assignPublicPool({ id: assigning.id, ownerId })
      }
      notify(
        assigning.batch
          ? `已分配 ${ids.length} 条公海数据，并生成销售线索`
          : '公海数据已分配，并生成销售线索',
        'success',
      )
      setAssigning(null)
      const remainingTotal = Math.max(0, Number(page.total || 0) - ids.length)
      const remainingPages = Math.max(1, Math.ceil(remainingTotal / pageSize))
      await load({ ...query, pageNo: Math.min(currentPage, remainingPages) })
    } catch (error) {
      notify(error.message || '公海数据分配失败', 'info')
    } finally {
      setAssignSubmitting(false)
    }
  }

  const analyze = async (row) => {
    setAnalyzingId(row.id)
    try {
      await api.channel.analyze(row.id)
      notify('AI整理完成，可以继续分配', 'success')
      await load(query)
    } catch (error) {
      notify(error.message || 'AI整理失败', 'info')
    } finally {
      setAnalyzingId(null)
    }
  }

  const openAssign = (row) => {
    if (!row.promotionReady) {
      notify(row.promotionBlockReason || '请先完成渠道数据整理', 'info')
      return
    }
    setAssigning(row)
  }

  const toggleRow = (rowId) => {
    const id = String(rowId)
    setSelectedIds((current) => (
      current.includes(id)
        ? current.filter((item) => item !== id)
        : [...current, id]
    ))
  }

  const togglePage = () => {
    setSelectedIds(allAssignableSelected ? [] : assignableIds)
  }

  const openBatchAssign = () => {
    if (!selectedIds.length) {
      notify('请先选择需要分配的公海数据', 'info')
      return
    }
    setAssigning({ batch: true, ids: [...selectedIds] })
  }

  return (
    <div className="page public-pool-page">
      <PageHeader
        eyebrow="销售公海"
        title="公海池"
        description="集中承接各渠道进入的真实获客数据，由负责人统一去重、判断并分配给销售"
        actions={<Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>}
      />

      <div className="public-pool-overview">
        <Card>
          <span><Archive size={18} />待分配数据</span>
          <b>{page.total || 0}</b>
          <small>未生成线索的渠道数据</small>
        </Card>
        <Card>
          <span><UserPlus size={18} />本页可分配</span>
          <b>{assignableCount}</b>
          <small>可直接分配给销售并生成线索</small>
        </Card>
      </div>

      <form className="filter-card public-pool-filter" onSubmit={search}>
        <div className="filter-search">
          <Search size={17} />
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="搜索公司、联系人、手机号或邮箱"
          />
        </div>
        <label>
          <span>渠道场景</span>
          <select
            value={query.channelType}
            onChange={(event) => setQuery({ ...query, channelType: event.target.value })}
          >
            {typeOptions.map((item) => (
              <option value={item.value} key={item.value}>{item.label}</option>
            ))}
          </select>
        </label>
        <Button type="submit" variant="secondary" icon={Search}>查询</Button>
      </form>

      <Card className="table-card public-pool-table-card">
        {canAssign && (
          <div className="public-pool-batch-bar">
            <div>
              <b>{selectedIds.length ? `已选择 ${selectedIds.length} 条` : '支持批量分配'}</b>
              <span>勾选本页待分配数据后，可统一分配给同一位销售</span>
            </div>
            <Button
              icon={UserPlus}
              disabled={!selectedIds.length || assignSubmitting}
              onClick={openBatchAssign}
            >批量分配</Button>
          </div>
        )}
        <div className="data-table-wrap">
          <table className={`data-table public-pool-table ${canAssign ? 'with-selection' : ''}`}>
            <thead>
              <tr>
                {canAssign && (
                  <th className="public-pool-select-cell">
                    <input
                      type="checkbox"
                      aria-label="选择本页全部可分配数据"
                      checked={allAssignableSelected}
                      disabled={!assignableIds.length}
                      onChange={togglePage}
                    />
                  </th>
                )}
                <th>公司名称</th>
                <th>联系人</th>
                <th>联系方式</th>
                <th>渠道场景</th>
                <th>来源</th>
                <th>进入时间</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((row) => (
                <tr key={row.id}>
                  {canAssign && (
                    <td className="public-pool-select-cell">
                      <input
                        type="checkbox"
                        aria-label={`选择${row.companyName || row.contactName || '公海数据'}`}
                        checked={selectedIdSet.has(String(row.id))}
                        disabled={!row.promotionReady}
                        onChange={() => toggleRow(row.id)}
                      />
                    </td>
                  )}
                  <td>
                    <strong>{row.companyName || '-'}</strong>
                    <small className="public-pool-product">咨询产品：{row.productName || '未关联'}</small>
                  </td>
                  <td>{row.contactName || row.title || '-'}</td>
                  <td><span>{row.phone || '-'}</span><small>{row.email || '-'}</small></td>
                  <td><Badge tone="info">{typeText[row.channelType] || row.channelType || '-'}</Badge></td>
                  <td>{leadSourceText[row.source] || row.source || '-'}</td>
                  <td>{formatDateTime(row.createdAt)}</td>
                  <td>
                    <Badge tone={row.promotionReady ? 'success' : 'warning'}>
                      {row.promotionReady ? '待分配' : '待整理'}
                    </Badge>
                  </td>
                  <td>
                    <div className="table-action-row text-actions">
                      <button type="button" className="table-text-button" onClick={() => setSelected(row)}>查看</button>
                      {!row.promotionReady && canAnalyze && (
                        <button
                          type="button"
                          className="table-text-button"
                          disabled={String(analyzingId || '') === String(row.id)}
                          onClick={() => analyze(row)}
                        >
                          {String(analyzingId || '') === String(row.id) ? '整理中' : 'AI整理'}
                        </button>
                      )}
                      {canAssign && (
                        <button type="button" className="table-text-button primary" onClick={() => openAssign(row)}>
                          分配
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
              <Archive size={27} />
              <b>公海池暂时没有数据</b>
              <span>渠道同步或获客表单提交后，数据会自动进入这里</span>
            </div>
          )}
          {loading && !records.length && (
            <div className="empty-table">
              <RefreshCw size={27} />
              <b>正在加载公海数据</b>
            </div>
          )}
        </div>
        <div className="table-footer">
          <span>共 {page.total || 0} 条，第 {currentPage} / {totalPages} 页</span>
          <div className="pagination">
            <button
              type="button"
              disabled={loading || currentPage <= 1}
              onClick={() => load({ ...query, pageNo: currentPage - 1 })}
            >‹</button>
            <button type="button" className="active">{currentPage}</button>
            <button
              type="button"
              disabled={loading || currentPage >= totalPages}
              onClick={() => load({ ...query, pageNo: currentPage + 1 })}
            >›</button>
          </div>
        </div>
      </Card>

      <OwnerAssignModal
        open={Boolean(assigning)}
        title={assigning?.batch ? '批量分配公海数据' : '分配公海数据'}
        recordName={assigning?.batch
          ? `已选择 ${assigning.ids?.length || 0} 条公海数据`
          : assigning?.companyName || assigning?.contactName || assigning?.title}
        currentOwnerName="公海池"
        ownerOptions={ownerOptions}
        submitting={assignSubmitting}
        onClose={() => setAssigning(null)}
        onConfirm={assign}
      />

      <PublicPoolDetailDrawer row={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

function PublicPoolDetailDrawer({ row, onClose }) {
  if (!row) return null
  const missing = missingFields(row)
  const sourceFields = parseSourceFields(row.sourceSnapshot)
  return (
    <Drawer open title="公海数据详情" onClose={onClose} size="lg">
      <div className="public-pool-detail-head">
        <span><Building2 size={20} /></span>
        <div>
          <h3>{row.companyName || row.contactName || row.title || '未命名数据'}</h3>
          <p>{typeText[row.channelType] || row.channelType || '未知渠道'} · {formatDateTime(row.createdAt)}</p>
        </div>
      </div>
      <div className="public-pool-detail-grid">
        <DetailItem label="公司名称" value={row.companyName} />
        <DetailItem label="联系人" value={row.contactName} />
        <DetailItem label="联系电话" value={row.phone} />
        <DetailItem label="联系邮箱" value={row.email} />
        <DetailItem label="咨询产品" value={row.productName} />
        <DetailItem label="渠道来源" value={leadSourceText[row.source] || row.source} />
        <DetailItem label="进入时间" value={formatDateTime(row.createdAt)} />
      </div>
      {missing.length > 0 && (
        <div className="public-pool-warning">
          <CalendarClock size={18} />
          <span>以下信息可由销售后续补充：{missing.join('、')}，不影响当前分配</span>
        </div>
      )}
      {row.remark && <TextBlock title="渠道备注" value={row.remark} markdown />}
      {row.aiSummary && <TextBlock title="AI整理摘要" value={row.aiSummary} icon={Sparkles} markdown />}
      {row.usefulInfo && <TextBlock title="渠道原始信息" value={row.usefulInfo} />}
      {sourceFields.length > 0 && <SourceFields fields={sourceFields} />}
      {row.sourceSnapshot && sourceFields.length === 0 && (
        <TextBlock title="来源数据快照" value={row.sourceSnapshot} mono />
      )}
    </Drawer>
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

function SourceFields({ fields }) {
  return (
    <section className="public-pool-text-block">
      <h4>表格原始填写信息</h4>
      <div className="public-pool-source-fields">
        {fields.map((field) => (
          <div key={field.label}>
            <span>{field.label}</span>
            <b>{field.value}</b>
          </div>
        ))}
      </div>
    </section>
  )
}

function TextBlock({ title, value, icon: Icon, mono = false, markdown = false }) {
  return (
    <section className="public-pool-text-block">
      <h4>{Icon && <Icon size={16} />}{title}</h4>
      {markdown
        ? <div className="public-pool-markdown"><MarkdownText value={value} /></div>
        : <pre className={mono ? 'mono' : ''}>{value}</pre>}
    </section>
  )
}
