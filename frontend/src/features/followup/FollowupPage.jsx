import { useEffect, useState } from 'react'
import { Edit2, MessageCircleMore, Plus, RefreshCw, Search, Trash2 } from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyPermission,
  PageHeader,
  RichTextViewer,
  useConfirmDialog,
} from '../../components'
import {
  FollowupFormModal,
  emptyFollowupForm,
  followupTypeText,
  formatDateTime,
  hasRichContent,
  targetTypeText,
  toFollowupForm,
  toFollowupPayload,
} from './FollowupPanel'
import { ownerName } from '../../hooks/useOwnerOptions'

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: [],
}

function compactQuery(query) {
  return {
    pageNo: query.pageNo || 1,
    pageSize: query.pageSize || 20,
    keyword: query.keyword || undefined,
    targetType: query.targetType || undefined,
    followupType: query.followupType || undefined,
  }
}

export function FollowupPage({ can, notify, navigate }) {
  const canWrite = can('crm:followup:manage') || can('crm:followup:create')
  const canView = can('crm:followup:view')
  const canDelete = can('crm:followup:manage')
  const { confirm, dialogProps } = useConfirmDialog()
  const [query, setQuery] = useState({ keyword: '', targetType: '', followupType: '', pageNo: 1, pageSize: 20 })
  const [page, setPage] = useState(emptyPage)
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)

  const load = async (nextQuery = query) => {
    if (!canView) return
    setLoading(true)
    try {
      const data = await api.followup.page(compactQuery(nextQuery))
      setPage(data || emptyPage)
      setQuery(nextQuery)
    } catch (error) {
      notify(error.message || '跟进记录加载失败', 'info')
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

  const save = async (form) => {
    if (!form.targetId) {
      notify('关联对象ID不能为空', 'info')
      return
    }
    if (!hasRichContent(form.content)) {
      notify('跟进内容不能为空', 'info')
      return
    }
    try {
      await api.followup.save(toFollowupPayload(form))
      notify('跟进记录已保存', 'success')
      setEditing(null)
      load(form.id ? query : { ...query, pageNo: 1 })
    } catch (error) {
      notify(error.message || '跟进记录保存失败', 'info')
    }
  }

  const remove = async (row) => {
    const confirmed = await confirm({
      title: '删除跟进记录',
      description: '删除后该跟进记录不会再出现在客户、线索或商机时间轴中。',
      target: row.targetName || row.targetId,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.followup.delete(row.id)
      notify('跟进记录已删除', 'success')
      load(query)
    } catch (error) {
      notify(error.message || '跟进记录删除失败', 'info')
    }
  }

  const records = page.records || []
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 20
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  if (!canView) {
    return <EmptyPermission onBack={() => navigate('dashboard')} />
  }

  return (
    <div className="page followup-page">
      <PageHeader
        title="跟进记录"
        description={`记录客户、线索、商机的真实销售动作，当前 ${page.total || 0} 条`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(query)}>刷新</Button>
            {canWrite && <Button icon={Plus} onClick={() => setEditing(emptyFollowupForm)}>写跟进</Button>}
          </>
        )}
      />

      <form className="filter-card customer-filter-card" onSubmit={search}>
        <div className="filter-search">
          <Search size={17} />
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="搜索对象名称、跟进内容、结果或下次计划"
          />
        </div>
        <label>
          <span>对象</span>
          <select value={query.targetType} onChange={(event) => setQuery({ ...query, targetType: event.target.value })}>
            <option value="">全部对象</option>
            {Object.entries(targetTypeText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </label>
        <label>
          <span>方式</span>
          <select
            value={query.followupType}
            onChange={(event) => setQuery({ ...query, followupType: event.target.value })}
          >
            <option value="">全部方式</option>
            {Object.entries(followupTypeText).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </label>
        <Button type="submit" variant="secondary" icon={Search}>查询</Button>
      </form>

      <Card className="table-card">
        <div className="data-table-wrap">
          <table className="data-table customer-list-table">
            <thead>
              <tr>
                <th>跟进对象</th>
                <th>方式</th>
                <th>跟进内容</th>
                <th>结果</th>
                <th>下次计划</th>
                <th>负责人</th>
                <th>跟进时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((row) => (
                <tr key={row.id}>
                  <td>
                    <strong>{row.targetName || row.targetId}</strong>
                    <small>{targetTypeText[row.targetType] || row.targetType} · {row.targetId}</small>
                  </td>
                  <td><Badge tone="info">{followupTypeText[row.followupType] || row.followupType}</Badge></td>
                  <td><div className="followup-table-content"><RichTextViewer value={row.content} empty="-" /></div></td>
                  <td>{row.result || '-'}</td>
                  <td>
                    <span>{row.nextPlan || '-'}</span>
                    <small>{row.nextFollowTime ? formatDateTime(row.nextFollowTime) : '-'}</small>
                  </td>
                  <td>{ownerName(row)}</td>
                  <td>{formatDateTime(row.followupAt)}</td>
                  <td>
                    <div className="table-action-row">
                      {canWrite && (
                        <button className="icon-button" onClick={() => setEditing(toFollowupForm(row))}>
                          <Edit2 size={17} />
                        </button>
                      )}
                      {canDelete && (
                        <button className="icon-button" onClick={() => remove(row)}>
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
              <MessageCircleMore size={26} />
              <b>暂无跟进记录</b>
              <span>可以从客户详情、线索详情、商机详情或当前页面写跟进。</span>
            </div>
          )}
          {loading && (
            <div className="empty-table">
              <RefreshCw size={26} />
              <b>正在加载跟进记录</b>
              <span>数据来自后台跟进记录接口</span>
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

      <FollowupFormModal
        open={Boolean(editing)}
        form={editing || emptyFollowupForm}
        onChange={setEditing}
        onClose={() => setEditing(null)}
        onSave={save}
        notify={notify}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}
