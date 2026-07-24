import { useEffect, useMemo, useState } from 'react'
import {
  Bot,
  Braces,
  Building2,
  CheckCircle2,
  Edit2,
  Eye,
  FileText,
  Gauge,
  Plus,
  RefreshCw,
  ServerCog,
  Sparkles,
  Trash2,
  Users,
  Wrench,
} from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Drawer,
  Field,
  PageHeader,
  SecretInput,
  useConfirmDialog,
} from '../../components'

const SCENE_OPTIONS = [
  { value: 'LEAD_ANALYZE', label: '线索分析' },
  { value: 'CHANNEL_ANALYZE', label: '渠道内容分析' },
  { value: 'CUSTOMER_PROFILE', label: '客户画像分析' },
  { value: 'OPPORTUNITY_ASSIST', label: '商机推进分析' },
  { value: 'GENERAL_ASSISTANT', label: '通用营销助手' },
]

const TRANSPORT_OPTIONS = [
  { value: 'SSE', label: 'SSE' },
  { value: 'STREAMABLE_HTTP', label: 'Streamable HTTP' },
  { value: 'STDIO', label: 'Stdio' },
]

const emptyAgentForm = {
  code: '',
  sceneCode: 'LEAD_ANALYZE',
  sceneName: '线索分析',
  name: '',
  description: '',
  systemPrompt: '',
  modelConfigId: '',
  modelProvider: 'OPENAI',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  maxIters: 8,
  extraConfigJson: '',
  remark: '',
  enabled: true,
}

const emptyMcpForm = {
  id: null,
  agentId: '',
  name: '',
  transportType: 'SSE',
  endpoint: '',
  command: '',
  argumentsJson: '',
  headersJson: '',
  enabled: true,
}

const emptySkillForm = {
  id: null,
  agentId: '',
  skillKey: '',
  name: '',
  content: '',
  enabled: true,
}

const emptyQuotaForm = {
  scope: 'USER',
  userId: '',
  departmentId: '',
  dailyTokenLimit: 100000,
  enabled: true,
  remark: '',
}

function sameId(first, second) {
  return String(first || '') === String(second || '')
}

function formatDateTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function sceneLabel(row) {
  if (!row) return '-'
  return row.sceneName || SCENE_OPTIONS.find((item) => item.value === row.sceneCode)?.label || row.sceneCode || '-'
}

function formatTokenLimit(value) {
  const number = Number(value || 0)
  if (!number) return '不限额'
  if (number >= 10000) return `${(number / 10000).toFixed(number % 10000 === 0 ? 0 : 1)}万`
  return String(number)
}

function quotaScopeLabel(scope) {
  const value = String(scope || '').toUpperCase()
  if (value === 'COMPANY' || value === 'ALL') return '全公司'
  if (value === 'DEPARTMENT') return '部门'
  return '用户'
}

function resolveDefaultAgentForm(models) {
  const defaultModel = (models || []).find((item) => item.defaultConfig) || (models || [])[0]
  if (!defaultModel) {
    return { ...emptyAgentForm }
  }
  return {
    ...emptyAgentForm,
    modelConfigId: defaultModel.id,
    modelProvider: defaultModel.provider || 'OPENAI',
    modelName: defaultModel.modelName || '',
    baseUrl: defaultModel.baseUrl || '',
    apiKey: '',
  }
}

function toAgentForm(row, models) {
  if (!row) {
    return resolveDefaultAgentForm(models)
  }
  return {
    ...emptyAgentForm,
    ...row,
    modelConfigId: row.modelConfigId || '',
    maxIters: row.maxIters || 8,
    enabled: row.enabled !== false,
    apiKey: '',
    extraConfigJson: row.extraConfigJson || '',
    remark: row.remark || '',
  }
}

function buildAgentPayload(form) {
  const scene = SCENE_OPTIONS.find((item) => item.value === form.sceneCode)
  return {
    id: form.id || undefined,
    code: form.code || undefined,
    sceneCode: form.sceneCode || undefined,
    sceneName: form.sceneName || scene?.label || undefined,
    name: form.name,
    description: form.description || undefined,
    systemPrompt: form.systemPrompt || undefined,
    modelConfigId: form.modelConfigId || undefined,
    modelProvider: form.modelConfigId ? undefined : form.modelProvider,
    modelName: form.modelConfigId ? undefined : form.modelName,
    baseUrl: form.modelConfigId ? undefined : form.baseUrl,
    apiKey: form.modelConfigId ? undefined : form.apiKey,
    maxIters: Number(form.maxIters) || 8,
    extraConfigJson: form.extraConfigJson || undefined,
    remark: form.remark || undefined,
    enabled: form.enabled !== false,
  }
}

export function AgentConfigPage({ can, notify }) {
  const canManage = can('crm:agent:manage')
  const [rows, setRows] = useState([])
  const [selected, setSelected] = useState(null)
  const [models, setModels] = useState([])
  const [mcps, setMcps] = useState([])
  const [skills, setSkills] = useState([])
  const [loading, setLoading] = useState(true)
  const [resourceLoading, setResourceLoading] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [agentForm, setAgentForm] = useState(null)
  const [mcpForm, setMcpForm] = useState(null)
  const [skillForm, setSkillForm] = useState(null)
  const [quotaOpen, setQuotaOpen] = useState(false)
  const [quotaLoading, setQuotaLoading] = useState(false)
  const [quotaOverview, setQuotaOverview] = useState(null)
  const [quotaForm, setQuotaForm] = useState(emptyQuotaForm)
  const { confirm, dialogProps } = useConfirmDialog()

  const enabledMcpCount = mcps.filter((item) => item.enabled).length
  const enabledSkillCount = skills.filter((item) => item.enabled).length
  const selectedModel = useMemo(() => (
    models.find((item) => sameId(item.id, selected?.modelConfigId))
  ), [models, selected])

  const quotaUsers = quotaOverview?.users || []
  const quotaDepartments = quotaOverview?.departments || []
  const quotaRows = quotaOverview?.quotas || []

  const loadResources = async (agentId) => {
    if (!agentId) {
      setMcps([])
      setSkills([])
      return
    }
    setResourceLoading(true)
    try {
      const [nextMcps, nextSkills] = await Promise.all([
        api.agent.mcps(agentId),
        api.agent.skills(agentId),
      ])
      setMcps(nextMcps || [])
      setSkills(nextSkills || [])
    } catch (err) {
      notify(err.message || '加载智能体资源失败', 'info')
    } finally {
      setResourceLoading(false)
    }
  }

  const load = async (keepId) => {
    setLoading(true)
    try {
      const [page, modelRows] = await Promise.all([
        api.agent.page({ pageNo: 1, pageSize: 100 }),
        api.modelConfig.list(),
      ])
      const nextRows = page?.records || []
      setRows(nextRows)
      setModels(modelRows || [])
      const nextSelected = nextRows.find((item) => sameId(item.id, keepId || selected?.id)) || nextRows[0] || null
      setSelected(nextSelected)
      if (detailOpen) {
        await loadResources(nextSelected?.id)
      }
    } catch (err) {
      notify(err.message || '加载智能体配置失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const selectAgent = async (row) => {
    setSelected(row)
    setDetailOpen(true)
    await loadResources(row.id)
  }

  const openAgentDrawer = (row) => {
    setAgentForm(toAgentForm(row, models))
  }

  const loadTokenQuota = async () => {
    setQuotaLoading(true)
    try {
      const data = await api.agent.tokenQuotaOverview()
      setQuotaOverview(data)
      setQuotaForm((current) => ({
        ...current,
        dailyTokenLimit: current.dailyTokenLimit || data?.defaultDailyTokenLimit || 100000,
      }))
    } catch (err) {
      notify(err.message || '加载 Token 额度失败', 'info')
    } finally {
      setQuotaLoading(false)
    }
  }

  const openQuotaDrawer = async () => {
    setQuotaOpen(true)
    await loadTokenQuota()
  }

  const saveAgent = async () => {
    if (!agentForm?.name?.trim()) {
      notify('智能体名称不能为空', 'info')
      return
    }
    if (!agentForm.modelConfigId && !agentForm.modelName?.trim()) {
      notify('请选择大模型配置，或填写模型标识', 'info')
      return
    }
    if (!agentForm.modelConfigId && !agentForm.id && !agentForm.apiKey?.trim()) {
      notify('新增手动模型智能体时必须填写 API Key', 'info')
      return
    }
    try {
      const response = await api.agent.save(buildAgentPayload(agentForm))
      notify('智能体配置已保存')
      setAgentForm(null)
      setDetailOpen(true)
      await load(response.id)
      await loadResources(response.id)
    } catch (err) {
      notify(err.message || '智能体配置保存失败', 'info')
    }
  }

  const removeMcp = async (row) => {
    const confirmed = await confirm({
      title: '删除 MCP 服务',
      description: '删除后该智能体运行时不会再挂载这个 MCP 服务。',
      target: row.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.agent.deleteMcp(row.id)
      notify('MCP 服务已删除')
      await loadResources(selected?.id)
    } catch (err) {
      notify(err.message || 'MCP 服务删除失败', 'info')
    }
  }

  const removeSkill = async (row) => {
    const confirmed = await confirm({
      title: '删除 Skill',
      description: '删除后该智能体运行时不会再挂载这个 Skill。',
      target: row.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.agent.deleteSkill(row.id)
      notify('Skill 已删除')
      await loadResources(selected?.id)
    } catch (err) {
      notify(err.message || 'Skill 删除失败', 'info')
    }
  }

  const toggleMcp = async (row) => {
    try {
      await api.agent.saveMcp({ ...row, enabled: !row.enabled })
      notify(row.enabled ? 'MCP 服务已停用' : 'MCP 服务已启用')
      await loadResources(selected?.id)
    } catch (err) {
      notify(err.message || 'MCP 服务状态更新失败', 'info')
    }
  }

  const toggleSkill = async (row) => {
    try {
      await api.agent.saveSkill({ ...row, enabled: !row.enabled })
      notify(row.enabled ? 'Skill 已停用' : 'Skill 已启用')
      await loadResources(selected?.id)
    } catch (err) {
      notify(err.message || 'Skill 状态更新失败', 'info')
    }
  }

  const saveMcpResource = async () => {
    if (!mcpForm?.name?.trim()) {
      notify('MCP 服务名称不能为空', 'info')
      return
    }
    if (mcpForm.transportType === 'STDIO' && !mcpForm.command?.trim()) {
      notify('Stdio 类型需要填写执行命令', 'info')
      return
    }
    if (mcpForm.transportType !== 'STDIO' && !mcpForm.endpoint?.trim()) {
      notify('MCP 服务地址不能为空', 'info')
      return
    }
    try {
      await api.agent.saveMcp(mcpForm)
      notify('MCP 服务已保存')
      setMcpForm(null)
      await loadResources(selected?.id)
    } catch (err) {
      notify(err.message || 'MCP 服务保存失败', 'info')
    }
  }

  const saveSkillResource = async () => {
    if (!skillForm?.name?.trim()) {
      notify('Skill 名称不能为空', 'info')
      return
    }
    try {
      await api.agent.saveSkill(skillForm)
      notify('Skill 已保存')
      setSkillForm(null)
      await loadResources(selected?.id)
    } catch (err) {
      notify(err.message || 'Skill 保存失败', 'info')
    }
  }

  const saveTokenQuota = async () => {
    const dailyTokenLimit = Number(quotaForm.dailyTokenLimit || 0)
    if (!Number.isFinite(dailyTokenLimit) || dailyTokenLimit < 0) {
      notify('Token额度不能小于0', 'info')
      return
    }
    if (quotaForm.scope === 'USER' && !quotaForm.userId) {
      notify('请选择用户', 'info')
      return
    }
    if (quotaForm.scope === 'DEPARTMENT' && !quotaForm.departmentId) {
      notify('请选择部门', 'info')
      return
    }
    try {
      const payload = {
        scope: quotaForm.scope,
        departmentId: quotaForm.scope === 'DEPARTMENT' ? Number(quotaForm.departmentId) : undefined,
        userIds: quotaForm.scope === 'USER' ? [Number(quotaForm.userId)] : [],
        dailyTokenLimit,
        enabled: quotaForm.enabled !== false,
        remark: quotaForm.remark || undefined,
      }
      const data = await api.agent.assignTokenQuota(payload)
      setQuotaOverview(data)
      notify('Token额度已保存')
    } catch (err) {
      notify(err.message || 'Token额度保存失败', 'info')
    }
  }

  const clearTokenQuota = async (row) => {
    const confirmed = await confirm({
      title: '恢复默认额度',
      description: '恢复后该用户将使用系统默认 Token 额度。',
      target: row.displayName || row.username || row.userId,
      confirmText: '确认恢复',
    })
    if (!confirmed) return
    try {
      const data = await api.agent.clearTokenQuota(row.userId)
      setQuotaOverview(data)
      notify('已恢复默认额度')
    } catch (err) {
      notify(err.message || '恢复默认额度失败', 'info')
    }
  }

  return (
    <div className="page agent-config-page">
      <PageHeader
        title="智能体配置"
        description="按业务场景维护提示词、Skills、MCP 服务和运行附加信息，运行时由 AgentRuntime 动态挂载"
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => load(selected?.id)}>刷新</Button>
            {canManage && <Button variant="secondary" icon={Gauge} onClick={openQuotaDrawer}>Token额度</Button>}
            {canManage && <Button icon={Plus} onClick={() => openAgentDrawer(null)}>新增智能体</Button>}
          </>
        )}
      />

      <div className="agent-config-layout">
        <Card className="agent-list-card table-card">
          <div className="table-toolbar">
            <div className="card-heading">
              <div>
                <h2><Bot size={17} />智能体列表</h2>
                <p>共 {rows.length} 个配置</p>
              </div>
            </div>
          </div>
          <div className="data-table-wrap">
            <table className="data-table agent-table">
              <thead>
                <tr>
                  <th>智能体</th>
                  <th>场景</th>
                  <th>模型</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr
                    className={sameId(row.id, selected?.id) ? 'selected-row' : ''}
                    key={row.id}
                    onClick={() => selectAgent(row)}
                  >
                    <td>
                      <strong><Sparkles size={15} />{row.name}</strong>
                      <small>{row.description || row.remark || '未填写说明'}</small>
                    </td>
                    <td>
                      <Badge tone="info">{sceneLabel(row)}</Badge>
                      <small>{row.sceneCode || '-'}</small>
                    </td>
                    <td>
                      <strong>{row.modelName || '-'}</strong>
                      <small>{row.modelProvider || '-'}</small>
                    </td>
                    <td>
                      <Badge dot tone={row.enabled ? 'success' : 'danger'}>{row.enabled ? '启用' : '停用'}</Badge>
                    </td>
                    <td>{formatDateTime(row.updatedAt)}</td>
                    <td>
                      <div className="agent-table-actions">
                        <button
                          className="icon-button"
                          title="查看详情"
                          onClick={(event) => {
                            event.stopPropagation()
                            selectAgent(row)
                          }}
                        >
                          <Eye size={17} />
                        </button>
                        <button
                          className="icon-button"
                          title="编辑配置"
                          disabled={!canManage}
                          onClick={(event) => {
                            event.stopPropagation()
                            openAgentDrawer(row)
                          }}
                        >
                          <Edit2 size={17} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!loading && !rows.length && (
              <div className="empty-table">
                <Bot size={26} />
                <b>暂无智能体配置</b>
                <span>新增后才能被线索分析等业务场景动态调用</span>
              </div>
            )}
            {loading && (
              <div className="empty-table">
                <RefreshCw size={26} />
                <b>正在加载</b>
                <span>读取数据库中的智能体配置</span>
              </div>
            )}
          </div>
        </Card>

      </div>

      <AgentDetailDrawer
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        selected={selected}
        selectedModel={selectedModel}
        mcps={mcps}
        skills={skills}
        enabledMcpCount={enabledMcpCount}
        enabledSkillCount={enabledSkillCount}
        resourceLoading={resourceLoading}
        canManage={canManage}
        onEditAgent={openAgentDrawer}
        onOpenMcp={(row) => setMcpForm(row ? { ...row } : { ...emptyMcpForm, agentId: selected?.id || '' })}
        onOpenSkill={(row) => setSkillForm(row ? { ...row } : { ...emptySkillForm, agentId: selected?.id || '' })}
        onToggleMcp={toggleMcp}
        onToggleSkill={toggleSkill}
        onRemoveMcp={removeMcp}
        onRemoveSkill={removeSkill}
      />
      <AgentDrawer
        open={Boolean(agentForm)}
        form={agentForm}
        models={models}
        canManage={canManage}
        onChange={setAgentForm}
        onSave={saveAgent}
        onClose={() => setAgentForm(null)}
      />
      <McpDrawer
        open={Boolean(mcpForm)}
        form={mcpForm}
        canManage={canManage}
        onChange={setMcpForm}
        onSave={saveMcpResource}
        onClose={() => setMcpForm(null)}
      />
      <SkillDrawer
        open={Boolean(skillForm)}
        form={skillForm}
        canManage={canManage}
        onChange={setSkillForm}
        onSave={saveSkillResource}
        onClose={() => setSkillForm(null)}
      />
      <TokenQuotaDrawer
        open={quotaOpen}
        loading={quotaLoading}
        overview={quotaOverview}
        form={quotaForm}
        users={quotaUsers}
        departments={quotaDepartments}
        rows={quotaRows}
        canManage={canManage}
        onChange={setQuotaForm}
        onSave={saveTokenQuota}
        onRefresh={loadTokenQuota}
        onClear={clearTokenQuota}
        onClose={() => setQuotaOpen(false)}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function AgentDetailDrawer({ open, onClose, selected, ...props }) {
  if (!selected) return null
  return (
    <Drawer open={open} size="wide" title="智能体详情" onClose={onClose}>
      <div className="agent-detail-drawer-body">
        <AgentDetailPanel selected={selected} {...props} />
      </div>
    </Drawer>
  )
}

function TokenQuotaDrawer({
  open,
  loading,
  overview,
  form,
  users,
  departments,
  rows,
  canManage,
  onChange,
  onSave,
  onRefresh,
  onClear,
  onClose,
}) {
  return (
    <Drawer
      open={open}
      size="wide"
      title="Token额度设置"
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>关闭</Button>
          <Button variant="secondary" disabled={loading} icon={RefreshCw} onClick={onRefresh}>刷新</Button>
          <Button disabled={!canManage || loading} onClick={onSave}>保存额度</Button>
        </>
      )}
    >
      <div className="agent-token-quota-layout">
        <Card className="agent-token-quota-form">
          <div className="card-heading">
            <div>
              <h2><Gauge size={17} />额度分配</h2>
              <p>最终额度直接绑定到用户，部门和全公司会批量覆盖目标用户额度</p>
            </div>
          </div>
          <div className="agent-quota-scope-grid">
            <button
              type="button"
              className={form.scope === 'USER' ? 'active' : ''}
              onClick={() => onChange({ ...form, scope: 'USER' })}
            >
              <Users size={17} />
              <span>用户</span>
            </button>
            <button
              type="button"
              className={form.scope === 'DEPARTMENT' ? 'active' : ''}
              onClick={() => onChange({ ...form, scope: 'DEPARTMENT' })}
            >
              <Building2 size={17} />
              <span>部门</span>
            </button>
            <button
              type="button"
              className={form.scope === 'COMPANY' ? 'active' : ''}
              onClick={() => onChange({ ...form, scope: 'COMPANY' })}
            >
              <Sparkles size={17} />
              <span>全公司</span>
            </button>
          </div>
          {form.scope === 'USER' && (
            <Field label="选择用户" required>
              <select value={form.userId || ''} onChange={(event) => onChange({ ...form, userId: event.target.value })}>
                <option value="">请选择用户</option>
                {users.map((user) => (
                  <option value={user.id} key={user.id}>
                    {user.displayName || user.username} / {user.departmentName || '未分配部门'}
                  </option>
                ))}
              </select>
            </Field>
          )}
          {form.scope === 'DEPARTMENT' && (
            <Field label="选择部门" required>
              <select value={form.departmentId || ''} onChange={(event) => onChange({ ...form, departmentId: event.target.value })}>
                <option value="">请选择部门</option>
                {departments.map((department) => (
                  <option value={department.id} key={department.id}>{department.name}</option>
                ))}
              </select>
            </Field>
          )}
          {form.scope === 'COMPANY' && (
            <div className="agent-quota-company-card">
              <Sparkles size={18} />
              <div>
                <b>全公司批量设置</b>
                <span>将覆盖当前租户下所有用户的 Token 额度</span>
              </div>
            </div>
          )}
          <Field label="每日 Token 额度" required hint="填写 0 表示不限额">
            <input
              type="number"
              min="0"
              value={form.dailyTokenLimit}
              onChange={(event) => onChange({ ...form, dailyTokenLimit: event.target.value })}
            />
          </Field>
          <Field label="备注">
            <textarea rows="3" value={form.remark || ''} onChange={(event) => onChange({ ...form, remark: event.target.value })} />
          </Field>
          <div className="agent-switch-line">
            <label>
              <input type="checkbox" checked={form.enabled !== false} onChange={(event) => onChange({ ...form, enabled: event.target.checked })} />
              <span>启用用户额度</span>
            </label>
          </div>
        </Card>

        <Card className="agent-token-quota-list">
          <div className="card-heading">
            <div>
              <h2><Users size={17} />用户额度</h2>
              <p>默认额度 {formatTokenLimit(overview?.defaultDailyTokenLimit)}，已单独绑定 {rows.length} 人</p>
            </div>
          </div>
          <div className="agent-token-quota-rows">
            {rows.map((row) => (
              <div className="agent-token-quota-row" key={row.id || row.userId}>
                <div>
                  <b>{row.displayName || row.username || row.userId}</b>
                  <span>{row.departmentName || '未分配部门'} · 来源：{quotaScopeLabel(row.assignScope)}</span>
                </div>
                <strong>{formatTokenLimit(row.dailyTokenLimit)}</strong>
                <Badge dot tone={row.enabled ? 'success' : 'danger'}>{row.enabled ? '启用' : '停用'}</Badge>
                <button className="icon-button" disabled={!canManage} title="恢复默认额度" onClick={() => onClear(row)}>
                  <Trash2 size={17} />
                </button>
              </div>
            ))}
            {!loading && !rows.length && (
              <div className="agent-resource-empty">暂无单独额度，当前使用系统默认额度</div>
            )}
            {loading && <div className="agent-resource-empty">正在加载 Token 额度…</div>}
          </div>
        </Card>
      </div>
    </Drawer>
  )
}

function AgentDetailPanel({
  selected,
  selectedModel,
  mcps,
  skills,
  enabledMcpCount,
  enabledSkillCount,
  resourceLoading,
  canManage,
  onEditAgent,
  onOpenMcp,
  onOpenSkill,
  onToggleMcp,
  onToggleSkill,
  onRemoveMcp,
  onRemoveSkill,
}) {
  if (!selected) {
    return (
      <Card className="agent-detail-card">
        <div className="empty-table">
          <Bot size={26} />
          <b>请选择智能体</b>
          <span>选择左侧配置后查看提示词、MCP 和 Skill</span>
        </div>
      </Card>
    )
  }

  return (
    <div className="agent-detail-column">
      <Card className="agent-overview-card" ai>
        <div className="card-heading">
          <div>
            <h2><Sparkles size={17} />{selected.name}</h2>
            <p>{selected.description || '未填写说明'}</p>
          </div>
          <Badge dot tone={selected.enabled ? 'success' : 'danger'}>{selected.enabled ? '启用' : '停用'}</Badge>
        </div>
        <div className="agent-metric-grid">
          <div>
            <span>业务场景</span>
            <b>{sceneLabel(selected)}</b>
          </div>
          <div>
            <span>模型配置</span>
            <b>{selectedModel?.name || selected.modelName || '-'}</b>
          </div>
          <div>
            <span>MCP 服务</span>
            <b>{enabledMcpCount}/{mcps.length}</b>
          </div>
          <div>
            <span>Skills</span>
            <b>{enabledSkillCount}/{skills.length}</b>
          </div>
        </div>
        <div className="agent-overview-actions">
          <Button variant="secondary" disabled={!canManage} icon={Edit2} onClick={() => onEditAgent(selected)}>编辑智能体</Button>
          <Button disabled={!canManage} icon={ServerCog} onClick={() => onOpenMcp(null)}>新增 MCP</Button>
          <Button disabled={!canManage} icon={Wrench} onClick={() => onOpenSkill(null)}>新增 Skill</Button>
        </div>
      </Card>

      <div className="agent-detail-grid">
        <Card className="agent-prompt-card">
          <div className="card-heading">
            <div>
              <h2><FileText size={17} />场景提示词</h2>
              <p>运行时会叠加基础提示词和接口注入提示词</p>
            </div>
          </div>
          <pre>{selected.systemPrompt || '未配置提示词'}</pre>
        </Card>
        <Card className="agent-extra-card">
          <div className="card-heading">
            <div>
              <h2><Braces size={17} />附加信息</h2>
              <p>用于后续扩展运行参数、策略和场景元数据</p>
            </div>
          </div>
          <pre>{selected.extraConfigJson || selected.remark || '未配置附加信息'}</pre>
        </Card>
      </div>

      <div className="agent-resource-grid">
        <ResourceCard
          title="MCP 服务"
          icon={ServerCog}
          loading={resourceLoading}
          rows={mcps}
          emptyText="暂无 MCP 服务"
          onAdd={() => onOpenMcp(null)}
          canManage={canManage}
          renderRow={(row) => (
            <div className="agent-resource-row" key={row.id}>
              <span><ServerCog size={16} /></span>
              <div>
                <b>{row.name}</b>
                <small>{row.transportType} · {row.endpoint || row.command || '-'}</small>
              </div>
              <Badge dot tone={row.enabled ? 'success' : 'danger'}>{row.enabled ? '启用' : '停用'}</Badge>
              <button className="icon-button" disabled={!canManage} onClick={() => onToggleMcp(row)}><CheckCircle2 size={17} /></button>
              <button className="icon-button" disabled={!canManage} onClick={() => onOpenMcp(row)}><Edit2 size={17} /></button>
              <button className="icon-button" disabled={!canManage} onClick={() => onRemoveMcp(row)}><Trash2 size={17} /></button>
            </div>
          )}
        />
        <ResourceCard
          title="Skills"
          icon={Wrench}
          loading={resourceLoading}
          rows={skills}
          emptyText="暂无 Skill"
          onAdd={() => onOpenSkill(null)}
          canManage={canManage}
          renderRow={(row) => (
            <div className="agent-resource-row" key={row.id}>
              <span><Wrench size={16} /></span>
              <div>
                <b>{row.name}</b>
                <small>{row.content ? row.content.slice(0, 48) : '未填写内容'}</small>
              </div>
              <Badge dot tone={row.enabled ? 'success' : 'danger'}>{row.enabled ? '启用' : '停用'}</Badge>
              <button className="icon-button" disabled={!canManage} onClick={() => onToggleSkill(row)}><CheckCircle2 size={17} /></button>
              <button className="icon-button" disabled={!canManage} onClick={() => onOpenSkill(row)}><Edit2 size={17} /></button>
              <button className="icon-button" disabled={!canManage} onClick={() => onRemoveSkill(row)}><Trash2 size={17} /></button>
            </div>
          )}
        />
      </div>
    </div>
  )
}

function ResourceCard({ title, icon: Icon, rows, loading, emptyText, onAdd, canManage, renderRow }) {
  return (
    <Card className="agent-resource-card">
      <div className="card-heading">
        <div>
          <h2><Icon size={17} />{title}</h2>
          <p>共 {rows.length} 条配置</p>
        </div>
        {canManage && <button type="button" onClick={onAdd}><Plus size={15} />新增</button>}
      </div>
      <div className="agent-resource-list">
        {rows.map(renderRow)}
        {!loading && !rows.length && <div className="agent-resource-empty">{emptyText}</div>}
        {loading && <div className="agent-resource-empty">正在加载资源配置…</div>}
      </div>
    </Card>
  )
}

function AgentDrawer({ open, form, models, canManage, onChange, onSave, onClose }) {
  if (!form) return null
  const selectedScene = SCENE_OPTIONS.find((item) => item.value === form.sceneCode)

  const changeScene = (value) => {
    const scene = SCENE_OPTIONS.find((item) => item.value === value)
    onChange({
      ...form,
      sceneCode: value,
      sceneName: scene ? scene.label : form.sceneName,
    })
  }

  const changeModel = (value) => {
    const model = models.find((item) => sameId(item.id, value))
    onChange({
      ...form,
      modelConfigId: value,
      modelProvider: model?.provider || form.modelProvider,
      modelName: model?.modelName || form.modelName,
      baseUrl: model?.baseUrl || form.baseUrl,
      apiKey: '',
    })
  }

  return (
    <Drawer
      open={open}
      size="wide"
      title={form.id ? '编辑智能体配置' : '新增智能体配置'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button disabled={!canManage} onClick={onSave}>保存</Button>
        </>
      )}
    >
      <div className="agent-form-layout">
        <Card className="agent-form-basic">
          <div className="card-heading">
            <div>
              <h2><Bot size={17} />基础配置</h2>
            </div>
          </div>
          <Field label="业务场景" required>
            <select value={form.sceneCode || ''} onChange={(event) => changeScene(event.target.value)}>
              {form.sceneCode && !SCENE_OPTIONS.some((item) => item.value === form.sceneCode) && (
                <option value={form.sceneCode}>{form.sceneName || form.sceneCode}</option>
              )}
              {SCENE_OPTIONS.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
            </select>
          </Field>
          <Field label="场景名称">
            <input value={form.sceneName || selectedScene?.label || ''} onChange={(event) => onChange({ ...form, sceneName: event.target.value })} />
          </Field>
          <Field label="场景标识">
            <input value={form.sceneCode || ''} onChange={(event) => onChange({ ...form, sceneCode: event.target.value })} />
          </Field>
          <Field label="智能体名称" required>
            <input value={form.name || ''} onChange={(event) => onChange({ ...form, name: event.target.value })} />
          </Field>
          <Field label="说明">
            <textarea rows="3" value={form.description || ''} onChange={(event) => onChange({ ...form, description: event.target.value })} />
          </Field>
          <div className="agent-switch-line">
            <label>
              <input type="checkbox" checked={form.enabled !== false} onChange={(event) => onChange({ ...form, enabled: event.target.checked })} />
              <span>启用智能体</span>
            </label>
          </div>
        </Card>

        <Card className="agent-form-runtime">
          <div className="card-heading">
            <div>
              <h2><ServerCog size={17} />运行配置</h2>
            </div>
          </div>
          <Field label="大模型配置">
            <select value={form.modelConfigId || ''} onChange={(event) => changeModel(event.target.value)}>
              <option value="">不使用模型配置，手动填写</option>
              {models.map((item) => (
                <option value={item.id} key={item.id}>{item.name} / {item.modelName}</option>
              ))}
            </select>
          </Field>
          {!form.modelConfigId && (
            <>
              <Field label="模型供应商">
                <select value={form.modelProvider || 'OPENAI'} onChange={(event) => onChange({ ...form, modelProvider: event.target.value })}>
                  <option value="OPENAI">OpenAI 兼容</option>
                  <option value="DASHSCOPE">通义千问</option>
                  <option value="DEEPSEEK">DeepSeek</option>
                  <option value="CUSTOM">自定义</option>
                </select>
              </Field>
              <Field label="模型标识" required>
                <input value={form.modelName || ''} onChange={(event) => onChange({ ...form, modelName: event.target.value })} />
              </Field>
              <Field label="Base URL">
                <input value={form.baseUrl || ''} onChange={(event) => onChange({ ...form, baseUrl: event.target.value })} />
              </Field>
              <Field label="API Key" required hint={form.id ? '留空表示不修改原密钥' : ''}>
                <SecretInput value={form.apiKey || ''} onChange={(event) => onChange({ ...form, apiKey: event.target.value })} />
              </Field>
            </>
          )}
          <Field label="最大迭代次数">
            <input type="number" min="1" max="50" value={form.maxIters || 8} onChange={(event) => onChange({ ...form, maxIters: event.target.value })} />
          </Field>
          <Field label="备注">
            <textarea rows="3" value={form.remark || ''} onChange={(event) => onChange({ ...form, remark: event.target.value })} />
          </Field>
        </Card>

        <Card className="agent-form-prompt">
          <div className="card-heading">
            <div>
              <h2><FileText size={17} />提示词与附加信息</h2>
            </div>
          </div>
          <Field label="系统提示词">
            <textarea rows="12" value={form.systemPrompt || ''} onChange={(event) => onChange({ ...form, systemPrompt: event.target.value })} />
          </Field>
          <Field label="附加 JSON">
            <textarea rows="8" value={form.extraConfigJson || ''} onChange={(event) => onChange({ ...form, extraConfigJson: event.target.value })} />
          </Field>
        </Card>
      </div>
    </Drawer>
  )
}

function McpDrawer({ open, form, canManage, onChange, onSave, onClose }) {
  if (!form) return null
  return (
    <Drawer
      open={open}
      title={form.id ? '编辑 MCP 服务' : '新增 MCP 服务'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button disabled={!canManage} onClick={onSave}>保存</Button>
        </>
      )}
    >
      <Field label="服务名称" required>
        <input value={form.name || ''} onChange={(event) => onChange({ ...form, name: event.target.value })} />
      </Field>
      <Field label="传输类型" required>
        <select value={form.transportType || 'SSE'} onChange={(event) => onChange({ ...form, transportType: event.target.value })}>
          {TRANSPORT_OPTIONS.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
        </select>
      </Field>
      {form.transportType !== 'STDIO' && (
        <Field label="服务地址" required>
          <input value={form.endpoint || ''} onChange={(event) => onChange({ ...form, endpoint: event.target.value })} />
        </Field>
      )}
      {form.transportType === 'STDIO' && (
        <>
          <Field label="执行命令" required>
            <input value={form.command || ''} onChange={(event) => onChange({ ...form, command: event.target.value })} />
          </Field>
          <Field label="命令参数 JSON">
            <textarea rows="4" value={form.argumentsJson || ''} onChange={(event) => onChange({ ...form, argumentsJson: event.target.value })} />
          </Field>
        </>
      )}
      <Field label="请求头 JSON">
        <textarea rows="4" value={form.headersJson || ''} onChange={(event) => onChange({ ...form, headersJson: event.target.value })} />
      </Field>
      <div className="agent-switch-line">
        <label>
          <input type="checkbox" checked={form.enabled !== false} onChange={(event) => onChange({ ...form, enabled: event.target.checked })} />
          <span>启用 MCP 服务</span>
        </label>
      </div>
    </Drawer>
  )
}

function SkillDrawer({ open, form, canManage, onChange, onSave, onClose }) {
  if (!form) return null
  return (
    <Drawer
      open={open}
      title={form.id ? '编辑 Skill' : '新增 Skill'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button disabled={!canManage} onClick={onSave}>保存</Button>
        </>
      )}
    >
      <Field label="Skill 名称" required>
        <input value={form.name || ''} onChange={(event) => onChange({ ...form, name: event.target.value })} />
      </Field>
      <Field label="Skill 内容">
        <textarea rows="14" value={form.content || ''} onChange={(event) => onChange({ ...form, content: event.target.value })} />
      </Field>
      <div className="agent-switch-line">
        <label>
          <input type="checkbox" checked={form.enabled !== false} onChange={(event) => onChange({ ...form, enabled: event.target.checked })} />
          <span>启用 Skill</span>
        </label>
      </div>
    </Drawer>
  )
}
