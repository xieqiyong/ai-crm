import { useEffect, useState } from 'react'
import { AlertTriangle, ArrowLeft, Clock3, RefreshCw, Search } from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, EmptyPermission, PageHeader } from '../../components'
import { readRouteQueryParam } from '../../router/routeQuery'

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 10,
  records: [],
}

function normalizeHealth(value) {
  return value === 'CRITICAL' ? 'CRITICAL' : 'WARNING'
}

function healthName(value) {
  return value === 'CRITICAL' ? '红灯急跟进' : '黄灯待跟进'
}

function healthTone(value) {
  return value === 'CRITICAL' ? 'danger' : 'warning'
}

function targetTypeName(value) {
  return value === 'CUSTOMER' ? '客户' : '线索'
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', { hour12: false })
}

function displayName(row) {
  if (row.targetType === 'LEAD') {
    return row.companyName || row.targetName || row.contactName || '-'
  }
  return row.targetName || row.companyName || '-'
}

export function FollowupAttentionPage({ can, notify, navigate, routeKey }) {
  const canView = can('crm:dashboard:view') || can('menu.dashboard')
  const routeHealth = normalizeHealth(readRouteQueryParam(routeKey, 'health'))
  const [query, setQuery] = useState({
    keyword: '',
    targetType: '',
    followupHealth: routeHealth,
    pageNo: 1,
    pageSize: 10,
  })
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(false)

  const load = async (nextQuery) => {
    if (!canView) return
    const payload = nextQuery || query
    setLoading(true)
    try {
      const data = await api.dashboard.followupAttentionPage({
        pageNo: payload.pageNo || 1,
        pageSize: 10,
        keyword: payload.keyword || undefined,
        targetType: payload.targetType || undefined,
        followupHealth: payload.followupHealth,
      })
      setPage(data || emptyPage)
      setQuery({
        ...payload,
        pageNo: data?.pageNo || payload.pageNo || 1,
        pageSize: 10,
      })
    } catch (error) {
      notify(error.message || '待跟进列表加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const nextQuery = {
      keyword: '',
      targetType: '',
      followupHealth: routeHealth,
      pageNo: 1,
      pageSize: 10,
    }
    setQuery(nextQuery)
    load(nextQuery)
  }, [routeHealth])

  const switchHealth = (value) => {
    if (value === routeHealth) return
    navigate(`followup-attention?health=${value}`)
  }

  const search = (event) => {
    event.preventDefault()
    load({ ...query, pageNo: 1 })
  }

  const goPage = (pageNo) => {
    if (loading || pageNo < 1) return
    load({ ...query, pageNo })
  }

  const goFollowup = (row) => {
    if (row.targetType === 'CUSTOMER') {
      navigate(`customers/detail/${encodeURIComponent(row.targetId)}`)
      return
    }
    navigate(`leads/detail/${encodeURIComponent(row.targetId)}`)
  }

  if (!canView) {
    return <EmptyPermission onBack={() => navigate('dashboard')} />
  }

  const currentPage = page.pageNo || 1
  const totalPages = Math.max(1, Math.ceil(Number(page.total || 0) / 10))
  const records = page.records || []

  return (
    <div className="page followup-attention-page compact-list-page">
      <PageHeader
        eyebrow="销售跟进"
        title={healthName(routeHealth)}
        description={`共 ${Number(page.total || 0).toLocaleString('zh-CN')} 个需要处理的线索或客户`}
        actions={(
          <>
            <Button variant="secondary" icon={ArrowLeft} onClick={() => navigate('dashboard')}>返回工作台</Button>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>{loading ? '刷新中' : '刷新'}</Button>
          </>
        )}
      />

      <div className="followup-attention-tabs">
        <button type="button" className={routeHealth === 'WARNING' ? 'active warning' : ''} onClick={() => switchHealth('WARNING')}>
          <Clock3 size={16} />黄灯待跟进
        </button>
        <button type="button" className={routeHealth === 'CRITICAL' ? 'active critical' : ''} onClick={() => switchHealth('CRITICAL')}>
          <AlertTriangle size={16} />红灯急跟进
        </button>
      </div>

      <form className="filter-card followup-attention-filter" onSubmit={search}>
        <div className="filter-search">
          <Search size={17} />
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="搜索公司、联系人、手机号、产品或负责人"
          />
        </div>
        <label>
          <span>对象类型</span>
          <select value={query.targetType} onChange={(event) => setQuery({ ...query, targetType: event.target.value })}>
            <option value="">全部对象</option>
            <option value="LEAD">线索</option>
            <option value="CUSTOMER">客户</option>
          </select>
        </label>
        <Button type="submit" variant="secondary" icon={Search}>查询</Button>
      </form>

      <Card className="table-card followup-attention-table-card">
        <div className="data-table-wrap">
          <table className="data-table followup-attention-table">
            <thead>
              <tr>
                <th>跟进对象</th>
                <th>类型</th>
                <th>意向产品</th>
                <th>负责人</th>
                <th>上次跟进</th>
                <th>下次计划</th>
                <th>提醒原因</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((row) => (
                <tr key={`${row.targetType}-${row.targetId}`}>
                  <td>
                    <button type="button" className="followup-attention-name" onClick={() => goFollowup(row)}>{displayName(row)}</button>
                    <small>{[row.contactName, row.contactPhone].filter(Boolean).join(' · ') || '暂无联系人信息'}</small>
                  </td>
                  <td><Badge tone={row.targetType === 'CUSTOMER' ? 'info' : 'neutral'}>{targetTypeName(row.targetType)}</Badge></td>
                  <td>{row.productName || '-'}</td>
                  <td>{row.ownerName || '-'}</td>
                  <td>{formatDateTime(row.lastFollowupAt)}</td>
                  <td>{formatDateTime(row.nextFollowTime)}</td>
                  <td><Badge dot tone={healthTone(row.followupHealth)}>{row.followupReason || healthName(row.followupHealth)}</Badge></td>
                  <td>
                    <button type="button" className="table-text-button primary" onClick={() => goFollowup(row)}>去跟进</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!loading && !records.length && (
            <div className="empty-table">
              {routeHealth === 'CRITICAL' ? <AlertTriangle size={26} /> : <Clock3 size={26} />}
              <b>当前没有{healthName(routeHealth)}对象</b>
              <span>列表与工作台统计使用同一套跟进时间规则。</span>
            </div>
          )}
          {loading && !records.length && (
            <div className="empty-table">
              <RefreshCw size={26} />
              <b>正在加载待跟进对象</b>
            </div>
          )}
        </div>
        <div className="table-footer">
          <span>共 {page.total || 0} 条，当前第 {currentPage} / {totalPages} 页</span>
          <div className="pagination">
            <button type="button" disabled={loading || currentPage <= 1} onClick={() => goPage(currentPage - 1)}>‹</button>
            <button type="button" className="active">{currentPage}</button>
            <button type="button" disabled={loading || currentPage >= totalPages} onClick={() => goPage(currentPage + 1)}>›</button>
          </div>
        </div>
      </Card>
    </div>
  )
}
