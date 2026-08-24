import { useEffect, useState } from 'react'
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  Database,
  ExternalLink,
  Plus,
  RefreshCw,
  Settings,
  Trash2,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, ConfirmDialog, Field, Modal, Select, useConfirmDialog } from '../../components'
import { useProductOptions } from '../../hooks/useProductOptions'

const emptySourceForm = {
  name: '',
  sourceType: 'WECOM_SMART_SHEET',
  status: 'ACTIVE',
  productId: '',
  syncMode: 'MANUAL',
  sourceUrl: '',
  syncIntervalMinutes: 1440,
  autoSync: false,
  autoAnalyze: false,
  fieldMappingJson: '',
}

const sourceTypeText = {
  WECOM_SMART_SHEET: '企微智能表格',
  MARKETING_FORM: '获客表单',
  WEBSITE_FORM: '官网表单',
  EXCEL_IMPORT: 'Excel导入',
  MANUAL: '手动来源',
}

const statusText = {
  ACTIVE: '正常',
  DISABLED: '停用',
  ERROR: '异常',
}

const statusTone = {
  ACTIVE: 'success',
  DISABLED: 'neutral',
  ERROR: 'warning',
}

function formatDateTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function numberValue(value) {
  if (value === undefined || value === null) return 0
  return Number(value) || 0
}

function buildSourcePayload(form) {
  return {
    ...form,
    syncMode: 'MANUAL',
    syncIntervalMinutes: 1440,
    autoSync: false,
    autoAnalyze: false,
    fieldMappingJson: form.fieldMappingJson || null,
  }
}

export function ChannelSourceBoard({ canManage, notify }) {
  const { confirm, dialogProps } = useConfirmDialog()
  const products = useProductOptions(notify, true, canManage)
  const [sources, setSources] = useState([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)
  const [logs, setLogs] = useState([])
  const [logSource, setLogSource] = useState(null)

  const load = async () => {
    setLoading(true)
    try {
      setSources(await api.channelSource.list({}) || [])
    } catch (err) {
      notify(err.message || '加载渠道来源失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const save = async (form) => {
    if (!form.productId) {
      notify('请选择该渠道咨询的产品', 'info')
      return
    }
    try {
      await api.channelSource.save(buildSourcePayload(form))
      notify('渠道来源已保存', 'success')
      setEditing(null)
      await load()
    } catch (err) {
      notify(err.message || '保存渠道来源失败', 'info')
    }
  }

  const remove = async (source) => {
    const confirmed = await confirm({
      title: '删除渠道来源',
      description: '删除后不会删除已经进入原始数据池的记录，只会停止该来源后续同步。',
      target: source.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.channelSource.delete(source.id)
      notify('渠道来源已删除', 'success')
      await load()
    } catch (err) {
      notify(err.message || '删除渠道来源失败', 'info')
    }
  }

  const openLogs = async (source) => {
    setLogSource(source)
    try {
      setLogs(await api.channelSource.logs(source.id) || [])
    } catch (err) {
      notify(err.message || '加载同步日志失败', 'info')
      setLogs([])
    }
  }

  return (
    <Card className="channel-source-board">
      <div className="channel-source-head">
        <div>
          <span className="eyebrow">渠道来源</span>
          <h3>渠道场景配置中心</h3>
          <p>集中查看渠道来源和真实数据指标；获客数据统一进入公海池，由负责人分配给销售。</p>
        </div>
        <div className="channel-source-actions">
          <Button variant="secondary" icon={RefreshCw} onClick={load}>刷新</Button>
          {canManage && <Button icon={Plus} onClick={() => setEditing(emptySourceForm)}>新增来源</Button>}
        </div>
      </div>

      {loading && <div className="channel-source-empty">正在加载渠道来源...</div>}
      {!loading && sources.length === 0 && (
        <div className="channel-source-empty">
          <Database size={28} />
          <b>暂无渠道来源</b>
          <span>先接入企微智能表格、获客表单或官网表单，同步数据会自动进入公海池。</span>
        </div>
      )}
      {!loading && sources.length > 0 && (
        <div className="channel-source-grid">
          {sources.map((source) => (
            <ChannelSourceCard
              key={source.id}
              source={source}
              canManage={canManage}
              onEdit={() => setEditing({ ...emptySourceForm, ...source })}
              onLogs={() => openLogs(source)}
              onDelete={() => remove(source)}
            />
          ))}
        </div>
      )}

      <ChannelSourceModal
        open={Boolean(editing)}
        form={editing || emptySourceForm}
        products={products}
        onClose={() => setEditing(null)}
        onSave={save}
      />
      <ChannelSourceLogsModal
        open={Boolean(logSource)}
        source={logSource}
        logs={logs}
        onClose={() => setLogSource(null)}
      />
      <ConfirmDialog {...dialogProps} />
    </Card>
  )
}

function ChannelSourceCard({ source, canManage, onEdit, onLogs, onDelete }) {
  const pendingCount = Math.max(0, numberValue(source.totalRecordCount) - numberValue(source.convertedLeadCount))
  return (
    <div className={`channel-source-card ${source.status === 'ERROR' ? 'error' : ''}`}>
      <div className="channel-source-card-left">
        <div className="channel-source-card-top">
          <div>
            <h4>{source.name}</h4>
            <p>{sourceTypeText[source.sourceType] || source.sourceType} · {source.productName || '未关联产品'}</p>
          </div>
          <Badge tone={statusTone[source.status] || 'neutral'}>{statusText[source.status] || source.status}</Badge>
        </div>
        <div className="channel-source-meta">
          <span><Database size={14} />数据进入公海池，分配后自动生成销售线索</span>
        </div>
        {source.lastError && (
          <div className="channel-source-error">
            <AlertTriangle size={14} />
            <span>{source.lastError}</span>
          </div>
        )}
      </div>
      <div className="channel-source-card-right">
        <div className="channel-source-metrics">
          <div><span>数据总量</span><b>{numberValue(source.totalRecordCount)}</b></div>
          <div><span>今日新增</span><b>{numberValue(source.todayNewCount)}</b></div>
          <div><span>待分配</span><b>{pendingCount}</b></div>
          <div><span>已分配线索</span><b>{numberValue(source.convertedLeadCount)}</b></div>
        </div>
        <div className="channel-source-foot">
          <span>最近导入：{formatDateTime(source.lastSuccessAt || source.lastSyncAt)}</span>
          <div>
            {source.sourceUrl && (
              <a href={source.sourceUrl} target="_blank" rel="noreferrer" className="text-action">
                <ExternalLink size={14} />打开
              </a>
            )}
            <button className="text-action" onClick={onLogs}><Clock size={14} />日志</button>
            {canManage && <button className="text-action" onClick={onEdit}><Settings size={14} />配置</button>}
            {canManage && <button className="text-action danger" onClick={onDelete}><Trash2 size={14} />删除</button>}
          </div>
        </div>
      </div>
    </div>
  )
}

function ChannelSourceModal({ open, form, products, onClose, onSave }) {
  const [draft, setDraft] = useState(form)

  useEffect(() => {
    setDraft(form)
  }, [form])

  if (!open) return null

  const update = (patch) => setDraft({ ...draft, ...patch })

  return (
    <Modal open={open} title={draft?.id ? '编辑渠道来源' : '新增渠道来源'} onClose={onClose}>
      <div className="channel-source-form">
        <Field label="渠道名称">
          <input value={draft.name || ''} onChange={(event) => update({ name: event.target.value })} placeholder="例如：企微产品预约表" />
        </Field>
        <Field label="企微智能表格链接">
          <textarea
            rows="4"
            value={draft.sourceUrl || ''}
            onChange={(event) => update({ sourceUrl: event.target.value })}
            placeholder="粘贴 doc.weixin.qq.com/smartsheet 链接"
          />
        </Field>
        <Field label="关联产品">
          <Select
            searchable
            value={draft.productId}
            options={(products || []).map((item) => ({
              value: item.id,
              label: item.name,
              description: item.description || item.code,
            }))}
            placeholder="请选择该渠道咨询的产品"
            searchPlaceholder="搜索产品名称"
            emptyText="暂无可选产品，请先到产品管理创建"
            onChange={(productId) => update({ productId })}
          />
        </Field>
        <div className="channel-source-form-grid">
          <Field label="状态">
            <select value={draft.status || 'ACTIVE'} onChange={(event) => update({ status: event.target.value })}>
              <option value="ACTIVE">正常</option>
              <option value="DISABLED">停用</option>
            </select>
          </Field>
        </div>
        <Field label="字段映射 JSON（可选）">
          <textarea
            rows="4"
            value={draft.fieldMappingJson || ''}
            onChange={(event) => update({ fieldMappingJson: event.target.value })}
            placeholder='{"companyName":"公司名称","contactName":"姓名","phone":"手机号","email":"邮箱"}'
          />
        </Field>
        <div className="modal-actions">
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button icon={CheckCircle2} onClick={() => onSave(draft)}>保存</Button>
        </div>
      </div>
    </Modal>
  )
}

function ChannelSourceLogsModal({ open, source, logs, onClose }) {
  if (!open) return null
  return (
    <Modal open={open} title={`${source?.name || '渠道来源'} · 导入记录`} onClose={onClose}>
      <div className="channel-source-logs">
        {logs.length === 0 && <div className="channel-source-empty">暂无导入记录</div>}
        {logs.map((log) => (
          <div className="channel-source-log" key={log.id}>
            <div>
              <Badge tone={log.status === 'SUCCESS' ? 'success' : log.status === 'FAILED' ? 'warning' : 'info'}>
                {log.status === 'SUCCESS' ? '成功' : log.status === 'FAILED' ? '失败' : '运行中'}
              </Badge>
              <span>渠道数据导入</span>
            </div>
            <p>
              拉取 {log.fetchedCount || 0}，新增 {log.createdCount || 0}，更新 {log.updatedCount || 0}，
              跳过 {log.skippedCount || 0}，失败 {log.failedCount || 0}
            </p>
            <small>{formatDateTime(log.startedAt)} - {formatDateTime(log.finishedAt)}</small>
            {log.errorMessage && <em>{log.errorMessage}</em>}
          </div>
        ))}
      </div>
    </Modal>
  )
}
