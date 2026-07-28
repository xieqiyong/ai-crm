import { useEffect, useState } from 'react'
import {
  BookOpen,
  Database,
  FileText,
  RefreshCw,
  Search,
  Trash2,
  Upload,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, ConfirmDialog, Field, Modal, PageHeader, useConfirmDialog } from '../../components'

const statusText = {
  DRAFT: '草稿',
  INDEXING: '入库中',
  READY: '可检索',
  WAITING_VECTOR: '待向量',
  FAILED: '失败',
}

const statusTone = {
  DRAFT: 'neutral',
  INDEXING: 'warning',
  READY: 'success',
  WAITING_VECTOR: 'info',
  FAILED: 'danger',
}

const vectorStatusText = {
  WAITING: '待向量',
  READY: '已向量化',
  FAILED: '向量失败',
}

const taskStatusText = {
  PENDING: '排队中',
  RUNNING: '执行中',
  SUCCESS: '已完成',
  SKIPPED: '已跳过',
  FAILED: '失败',
}

const terminalTaskStatuses = ['SUCCESS', 'SKIPPED', 'FAILED']

const emptyQuery = {
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  category: '',
  sourceType: '',
  status: '',
}

const emptyForm = {
  title: '',
  sourceType: 'PRODUCT',
  category: '产品知识',
  tags: '',
  sourceUrl: '',
  content: '',
}

const emptyImportForm = {
  title: '',
  sourceType: 'DOCUMENT',
  category: '产品知识',
  tags: '',
  sourceUrl: '',
}

function formatDateTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function compactQuery(query) {
  return {
    pageNo: query.pageNo || 1,
    pageSize: query.pageSize || 20,
    keyword: query.keyword || undefined,
    category: query.category || undefined,
    sourceType: query.sourceType || undefined,
    status: query.status || undefined,
  }
}

function toEditForm(row) {
  return {
    id: row.id,
    title: row.title || '',
    sourceType: row.sourceType || 'PRODUCT',
    category: row.category || '',
    tags: row.tags || '',
    sourceUrl: row.sourceUrl || '',
    content: row.content || '',
  }
}

export function KnowledgePage({ notify }) {
  const [query, setQuery] = useState(emptyQuery)
  const [page, setPage] = useState({ records: [], total: 0, pageNo: 1, pageSize: 20 })
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)
  const [importing, setImporting] = useState(false)
  const [searchText, setSearchText] = useState('')
  const [searchResult, setSearchResult] = useState(null)
  const [taskMap, setTaskMap] = useState({})
  const { confirm, dialogProps } = useConfirmDialog()

  const load = async (nextQuery = query) => {
    setLoading(true)
    try {
      const data = await api.knowledge.pageDocument(compactQuery(nextQuery))
      setPage(data || { records: [], total: 0 })
      setQuery(nextQuery)
    } catch (err) {
      notify(err.message || '知识库加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(emptyQuery)
  }, [])

  const refreshFirstPage = () => load({ ...query, pageNo: 1 })

  const openEdit = async (row) => {
    try {
      const data = await api.knowledge.detailDocument(row.id)
      setEditing(toEditForm(data))
    } catch (err) {
      notify(err.message || '知识详情加载失败', 'info')
    }
  }

  const trackTask = (response) => {
    if (!response?.taskId || !response?.documentId) return
    const task = {
      id: response.taskId,
      documentId: response.documentId,
      status: response.status || 'PENDING',
      stage: response.stage || '排队中',
      progress: response.progress || 0,
      message: response.message || '任务已提交',
      events: [],
    }
    setTaskMap((prev) => ({ ...prev, [response.documentId]: task }))
    pollTask(response.taskId, response.documentId)
  }

  const pollTask = (taskId, documentId, attempt = 0) => {
    window.setTimeout(async () => {
      try {
        const task = await api.knowledge.ingestTask(taskId)
        setTaskMap((prev) => ({ ...prev, [documentId]: task }))
        if (terminalTaskStatuses.includes(task.status)) {
          notify(task.message || '知识入库任务已结束', task.status === 'FAILED' ? 'info' : 'success')
          load()
          return
        }
        if (attempt < 600) {
          pollTask(taskId, documentId, attempt + 1)
        }
      } catch (err) {
        notify(err.message || '知识入库任务状态读取失败', 'info')
      }
    }, 1200)
  }

  const ingest = async (row) => {
    try {
      const response = await api.knowledge.ingestDocument(row.id)
      notify(response.message || '知识入库任务已提交', 'success')
      trackTask(response)
      refreshFirstPage()
    } catch (err) {
      notify(err.message || '知识入库失败', 'info')
    }
  }

  const forceIngest = async (row) => {
    const confirmed = await confirm({
      title: '强制重建知识索引',
      description: '强制重建会重新切分、向量化并写入索引，通常只在索引损坏或切换模型后使用。',
      target: row.title,
      confirmText: '确认重建',
      tone: 'warning',
    })
    if (!confirmed) return
    try {
      const response = await api.knowledge.ingestDocument({ id: row.id, force: true })
      notify(response.message || '知识索引重建任务已提交', 'success')
      trackTask(response)
      refreshFirstPage()
    } catch (err) {
      notify(err.message || '知识索引重建失败', 'info')
    }
  }

  const remove = async (row) => {
    const confirmed = await confirm({
      title: '删除知识文档',
      description: '删除后该文档不会再参与知识库检索，已生成的分片会同步标记删除。',
      target: row.title,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.knowledge.deleteDocument(row.id)
      notify('知识文档已删除', 'success')
      load()
    } catch (err) {
      notify(err.message || '知识文档删除失败', 'info')
    }
  }

  const searchKnowledge = async () => {
    if (!searchText.trim()) {
      notify('请输入检索内容', 'info')
      return
    }
    try {
      const response = await api.knowledge.search({ query: searchText.trim(), topK: 6 })
      setSearchResult(response)
      notify(response.message || '检索完成', 'success')
    } catch (err) {
      notify(err.message || '知识库检索失败', 'info')
    }
  }

  const records = page.records || []
  const readyCount = records.filter((item) => item.status === 'READY').length
  const chunkCount = records.reduce((sum, item) => sum + Number(item.chunkCount || 0), 0)
  const currentPage = page.pageNo || query.pageNo || 1
  const pageSize = page.pageSize || query.pageSize || 20
  const totalPages = Math.max(1, Math.ceil((page.total || 0) / pageSize))

  return (
    <div className="page knowledge-page">
      <PageHeader
        eyebrow="RAG"
        title="知识库"
        description="沉淀产品定位、解决方案、客户案例和FAQ，供AI智能体、获客表单和客服机器人检索使用"
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={refreshFirstPage}>刷新</Button>
            <Button variant="secondary" icon={Upload} onClick={() => setImporting(true)}>上传文档</Button>
            <Button icon={BookOpen} onClick={() => setEditing(emptyForm)}>新建知识</Button>
          </>
        )}
      />

      <div className="knowledge-overview">
        <KnowledgeStat icon={BookOpen} label="当前页文档" value={records.length} />
        <KnowledgeStat icon={Database} label="当前页可检索" value={readyCount} />
        <KnowledgeStat icon={FileText} label="当前页分片" value={chunkCount} />
      </div>

      <Card className="knowledge-search-card">
        <div>
          <b>知识库检索测试</b>
          <span>用于验证产品知识、方案资料、FAQ 是否能被AI检索到。</span>
        </div>
        <div className="knowledge-search-line">
          <input
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && searchKnowledge()}
            placeholder="例如：我们产品能解决哪些客户运维痛点？"
          />
          <Button icon={Search} onClick={searchKnowledge}>检索</Button>
        </div>
        {searchResult && (
          <div className="knowledge-search-result">
            <small>
              {searchResult.message}
              {' · '}
              Milvus：{searchResult.milvusEnabled ? '已启用' : '未启用'}
              {' · '}
              ES：{searchResult.elasticsearchEnabled ? '已启用' : '未启用'}
            </small>
            {(searchResult.hits || []).map((hit) => (
              <div className="knowledge-hit" key={hit.chunkId}>
                <div>
                  <b>{hit.title || '-'}</b>
                  <Badge tone="info">
                    {hit.matchType || '检索'}{hit.indexVersion ? ` · v${hit.indexVersion}` : ''}
                  </Badge>
                </div>
                <p>{hit.content}</p>
              </div>
            ))}
            {!(searchResult.hits || []).length && <p className="knowledge-empty-text">没有匹配知识。</p>}
          </div>
        )}
      </Card>

      <Card className="filter-card knowledge-filter">
        <label>
          <span>关键词</span>
          <input
            value={query.keyword}
            onChange={(event) => setQuery({ ...query, keyword: event.target.value })}
            placeholder="标题、标签或内容"
          />
        </label>
        <label>
          <span>分类</span>
          <input
            value={query.category}
            onChange={(event) => setQuery({ ...query, category: event.target.value })}
            placeholder="产品知识 / 客户案例"
          />
        </label>
        <label>
          <span>状态</span>
          <select value={query.status} onChange={(event) => setQuery({ ...query, status: event.target.value })}>
            <option value="">全部</option>
            <option value="DRAFT">草稿</option>
            <option value="READY">可检索</option>
            <option value="WAITING_VECTOR">待向量</option>
            <option value="FAILED">失败</option>
          </select>
        </label>
        <Button variant="secondary" icon={Search} onClick={refreshFirstPage}>筛选</Button>
      </Card>

      <KnowledgeTable
        records={records}
        loading={loading}
        page={page}
        query={query}
        currentPage={currentPage}
        totalPages={totalPages}
        taskMap={taskMap}
        onLoad={load}
        onEdit={openEdit}
        onIngest={ingest}
        onForceIngest={forceIngest}
        onDelete={remove}
      />

      <KnowledgeEditModal
        open={Boolean(editing)}
        data={editing}
        onClose={() => setEditing(null)}
        notify={notify}
        onTaskStart={trackTask}
        reload={refreshFirstPage}
      />
      <KnowledgeImportModal
        open={importing}
        onClose={() => setImporting(false)}
        notify={notify}
        reload={refreshFirstPage}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function KnowledgeStat({ icon: Icon, label, value }) {
  return (
    <Card className="knowledge-stat">
      <span><Icon size={18} /></span>
      <small>{label}</small>
      <b>{value}</b>
    </Card>
  )
}

function KnowledgeTable({
  records,
  loading,
  page,
  query,
  currentPage,
  totalPages,
  taskMap,
  onLoad,
  onEdit,
  onIngest,
  onForceIngest,
  onDelete,
}) {
  return (
    <Card className="table-card knowledge-table-card">
      <div className="data-table-wrap">
        <table className="data-table knowledge-table">
          <thead>
            <tr>
              <th>知识文档</th>
              <th>分类</th>
              <th>状态</th>
              <th>版本</th>
              <th>分片</th>
              <th>任务进度</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {records.map((row) => (
              <KnowledgeTableRow
                key={row.id}
                row={row}
                task={taskMap[row.id]}
                onEdit={onEdit}
                onIngest={onIngest}
                onForceIngest={onForceIngest}
                onDelete={onDelete}
              />
            ))}
          </tbody>
        </table>

        {!loading && records.length === 0 && (
          <div className="empty-table">
            <BookOpen size={26} />
            <b>暂无知识文档</b>
            <span>上传产品资料或新建知识后，这里会展示真实数据。</span>
          </div>
        )}
        {loading && (
          <div className="empty-table">
            <RefreshCw size={26} />
            <b>正在加载</b>
            <span>正在读取知识库文档</span>
          </div>
        )}
      </div>

      <div className="table-footer">
        <span>共 {page.total || 0} 条，当前第 {currentPage} / {totalPages} 页</span>
        <div className="pagination">
          <button
            disabled={currentPage <= 1}
            onClick={() => onLoad({ ...query, pageNo: currentPage - 1 })}
          >
            上一页
          </button>
          <button className="active">{currentPage}</button>
          <button
            disabled={currentPage >= totalPages}
            onClick={() => onLoad({ ...query, pageNo: currentPage + 1 })}
          >
            下一页
          </button>
        </div>
      </div>
    </Card>
  )
}

function KnowledgeTableRow({ row, task, onEdit, onIngest, onForceIngest, onDelete }) {
  const latestEvent = task?.events?.length ? task.events[task.events.length - 1] : null
  return (
    <tr>
      <td>
        <strong>{row.title}</strong>
        <small>{row.tags || row.sourceUrl || row.objectKey || '未填写标签'}</small>
      </td>
      <td>{row.category || '-'}</td>
      <td>
        <Badge dot tone={statusTone[row.status] || 'neutral'}>
          {statusText[row.status] || row.status}
        </Badge>
      </td>
      <td>{row.indexVersion ? `v${row.indexVersion}` : '-'}</td>
      <td>{row.chunkCount || 0}</td>
      <td>
        {task ? (
          <div className="knowledge-task-cell">
            <div>
              <Badge tone={task.status === 'FAILED' ? 'danger' : 'info'}>
                {taskStatusText[task.status] || task.status}
              </Badge>
              <span>{task.progress || 0}%</span>
            </div>
            <i><em style={{ width: `${task.progress || 0}%` }} /></i>
            <small>{latestEvent?.message || task.message || task.stage}</small>
          </div>
        ) : (
          vectorStatusText[row.vectorStatus] || row.vectorStatus || '-'
        )}
      </td>
      <td>{formatDateTime(row.updatedAt)}</td>
      <td>
        <div className="table-action-row">
          <button className="text-action" onClick={() => onEdit(row)}>编辑</button>
          <button className="text-action strong" onClick={() => onIngest(row)}>入库</button>
          <button className="text-action" onClick={() => onForceIngest(row)}>重建</button>
          <button className="text-action danger" onClick={() => onDelete(row)}>
            <Trash2 size={13} />
            删除
          </button>
        </div>
      </td>
    </tr>
  )
}

function KnowledgeEditModal({ open, data, onClose, notify, reload }) {
  const [form, setForm] = useState(emptyForm)

  useEffect(() => {
    if (open) {
      setForm(data || emptyForm)
    }
  }, [open, data])

  const submit = async () => {
    if (!form.title?.trim()) {
      notify('知识标题不能为空', 'info')
      return
    }
    try {
      await api.knowledge.saveDocument(form)
      notify('知识文档已保存', 'success')
      onClose()
      reload()
    } catch (err) {
      notify(err.message || '知识文档保存失败', 'info')
    }
  }

  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>取消</Button>
      <Button onClick={submit}>保存</Button>
    </>
  )

  return (
    <Modal open={open} title={form.id ? '编辑知识' : '新建知识'} onClose={onClose} size="lg" footer={footer}>
      <div className="form-grid knowledge-form-grid">
        <Field label="标题" required>
          <input value={form.title || ''} onChange={(event) => setForm({ ...form, title: event.target.value })} />
        </Field>
        <Field label="来源类型">
          <select
            value={form.sourceType || 'PRODUCT'}
            onChange={(event) => setForm({ ...form, sourceType: event.target.value })}
          >
            <option value="PRODUCT">产品资料</option>
            <option value="SOLUTION">解决方案</option>
            <option value="CASE">客户案例</option>
            <option value="FAQ">FAQ</option>
            <option value="SALES_SOP">销售话术</option>
            <option value="DOCUMENT">文档导入</option>
          </select>
        </Field>
        <Field label="分类">
          <input
            value={form.category || ''}
            onChange={(event) => setForm({ ...form, category: event.target.value })}
            placeholder="例如：产品知识"
          />
        </Field>
        <Field label="标签">
          <input
            value={form.tags || ''}
            onChange={(event) => setForm({ ...form, tags: event.target.value })}
            placeholder="多个标签用逗号分隔"
          />
        </Field>
        <Field label="来源链接" className="wide-field">
          <input
            value={form.sourceUrl || ''}
            onChange={(event) => setForm({ ...form, sourceUrl: event.target.value })}
            placeholder="官网、文档或案例链接，可为空"
          />
        </Field>
        <Field label="知识正文" required className="wide-field">
          <textarea
            rows="14"
            value={form.content || ''}
            onChange={(event) => setForm({ ...form, content: event.target.value })}
            placeholder="填写公司产品定位、能力边界、解决方案、FAQ或客户案例"
          />
        </Field>
      </div>
    </Modal>
  )
}

function KnowledgeImportModal({ open, onClose, notify, onTaskStart, reload }) {
  const [form, setForm] = useState(emptyImportForm)
  const [file, setFile] = useState(null)

  useEffect(() => {
    if (!open) {
      setForm(emptyImportForm)
      setFile(null)
    }
  }, [open])

  const selectFile = (event) => {
    const nextFile = event.target.files?.[0]
    if (!nextFile) return
    setFile(nextFile)
    setForm({ ...form, title: form.title || nextFile.name })
  }

  const submit = async () => {
    if (!file) {
      notify('请选择知识文档', 'info')
      return
    }
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', form.title || file.name)
    formData.append('sourceType', form.sourceType || 'DOCUMENT')
    formData.append('category', form.category || '')
    formData.append('tags', form.tags || '')
    formData.append('sourceUrl', form.sourceUrl || '')
    try {
      const response = await api.knowledge.importDocument(formData)
      notify(response.message || '知识文档已导入，后台正在入库', 'success')
      onTaskStart(response)
      onClose()
      reload()
    } catch (err) {
      notify(err.message || '知识文档导入失败', 'info')
    }
  }

  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>取消</Button>
      <Button disabled={!file} onClick={submit}>导入入库</Button>
    </>
  )

  return (
    <Modal open={open} title="上传知识文档" onClose={onClose} footer={footer}>
      <div className="form-grid">
        <Field label="知识文档" required hint="支持 HTML、TXT、MD、DOCX，导入后会自动切分并写入检索索引。">
          <div className="upload-drop">
            <Upload size={26} />
            <span>{file ? file.name : '选择知识文档'}</span>
            <small>建议先导入产品介绍、部署方案、FAQ、客户案例和销售SOP。</small>
            <input type="file" accept=".html,.htm,.txt,.md,.markdown,.docx" onChange={selectFile} />
          </div>
        </Field>
        <Field label="标题">
          <input value={form.title || ''} onChange={(event) => setForm({ ...form, title: event.target.value })} />
        </Field>
        <Field label="来源类型">
          <select
            value={form.sourceType || 'DOCUMENT'}
            onChange={(event) => setForm({ ...form, sourceType: event.target.value })}
          >
            <option value="PRODUCT">产品资料</option>
            <option value="SOLUTION">解决方案</option>
            <option value="CASE">客户案例</option>
            <option value="FAQ">FAQ</option>
            <option value="SALES_SOP">销售话术</option>
            <option value="DOCUMENT">文档导入</option>
          </select>
        </Field>
        <Field label="分类">
          <input value={form.category || ''} onChange={(event) => setForm({ ...form, category: event.target.value })} />
        </Field>
        <Field label="标签">
          <input value={form.tags || ''} onChange={(event) => setForm({ ...form, tags: event.target.value })} />
        </Field>
        <Field label="来源链接">
          <input
            value={form.sourceUrl || ''}
            onChange={(event) => setForm({ ...form, sourceUrl: event.target.value })}
          />
        </Field>
      </div>
    </Modal>
  )
}
