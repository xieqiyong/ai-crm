import { useEffect, useMemo, useState } from 'react'
import {
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Clock3,
  Copy,
  Database,
  ExternalLink,
  MessageCircleMore,
  Plus,
  RefreshCw,
  Send,
  Settings2,
  Sheet,
  Trash2,
  TriangleAlert,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, Field, Modal, Select } from '../../components'
import { useProductOptions } from '../../hooks/useProductOptions'

const platforms = [
  {
    value: 'WECOM',
    label: '企业微信',
    description: '使用企微文档 API 创建并直接读取记录',
    icon: MessageCircleMore,
    available: true,
  },
  {
    value: 'DINGTALK',
    label: '钉钉',
    description: '接口位置已预留，后续接入钉钉表格',
    icon: Send,
    available: false,
  },
  {
    value: 'FEISHU',
    label: '飞书',
    description: '接口位置已预留，后续接入飞书多维表格',
    icon: Sheet,
    available: false,
  },
]

const platformName = {
  WECOM: '企业微信',
  DINGTALK: '钉钉',
  FEISHU: '飞书',
}

const fieldTypes = [
  { value: 'FIELD_TYPE_TEXT', label: '文本' },
  { value: 'FIELD_TYPE_PHONE_NUMBER', label: '手机号' },
  { value: 'FIELD_TYPE_EMAIL', label: '邮箱' },
  { value: 'FIELD_TYPE_NUMBER', label: '数字' },
  { value: 'FIELD_TYPE_DATE_TIME', label: '日期时间' },
  { value: 'FIELD_TYPE_SINGLE_SELECT', label: '单选' },
  { value: 'FIELD_TYPE_SELECT', label: '多选' },
  { value: 'FIELD_TYPE_CHECKBOX', label: '复选框' },
  { value: 'FIELD_TYPE_URL', label: '链接' },
]

const coreFields = [
  { id: 'companyName', businessKey: 'companyName', title: '公司名称', fieldType: 'FIELD_TYPE_TEXT' },
  { id: 'contactName', businessKey: 'contactName', title: '联系人', fieldType: 'FIELD_TYPE_TEXT' },
  { id: 'phone', businessKey: 'phone', title: '联系电话', fieldType: 'FIELD_TYPE_PHONE_NUMBER' },
  { id: 'email', businessKey: 'email', title: '联系邮箱', fieldType: 'FIELD_TYPE_EMAIL' },
  { id: 'remark', businessKey: 'remark', title: '需求描述', fieldType: 'FIELD_TYPE_TEXT' },
]

const businessFieldOptions = [
  { value: '', label: '仅保留原始资料', description: '不回填公海池标准字段' },
  { value: 'companyName', label: '公司名称' },
  { value: 'contactName', label: '联系人' },
  { value: 'phone', label: '联系电话' },
  { value: 'email', label: '联系邮箱' },
  { value: 'remark', label: '需求描述' },
]

const identityBusinessKeys = ['companyName', 'contactName', 'phone', 'email']

function createEmptyForm() {
  return {
    platform: 'WECOM',
    name: '',
    productId: '',
    fields: coreFields.map((field) => ({ ...field })),
  }
}

function formatDateTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function numberValue(value) {
  return Number(value) || 0
}

function resolvePlatform(source) {
  const provider = String(source?.externalProvider || '')
  return provider.split('_')[0] || 'WECOM'
}

function validateFieldDesign(fields, notify) {
  if (!fields?.length) {
    notify('请至少配置一个表格字段', 'info')
    return false
  }
  if (fields.some((field) => !field.title?.trim())) {
    notify('请填写完整的字段名称', 'info')
    return false
  }
  const titles = fields.map((field) => field.title.trim().toLowerCase())
  if (new Set(titles).size !== titles.length) {
    notify('字段名称不能重复', 'info')
    return false
  }
  const businessKeys = fields.map((field) => field.businessKey).filter(Boolean)
  if (new Set(businessKeys).size !== businessKeys.length) {
    notify('同一个 CRM 业务用途只能绑定一个表格字段', 'info')
    return false
  }
  if (!businessKeys.some((key) => identityBusinessKeys.includes(key))) {
    notify('公司名称、联系人、联系电话、联系邮箱至少绑定一个', 'info')
    return false
  }
  return true
}

export function ManagedSheetBoard({ canManage, notify }) {
  const products = useProductOptions(notify, true, canManage)
  const [sources, setSources] = useState([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [syncingId, setSyncingId] = useState(null)
  const [logSource, setLogSource] = useState(null)
  const [logs, setLogs] = useState([])
  const [fieldSource, setFieldSource] = useState(null)
  const [managedFields, setManagedFields] = useState([])
  const [fieldLoading, setFieldLoading] = useState(false)
  const [fieldSaving, setFieldSaving] = useState(false)
  const [deleteSource, setDeleteSource] = useState(null)
  const [deletingMode, setDeletingMode] = useState('')

  const load = async () => {
    setLoading(true)
    try {
      setSources(await api.channelSource.list({ managed: true }) || [])
    } catch (err) {
      notify(err.message || '加载自建表格失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const create = async (form) => {
    if (!form.name?.trim()) {
      notify('请填写表格名称', 'info')
      return
    }
    if (!form.productId) {
      notify('请选择该表格关联的产品', 'info')
      return
    }
    const fields = form.fields || []
    if (!validateFieldDesign(fields, notify)) return
    const platform = platforms.find((item) => item.value === form.platform)
    if (!platform?.available) {
      notify(`${platform?.label || '当前平台'}暂未接入`, 'info')
      return
    }
    setCreating(true)
    try {
      await api.channelSource.managedSheetCreate({
        name: form.name.trim(),
        platform: form.platform,
        productId: form.productId,
        fields: fields.map((field) => ({
          businessKey: field.businessKey || null,
          title: field.title.trim(),
          fieldType: field.fieldType,
        })),
      })
      notify('智能表格创建成功，可以直接打开填写', 'success')
      setCreateOpen(false)
      await load()
    } catch (err) {
      notify(err.message || '创建智能表格失败', 'info')
    } finally {
      setCreating(false)
    }
  }

  const sync = async (source) => {
    setSyncingId(source.id)
    try {
      const result = await api.channelSource.managedSheetSync(source.id)
      notify(
        `拉取完成：新增 ${result?.createdCount || 0}，更新 ${result?.updatedCount || 0}，跳过 ${result?.skippedCount || 0}`,
        'success',
      )
      await load()
    } catch (err) {
      notify(err.message || '拉取表格数据失败', 'info')
      await load()
    } finally {
      setSyncingId(null)
    }
  }

  const openFields = async (source) => {
    setFieldSource(source)
    setManagedFields([])
    setFieldLoading(true)
    try {
      const fields = await api.channelSource.managedSheetFields(source.id)
      setManagedFields((fields || []).map((field, index) => ({
        ...field,
        id: field.fieldId || `field-${index}`,
      })))
    } catch (err) {
      notify(err.message || '读取企微表格字段失败', 'info')
      setFieldSource(null)
    } finally {
      setFieldLoading(false)
    }
  }

  const saveFields = async (fields) => {
    if (!fieldSource || !validateFieldDesign(fields, notify)) return
    setFieldSaving(true)
    try {
      const saved = await api.channelSource.managedSheetFieldsSave(
        fieldSource.id,
        fields.map((field) => ({
          fieldId: field.fieldId || null,
          businessKey: field.businessKey || null,
          title: field.title.trim(),
          fieldType: field.fieldType,
        })),
      )
      setManagedFields((saved || []).map((field, index) => ({
        ...field,
        id: field.fieldId || `field-${index}`,
      })))
      notify('表格字段已同步到企业微信', 'success')
      setFieldSource(null)
      await load()
    } catch (err) {
      notify(err.message || '保存企微表格字段失败', 'info')
    } finally {
      setFieldSaving(false)
    }
  }

  const remove = async (deletePlatformDocument) => {
    if (!deleteSource) return
    setDeletingMode(deletePlatformDocument ? 'platform' : 'config')
    try {
      await api.channelSource.managedSheetDelete(deleteSource.id, deletePlatformDocument)
      notify(
        deletePlatformDocument ? '企微智能表格和 CRM 配置均已删除' : 'CRM 配置已移除，企微智能表格仍然保留',
        'success',
      )
      setDeleteSource(null)
      await load()
    } catch (err) {
      notify(err.message || '删除自建表格失败', 'info')
    } finally {
      setDeletingMode('')
    }
  }

  const openLogs = async (source) => {
    setLogSource(source)
    try {
      setLogs(await api.channelSource.logs(source.id) || [])
    } catch (err) {
      setLogs([])
      notify(err.message || '加载拉取记录失败', 'info')
    }
  }

  const copyLink = async (source) => {
    if (!source?.sourceUrl) {
      notify('当前表格没有可复制的访问链接', 'info')
      return
    }
    try {
      await navigator.clipboard.writeText(source.sourceUrl)
      notify('表格链接已复制', 'success')
    } catch (err) {
      notify('复制失败，请点击“打开表格”后从地址栏复制', 'info')
    }
  }

  return (
    <Card className="channel-source-board managed-sheet-board">
      <div className="channel-source-head">
        <div>
          <span className="eyebrow">平台 API</span>
          <h3>自建表格</h3>
          <p>由 CRM 调用平台接口创建获客表格，填写记录可直接拉取到公海池，无需浏览器导出。</p>
        </div>
        <div className="channel-source-actions">
          <Button variant="secondary" icon={RefreshCw} onClick={load}>刷新</Button>
          {canManage && <Button icon={Plus} onClick={() => setCreateOpen(true)}>创建表格</Button>}
        </div>
      </div>

      <div className="managed-sheet-capabilities">
        {platforms.map((platform) => {
          const Icon = platform.icon
          return (
            <div className={`managed-sheet-capability ${platform.available ? 'available' : ''}`} key={platform.value}>
              <span><Icon size={18} /></span>
              <div><b>{platform.label}</b><small>{platform.available ? '已接入' : '待接入'}</small></div>
            </div>
          )
        })}
      </div>

      {loading && <div className="channel-source-empty">正在加载自建表格...</div>}
      {!loading && sources.length === 0 && (
        <div className="channel-source-empty managed-sheet-empty">
          <Database size={28} />
          <b>还没有自建表格</b>
          <span>创建后会得到可打开的企微智能表格，客户填写的数据可随时拉取。</span>
          {canManage && <Button icon={Plus} onClick={() => setCreateOpen(true)}>创建第一张表格</Button>}
        </div>
      )}
      {!loading && sources.length > 0 && (
        <div className="channel-source-grid">
          {sources.map((source) => (
            <ManagedSheetCard
              key={source.id}
              source={source}
              canManage={canManage}
              syncing={String(syncingId || '') === String(source.id)}
              onSync={() => sync(source)}
              onCopy={() => copyLink(source)}
              onLogs={() => openLogs(source)}
              onFields={() => openFields(source)}
              onDelete={() => setDeleteSource(source)}
            />
          ))}
        </div>
      )}

      <ManagedSheetCreateModal
        open={createOpen}
        creating={creating}
        products={products}
        onClose={() => setCreateOpen(false)}
        onCreate={create}
      />
      <ManagedSheetLogsModal
        open={Boolean(logSource)}
        source={logSource}
        logs={logs}
        onClose={() => setLogSource(null)}
      />
      <ManagedSheetFieldsModal
        open={Boolean(fieldSource)}
        source={fieldSource}
        fields={managedFields}
        loading={fieldLoading}
        saving={fieldSaving}
        onClose={() => !fieldSaving && setFieldSource(null)}
        onSave={saveFields}
      />
      <ManagedSheetDeleteModal
        open={Boolean(deleteSource)}
        source={deleteSource}
        deletingMode={deletingMode}
        onClose={() => !deletingMode && setDeleteSource(null)}
        onRemoveConfig={() => remove(false)}
        onDeletePlatform={() => remove(true)}
      />
    </Card>
  )
}

function ManagedSheetCard({ source, canManage, syncing, onSync, onCopy, onLogs, onFields, onDelete }) {
  const platform = resolvePlatform(source)
  const pendingCount = Math.max(0, numberValue(source.totalRecordCount) - numberValue(source.convertedLeadCount))
  return (
    <div className={`channel-source-card managed-sheet-card ${source.status === 'ERROR' ? 'error' : ''}`}>
      <div className="channel-source-card-left">
        <div className="channel-source-card-top">
          <div>
            <h4>{source.name}</h4>
            <p>{platformName[platform] || platform} · {source.productName || '未关联产品'}</p>
          </div>
          <Badge tone={source.status === 'ACTIVE' ? 'success' : source.status === 'ERROR' ? 'warning' : 'neutral'}>
            {source.status === 'ACTIVE' ? '可用' : source.status === 'ERROR' ? '拉取异常' : '已停用'}
          </Badge>
        </div>
        <div className="channel-source-meta">
          <span><CheckCircle2 size={14} />平台接口创建，记录可直接读取并进入公海池</span>
          {source.sourceUrl && (
            <span className="managed-sheet-url" title={source.sourceUrl}>{source.sourceUrl}</span>
          )}
        </div>
        {source.lastError && <div className="channel-source-error">{source.lastError}</div>}
      </div>
      <div className="channel-source-card-right">
        <div className="channel-source-metrics">
          <div><span>已读取记录</span><b>{numberValue(source.totalRecordCount)}</b></div>
          <div><span>今日新增</span><b>{numberValue(source.todayNewCount)}</b></div>
          <div><span>待分配</span><b>{pendingCount}</b></div>
          <div><span>已生成线索</span><b>{numberValue(source.convertedLeadCount)}</b></div>
        </div>
        <div className="channel-source-foot">
          <span>最近拉取：{formatDateTime(source.lastSuccessAt || source.lastSyncAt)}</span>
          <div>
            {source.sourceUrl && (
              <a href={source.sourceUrl} target="_blank" rel="noreferrer" className="text-action">
                <ExternalLink size={14} />打开表格
              </a>
            )}
            {source.sourceUrl && (
              <button className="text-action" onClick={onCopy}><Copy size={14} />复制链接</button>
            )}
            {canManage && (
              <button className="text-action" disabled={syncing} onClick={onSync}>
                <RefreshCw size={14} />{syncing ? '正在拉取' : '立即拉取'}
              </button>
            )}
            {canManage && <button className="text-action" onClick={onFields}><Settings2 size={14} />字段管理</button>}
            <button className="text-action" onClick={onLogs}><Clock3 size={14} />记录</button>
            {canManage && <button className="text-action danger" onClick={onDelete}><Trash2 size={14} />删除</button>}
          </div>
        </div>
      </div>
    </div>
  )
}

function ManagedSheetCreateModal({ open, creating, products, onClose, onCreate }) {
  const [form, setForm] = useState(createEmptyForm)
  const selectedPlatform = useMemo(
    () => platforms.find((item) => item.value === form.platform) || platforms[0],
    [form.platform],
  )

  useEffect(() => {
    if (open) setForm(createEmptyForm())
  }, [open])

  const updateField = (index, patch) => {
    setForm((current) => ({
      ...current,
      fields: current.fields.map((field, fieldIndex) => (
        fieldIndex === index ? { ...field, ...patch } : field
      )),
    }))
  }

  const addField = () => {
    setForm((current) => {
      if (current.fields.length >= 50) return current
      return {
        ...current,
        fields: [
          ...current.fields,
          {
            id: `custom-${Date.now()}-${current.fields.length}`,
            businessKey: null,
            title: '',
            fieldType: 'FIELD_TYPE_TEXT',
          },
        ],
      }
    })
  }

  const removeField = (index) => {
    setForm((current) => {
      return {
        ...current,
        fields: current.fields.filter((_, fieldIndex) => fieldIndex !== index),
      }
    })
  }

  const moveField = (index, offset) => {
    setForm((current) => {
      const target = index + offset
      if (target < 0 || target >= current.fields.length) return current
      const fields = [...current.fields]
      const selected = fields[index]
      fields[index] = fields[target]
      fields[target] = selected
      return { ...current, fields }
    })
  }

  if (!open) return null

  return (
    <Modal open={open} title="创建渠道智能表格" size="xl" onClose={onClose}>
      <div className="managed-sheet-create-form">
        <Field label="选择平台">
          <div className="managed-sheet-platforms">
            {platforms.map((platform) => {
              const Icon = platform.icon
              return (
                <button
                  type="button"
                  key={platform.value}
                  className={form.platform === platform.value ? 'active' : ''}
                  onClick={() => setForm({ ...form, platform: platform.value })}
                >
                  <span><Icon size={20} /></span>
                  <b>{platform.label}</b>
                  <small>{platform.available ? '已接入' : '待接入'}</small>
                </button>
              )
            })}
          </div>
        </Field>
        {!selectedPlatform.available && (
          <div className="managed-sheet-unavailable">{selectedPlatform.description}</div>
        )}
        <Field label="表格名称">
          <input
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            placeholder="例如：OpenOcta 产品咨询表"
          />
        </Field>
        <Field label="关联产品">
          <Select
            searchable
            value={form.productId}
            options={(products || []).map((item) => ({
              value: item.id,
              label: item.name,
              description: item.description || item.code,
            }))}
            placeholder="请选择该表格对应的意向产品"
            searchPlaceholder="搜索产品名称"
            emptyText="暂无可选产品，请先到产品管理创建"
            onChange={(productId) => setForm({ ...form, productId })}
          />
        </Field>
        <Field label="表格字段">
          <div className="managed-sheet-field-designer">
            <div className="managed-sheet-field-head">
              <div>
                <b>字段设计</b>
                <small>业务字段用于进入公海池，自定义字段会完整保留在渠道资料中。</small>
              </div>
              <button
                type="button"
                className="managed-sheet-add-field"
                disabled={form.fields.length >= 50}
                onClick={addField}
              >
                <Plus size={15} />新增字段
              </button>
            </div>
            <div className="managed-sheet-field-list">
              {form.fields.map((field, index) => (
                <div className="managed-sheet-field-row" key={field.id}>
                  <span className="managed-sheet-field-index">{index + 1}</span>
                  <input
                    value={field.title}
                    maxLength={64}
                    placeholder="请输入字段名称"
                    onChange={(event) => updateField(index, { title: event.target.value })}
                  />
                  <Select
                    value={field.fieldType}
                    options={fieldTypes}
                    onChange={(fieldType) => updateField(index, { fieldType })}
                  />
                  <Select
                    value={field.businessKey || ''}
                    options={businessFieldOptions}
                    placeholder="选择 CRM 用途"
                    onChange={(businessKey) => updateField(index, { businessKey: businessKey || null })}
                  />
                  <div className="managed-sheet-field-actions">
                    <button
                      type="button"
                      title="上移"
                      disabled={index === 0}
                      onClick={() => moveField(index, -1)}
                    >
                      <ChevronUp size={16} />
                    </button>
                    <button
                      type="button"
                      title="下移"
                      disabled={index === form.fields.length - 1}
                      onClick={() => moveField(index, 1)}
                    >
                      <ChevronDown size={16} />
                    </button>
                    <button
                      type="button"
                      className="danger"
                      title="删除字段"
                      onClick={() => removeField(index)}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <div className="managed-sheet-field-foot">
              <span>共 {form.fields.length} 个字段，最多 50 个</span>
              <span>记录创建时间由企微自动生成，无需额外添加。</span>
            </div>
          </div>
        </Field>
        <div className="modal-actions">
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button
            icon={Plus}
            disabled={creating || !selectedPlatform.available}
            onClick={() => onCreate(form)}
          >
            {creating ? '正在创建' : '创建表格'}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

function ManagedSheetFieldsModal({ open, source, fields, loading, saving, onClose, onSave }) {
  const [draft, setDraft] = useState([])

  useEffect(() => {
    if (open) setDraft((fields || []).map((field) => ({ ...field })))
  }, [fields, open])

  const updateField = (index, patch) => {
    setDraft((current) => current.map((field, fieldIndex) => (
      fieldIndex === index ? { ...field, ...patch } : field
    )))
  }

  const addField = () => {
    setDraft((current) => {
      if (current.length >= 50) return current
      return [
        ...current,
        {
          id: `new-${Date.now()}-${current.length}`,
          fieldId: null,
          businessKey: null,
          title: '',
          fieldType: 'FIELD_TYPE_TEXT',
          required: false,
        },
      ]
    })
  }

  const removeField = (index) => {
    setDraft((current) => {
      if (current[index]?.required) return current
      return current.filter((_, fieldIndex) => fieldIndex !== index)
    })
  }

  if (!open) return null

  return (
    <Modal
      open={open}
      title={`${source?.name || '自建表格'} · 字段管理`}
      size="xl"
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" disabled={saving} onClick={onClose}>取消</Button>
          <Button disabled={loading || saving} onClick={() => onSave(draft)}>
            {saving ? '正在同步' : '保存并同步企微'}
          </Button>
        </>
      )}
    >
      <div className="managed-sheet-fields-modal">
        <div className="managed-sheet-fields-note">
          <Settings2 size={18} />
          <div>
            <b>这里维护的是企微真实字段</b>
            <span>新增、重命名和删除会直接同步到企业微信；已有字段类型不可修改，避免历史数据失效。</span>
          </div>
        </div>
        {loading ? (
          <div className="channel-source-empty">正在读取企业微信字段...</div>
        ) : (
          <div className="managed-sheet-field-designer">
            <div className="managed-sheet-field-head">
              <div>
                <b>字段设计</b>
                <small>CRM 用途决定该列如何回填公海池；不选择用途的字段仍会保留在完整资料中。</small>
              </div>
              <button
                type="button"
                className="managed-sheet-add-field"
                disabled={draft.length >= 50 || saving}
                onClick={addField}
              >
                <Plus size={15} />新增字段
              </button>
            </div>
            <div className="managed-sheet-field-list">
              {draft.map((field, index) => (
                <div className="managed-sheet-field-row" key={field.id || field.fieldId || index}>
                  <span className="managed-sheet-field-index">{index + 1}</span>
                  <input
                    value={field.title || ''}
                    maxLength={64}
                    disabled={saving}
                    placeholder="请输入字段名称"
                    onChange={(event) => updateField(index, { title: event.target.value })}
                  />
                  <Select
                    value={field.fieldType}
                    options={fieldTypes}
                    disabled={Boolean(field.fieldId) || saving}
                    onChange={(fieldType) => updateField(index, { fieldType })}
                  />
                  <Select
                    value={field.businessKey || ''}
                    options={businessFieldOptions}
                    disabled={saving}
                    placeholder="选择 CRM 用途"
                    onChange={(businessKey) => updateField(index, { businessKey: businessKey || null })}
                  />
                  <div className="managed-sheet-field-actions">
                    <button
                      type="button"
                      className="danger"
                      title={field.required ? '企微默认标题字段不可删除' : '删除字段'}
                      disabled={field.required || saving}
                      onClick={() => removeField(index)}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <div className="managed-sheet-field-foot">
              <span>共 {draft.length} 个字段，最多 50 个</span>
              <span>企微默认标题字段可以重命名，但不能删除。</span>
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}

function ManagedSheetDeleteModal({
  open,
  source,
  deletingMode,
  onClose,
  onRemoveConfig,
  onDeletePlatform,
}) {
  const [confirmName, setConfirmName] = useState('')

  useEffect(() => {
    if (open) setConfirmName('')
  }, [open, source?.id])

  if (!open) return null

  const nameMatched = confirmName.trim() === String(source?.name || '').trim()
  return (
    <Modal open={open} title="删除自建表格" size="lg" onClose={onClose}>
      <div className="managed-sheet-delete-modal">
        <div className="managed-sheet-delete-target">
          <Sheet size={20} />
          <div><span>当前表格</span><b>{source?.name}</b></div>
        </div>
        <div className="managed-sheet-delete-options">
          <section>
            <div><Database size={20} /></div>
            <h3>仅移除 CRM 配置</h3>
            <p>停止在 CRM 中展示和拉取，企微智能表格以及已经进入公海池的数据继续保留。</p>
            <Button
              variant="secondary"
              disabled={Boolean(deletingMode)}
              onClick={onRemoveConfig}
            >
              {deletingMode === 'config' ? '正在移除' : '仅移除配置'}
            </Button>
          </section>
          <section className="danger">
            <div><TriangleAlert size={20} /></div>
            <h3>彻底删除企微表格</h3>
            <p>调用企微接口删除真实文档，然后移除 CRM 配置。该操作无法撤销，但已进入公海池的数据不会删除。</p>
            <label>
              <span>输入表格名称确认</span>
              <input
                value={confirmName}
                disabled={Boolean(deletingMode)}
                placeholder={source?.name || ''}
                onChange={(event) => setConfirmName(event.target.value)}
              />
            </label>
            <Button
              variant="danger"
              disabled={!nameMatched || Boolean(deletingMode)}
              onClick={onDeletePlatform}
            >
              {deletingMode === 'platform' ? '正在彻底删除' : '彻底删除企微表格'}
            </Button>
          </section>
        </div>
        <div className="modal-actions">
          <Button variant="secondary" disabled={Boolean(deletingMode)} onClick={onClose}>取消</Button>
        </div>
      </div>
    </Modal>
  )
}

function ManagedSheetLogsModal({ open, source, logs, onClose }) {
  if (!open) return null
  return (
    <Modal open={open} title={`${source?.name || '自建表格'} · 拉取记录`} onClose={onClose}>
      <div className="channel-source-logs">
        {logs.length === 0 && <div className="channel-source-empty">暂无拉取记录</div>}
        {logs.map((log) => (
          <div className="channel-source-log" key={log.id}>
            <div>
              <Badge tone={log.status === 'SUCCESS' ? 'success' : log.status === 'FAILED' ? 'warning' : 'info'}>
                {log.status === 'SUCCESS' ? '成功' : log.status === 'FAILED' ? '失败' : '运行中'}
              </Badge>
              <span>平台接口拉取</span>
            </div>
            <p>
              读取 {log.fetchedCount || 0}，新增 {log.createdCount || 0}，更新 {log.updatedCount || 0}，
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
