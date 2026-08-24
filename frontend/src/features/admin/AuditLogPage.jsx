import { useEffect, useState } from 'react'
import { FileClock, RefreshCw, Search, ShieldCheck } from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  Drawer,
  PageHeader,
  Select,
} from '../../components'

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: [],
}

const moduleText = {
  ORGANIZATION: '组织与权限',
  MODEL: '大模型配置',
  AGENT: '智能体配置',
  KNOWLEDGE: '知识库',
  LEAD: '线索管理',
  CUSTOMER: '客户管理',
  CHANNEL: '渠道管理',
  PUBLIC_POOL: '公海池',
  PRODUCT: '产品管理',
  OPPORTUNITY: '商机管理',
  FOLLOWUP: '跟进记录',
  SECURITY: '登录与安全',
  NOTIFICATION: '系统通知',
  MAIL: '客户邮件',
}

const moduleOptions = [
  { value: '', label: '全部模块' },
  ...Object.entries(moduleText).map(([value, label]) => ({ value, label })),
]

function parseDetail(value) {
  if (!value) return {}
  try {
    return JSON.parse(value)
  } catch {
    return {}
  }
}

function parseAction(value) {
  const [module, ...actionParts] = String(value || '').split(':')
  return {
    module,
    action: actionParts.join(':'),
  }
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatParameters(value) {
  if (!value || (typeof value === 'object' && !Object.keys(value).length)) {
    return '本次操作未记录请求参数'
  }
  return JSON.stringify(value, null, 2)
}

function resultMeta(detail) {
  if (detail.success === true) {
    return { tone: 'success', text: '成功' }
  }
  if (detail.success === false) {
    return { tone: 'danger', text: '失败' }
  }
  return { tone: 'neutral', text: '未记录' }
}

export function AuditLogPage({ notify }) {
  const [query, setQuery] = useState({ module: '', pageNo: 1, pageSize: 20 })
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const data = await api.observability.pageAuditLog({
        ...nextQuery,
        module: nextQuery.module || undefined,
      })
      setPage(data || emptyPage)
      setQuery(nextQuery)
    } catch (err) {
      notify(err.message || '审计日志加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 20
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  return (
    <div className="page audit-log-page">
      <PageHeader
        title="审计日志"
        description={`拥有审计查看权限的管理员可查看当前租户全部操作，当前共 ${page.total || 0} 条`}
        actions={<Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>}
      />

      <div className="filter-card audit-log-filter">
        <div className="audit-log-filter-field">
          <span>操作模块</span>
          <Select
            value={query.module}
            options={moduleOptions}
            onChange={(module) => setQuery({ ...query, module, pageNo: 1 })}
          />
        </div>
        <Button
          variant="secondary"
          icon={Search}
          onClick={() => load({ ...query, pageNo: 1 })}
        >
          查询
        </Button>
      </div>

      <Card className="table-card">
        <div className="data-table-wrap">
          <table className="data-table audit-log-table">
            <thead>
              <tr>
                <th>操作时间</th>
                <th>操作人</th>
                <th>模块</th>
                <th>操作</th>
                <th>操作对象</th>
                <th>结果</th>
                <th>耗时</th>
                <th>详情</th>
              </tr>
            </thead>
            <tbody>
              {records.map((row) => {
                const detail = parseDetail(row.detailJson)
                const action = parseAction(row.action)
                const result = resultMeta(detail)
                return (
                  <tr key={row.id}>
                    <td>{formatDateTime(row.createdAt)}</td>
                    <td>
                      <strong>{detail.operatorName || '未识别用户'}</strong>
                      <small>{row.operatorId ? `用户编号：${row.operatorId}` : '-'}</small>
                    </td>
                    <td>{moduleText[action.module] || action.module || '-'}</td>
                    <td>{detail.description || action.action || '-'}</td>
                    <td>
                      <span>{detail.targetName || row.targetType || '-'}</span>
                      <small>{row.targetId ? `编号：${row.targetId}` : row.targetType || '-'}</small>
                    </td>
                    <td>
                      <Badge tone={result.tone}>{result.text}</Badge>
                    </td>
                    <td>{Number.isFinite(Number(detail.costMillis)) ? `${detail.costMillis} ms` : '-'}</td>
                    <td>
                      <button
                        type="button"
                        className="table-text-button"
                        onClick={() => setSelected({ ...row, detail })}
                      >
                        查看
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {!loading && !records.length && (
            <div className="empty-table">
              <FileClock size={27} />
              <b>暂无审计日志</b>
              <span>关键写操作发生后会在这里留下真实记录</span>
            </div>
          )}
          {loading && (
            <div className="empty-table">
              <RefreshCw size={27} />
              <b>正在加载审计日志</b>
              <span>数据来自后台审计日志表</span>
            </div>
          )}
        </div>
        <div className="table-footer">
          <span>共 {page.total || 0} 条，当前第 {currentPage} / {totalPages} 页</span>
          <div className="pagination">
            <button
              type="button"
              disabled={currentPage <= 1}
              onClick={() => load({ ...query, pageNo: currentPage - 1 })}
            >
              ‹
            </button>
            <button type="button" className="active">{currentPage}</button>
            <button
              type="button"
              disabled={currentPage >= totalPages}
              onClick={() => load({ ...query, pageNo: currentPage + 1 })}
            >
              ›
            </button>
          </div>
        </div>
      </Card>

      <AuditDetailDrawer record={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

function AuditDetailDrawer({ record, onClose }) {
  if (!record) return null
  const detail = record.detail || {}
  const action = parseAction(record.action)
  const result = resultMeta(detail)
  return (
    <Drawer open title="审计详情" onClose={onClose}>
      <div className="audit-detail">
        <div className="audit-detail-hero">
          <span><ShieldCheck size={22} /></span>
          <div>
            <small>{moduleText[action.module] || action.module || '系统操作'}</small>
            <h2>{detail.description || action.action || '-'}</h2>
          </div>
          <Badge tone={result.tone}>操作{result.text}</Badge>
        </div>
        <dl className="audit-detail-grid">
          <div><dt>操作人</dt><dd>{detail.operatorName || '-'}</dd></div>
          <div><dt>操作时间</dt><dd>{formatDateTime(record.createdAt)}</dd></div>
          <div><dt>操作对象</dt><dd>{detail.targetName || record.targetType || '-'}</dd></div>
          <div><dt>对象编号</dt><dd>{record.targetId || '-'}</dd></div>
          <div><dt>执行耗时</dt><dd>{detail.costMillis == null ? '-' : `${detail.costMillis} ms`}</dd></div>
          <div><dt>链路编号</dt><dd>{detail.traceId || '-'}</dd></div>
        </dl>
        {detail.errorMessage && (
          <div className="audit-error">
            <b>失败原因</b>
            <p>{detail.errorMessage}</p>
          </div>
        )}
        <div className="audit-parameters">
          <b>请求参数</b>
          <pre>{formatParameters(detail.parameters)}</pre>
        </div>
      </div>
    </Drawer>
  )
}
