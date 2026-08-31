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
  Layers3,
  BrainCircuit,
  Plus,
  RefreshCw,
  ServerCog,
  Settings2,
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

const WORKFLOW_OPTIONS = [
  { value: 'STANDARD_AGENT', label: '标准智能体（自由对话与工具调用）' },
  { value: 'LEAD_ANALYSIS', label: '线索分析编排' },
]

const BUILTIN_TOOL_OPTIONS = [
  { value: 'customer_web_search', label: '客户公开信息检索' },
  { value: 'knowledge_hybrid_search', label: '知识库混合检索' },
  { value: 'crm_lead_page', label: '查询线索列表' },
  { value: 'crm_lead_detail', label: '查询线索详情' },
  { value: 'crm_customer_page', label: '查询客户列表' },
  { value: 'crm_customer_detail', label: '查询客户详情' },
  { value: 'crm_followup_page', label: '查询跟进列表' },
  { value: 'crm_followup_detail', label: '查询跟进详情' },
  { value: 'crm_opportunity_page', label: '查询商机列表' },
  { value: 'crm_opportunity_detail', label: '查询商机详情' },
  { value: 'generate_report', label: '生成Word、PDF或HTML报告' },
]

const TRANSPORT_OPTIONS = [
  { value: 'SSE', label: 'SSE' },
  { value: 'STREAMABLE_HTTP', label: 'Streamable HTTP' },
  { value: 'STDIO', label: 'Stdio' },
]

const emptyAgentForm = {
  code: '',
  sceneCode: '',
  sceneName: '',
  name: '',
  description: '',
  systemPrompt: '',
  modelConfigId: '',
  modelProvider: 'OPENAI',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  maxIters: 8,
  defaultForScene: false,
  scenePriority: 0,
  workflowCode: 'STANDARD_AGENT',
  builtinTools: [],
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

const emptySceneForm = {
  id: null,
  code: '',
  name: '',
  description: '',
  sortNo: 0,
  enabled: true,
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
  return row.sceneName || row.sceneCode || '-'
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

function resolveDefaultAgentForm(models, scenes) {
  const defaultModel = (models || []).find((item) => item.defaultConfig) || (models || [])[0]
  const defaultScene = (scenes || []).find((item) => item.enabled !== false)
  if (!defaultModel) {
    return {
      ...emptyAgentForm,
      sceneCode: defaultScene?.code || '',
      sceneName: defaultScene?.name || '',
    }
  }
  return {
    ...emptyAgentForm,
    sceneCode: defaultScene?.code || '',
    sceneName: defaultScene?.name || '',
    modelConfigId: defaultModel.id,
    modelProvider: defaultModel.provider || 'OPENAI',
    modelName: defaultModel.modelName || '',
    baseUrl: defaultModel.baseUrl || '',
    apiKey: '',
  }
}

function parseAgentExtraConfig(value) {
  if (!value?.trim()) return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function resolveBuiltinTools(config) {
  const configured = config.builtinTools ?? config.builtin_tools
  if (configured === undefined || configured === '*') {
    return BUILTIN_TOOL_OPTIONS.map((item) => item.value)
  }
  if (Array.isArray(configured)) {
    return configured.map(String)
  }
  return []
}

function toAgentForm(row, models, scenes) {
  if (!row) {
    return resolveDefaultAgentForm(models, scenes)
  }
  const config = parseAgentExtraConfig(row.extraConfigJson || '')
  return {
    ...emptyAgentForm,
    ...row,
    modelConfigId: row.modelConfigId || '',
    maxIters: row.maxIters || 8,
    enabled: row.enabled !== false,
    apiKey: '',
    extraConfigJson: row.extraConfigJson || '',
    defaultForScene: config.defaultForScene === true || config.default_for_scene === true,
    scenePriority: Number(config.scenePriority ?? config.scene_priority ?? 0),
    workflowCode: config.workflowCode || config.workflow_code || (row.sceneCode === 'LEAD_ANALYZE' ? 'LEAD_ANALYSIS' : 'STANDARD_AGENT'),
    builtinTools: resolveBuiltinTools(config),
    remark: row.remark || '',
  }
}

function buildAgentExtraConfig(form) {
  const config = parseAgentExtraConfig(form.extraConfigJson || '')
  delete config.default_for_scene
  delete config.scene_priority
  delete config.workflow_code
  delete config.builtin_tools
  config.defaultForScene = form.defaultForScene === true
  config.scenePriority = Number(form.scenePriority) || 0
  config.workflowCode = form.workflowCode || 'STANDARD_AGENT'
  config.builtinTools = Array.isArray(form.builtinTools) ? form.builtinTools : []
  return JSON.stringify(config, null, 2)
}

function buildAgentPayload(form) {
  return {
    id: form.id || undefined,
    code: form.code || undefined,
    sceneCode: form.sceneCode || undefined,
    sceneName: form.sceneName || undefined,
    name: form.name,
    description: form.description || undefined,
    systemPrompt: form.systemPrompt || undefined,
    modelConfigId: form.modelConfigId || undefined,
    modelProvider: form.modelConfigId ? undefined : form.modelProvider,
    modelName: form.modelConfigId ? undefined : form.modelName,
    baseUrl: form.modelConfigId ? undefined : form.baseUrl,
    apiKey: form.modelConfigId ? undefined : form.apiKey,
    maxIters: Number(form.maxIters) || 8,
    extraConfigJson: buildAgentExtraConfig(form),
    remark: form.remark || undefined,
    enabled: form.enabled !== false,
  }
}

export function AgentConfigPage({ can, notify }) {
  const canManage = can('crm:agent:manage')
  const [rows, setRows] = useState([])
  const [selected, setSelected] = useState(null)
  const [models, setModels] = useState([])
  const [scenes, setScenes] = useState([])
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
  const [sceneOpen, setSceneOpen] = useState(false)
  const [sceneForm, setSceneForm] = useState(emptySceneForm)
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
      const [page, modelRows, sceneRows] = await Promise.all([
        api.agent.page({ pageNo: 1, pageSize: 100 }),
        api.modelConfig.list(),
        api.agent.scenes(),
      ])
      const nextRows = page?.records || []
      setRows(nextRows)
      setModels(modelRows || [])
      setScenes(sceneRows || [])
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
    setAgentForm(toAgentForm(row, models, scenes))
  }

  const saveScene = async () => {
    if (!sceneForm.name?.trim()) {
      notify('场景名称不能为空', 'info')
      return
    }
    try {
      const selectAfterSave = Boolean(agentForm) && !sceneForm.id && !sceneForm.code
      const saved = await api.agent.saveScene(sceneForm)
      const nextScenes = await api.agent.scenes()
      setScenes(nextScenes || [])
      setSceneForm(emptySceneForm)
      setAgentForm((current) => {
        if (!current || !selectAfterSave) return current
        return { ...current, sceneCode: saved.code, sceneName: saved.name }
      })
      notify('场景已保存')
    } catch (err) {
      notify(err.message || '场景保存失败', 'info')
    }
  }

  const removeScene = async (row) => {
    const confirmed = await confirm({
      title: '删除业务场景',
      description: '只有未关联智能体的场景才能删除。',
      target: row.name,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.agent.deleteScene(row.id)
      setScenes(await api.agent.scenes())
      notify('场景已删除')
    } catch (err) {
      notify(err.message || '场景删除失败', 'info')
    }
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
    if (!agentForm.sceneCode?.trim()) {
      notify('业务场景标识不能为空，可输入任意自定义标识', 'info')
      return
    }
    if (agentForm.extraConfigJson?.trim()) {
      try {
        const value = JSON.parse(agentForm.extraConfigJson)
        if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('附加配置必须是对象')
      } catch {
        notify('附加 JSON 格式不正确', 'info')
        return
      }
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
            {canManage && <Button variant="secondary" icon={Layers3} onClick={() => setSceneOpen(true)}>场景管理</Button>}
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
        scenes={scenes}
        canManage={canManage}
        onChange={setAgentForm}
        onSave={saveAgent}
        onManageScenes={() => setSceneOpen(true)}
        onClose={() => setAgentForm(null)}
      />
      <SceneManagerDrawer
        open={sceneOpen}
        scenes={scenes}
        form={sceneForm}
        canManage={canManage}
        onChange={setSceneForm}
        onEdit={(row) => setSceneForm({
          id: row.id || null,
          code: row.code || '',
          name: row.name || '',
          description: row.description || '',
          sortNo: row.sortNo || 0,
          enabled: row.enabled !== false,
        })}
        onReset={() => setSceneForm(emptySceneForm)}
        onSave={saveScene}
        onDelete={removeScene}
        onClose={() => {
          setSceneOpen(false)
          setSceneForm(emptySceneForm)
        }}
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

function SceneManagerDrawer({ open, scenes, form, canManage, onChange, onEdit, onReset, onSave, onDelete, onClose }) {
  return (
    <Drawer
      open={open}
      size="wide"
      title="业务场景管理"
      onClose={onClose}
      footer={<Button variant="secondary" onClick={onClose}>关闭</Button>}
    >
      <div className="agent-scene-manager">
        <Card className="agent-scene-editor">
          <div className="agent-section-heading">
            <span><Layers3 size={18} /></span>
            <div>
              <h3>{form.id || form.code ? '编辑场景' : '新增场景'}</h3>
              <p>场景标识由系统生成，智能体只需要选择场景。</p>
            </div>
          </div>
          <Field label="场景名称" required>
            <input value={form.name || ''} placeholder="例如：线索用户画像" onChange={(event) => onChange({ ...form, name: event.target.value })} />
          </Field>
          <Field label="场景说明">
            <textarea rows="4" value={form.description || ''} placeholder="说明这个场景解决什么业务问题" onChange={(event) => onChange({ ...form, description: event.target.value })} />
          </Field>
          <Field label="展示顺序">
            <input type="number" min="0" value={form.sortNo ?? 0} onChange={(event) => onChange({ ...form, sortNo: event.target.value })} />
          </Field>
          <div className="agent-switch-line">
            <label>
              <input type="checkbox" checked={form.enabled !== false} onChange={(event) => onChange({ ...form, enabled: event.target.checked })} />
              <span>启用该场景</span>
            </label>
          </div>
          <div className="agent-scene-editor-actions">
            {(form.id || form.code) && <Button variant="secondary" onClick={onReset}>取消编辑</Button>}
            <Button disabled={!canManage} onClick={onSave}>{form.id || form.code ? '保存修改' : '新增场景'}</Button>
          </div>
        </Card>

        <div className="agent-scene-catalog">
          <div className="agent-scene-catalog-head">
            <div>
              <h3>场景目录</h3>
              <p>共 {scenes.length} 个场景，均来自当前租户的真实配置。</p>
            </div>
          </div>
          <div className="agent-scene-grid">
            {scenes.map((row, index) => (
              <article className={`agent-scene-card tone-${(index % 4) + 1}`} key={row.id || row.code}>
                <div className="agent-scene-card-top">
                  <span className="agent-scene-mark"><Layers3 size={18} /></span>
                  <Badge dot tone={row.enabled ? 'success' : 'danger'}>{row.enabled ? '启用' : '停用'}</Badge>
                </div>
                <h4>{row.name}</h4>
                <p>{row.description || '暂未填写场景说明'}</p>
                <div className="agent-scene-meta">
                  <span><b>{row.agentCount || 0}</b> 个智能体</span>
                  <span>{row.managed ? '自定义场景' : '现有场景'}</span>
                </div>
                <div className="agent-scene-actions">
                  <Button variant="secondary" icon={Edit2} onClick={() => onEdit(row)}>{row.managed ? '编辑' : '纳入管理'}</Button>
                  {row.managed && Number(row.agentCount || 0) === 0 && (
                    <button className="icon-button danger" title="删除场景" onClick={() => onDelete(row)}><Trash2 size={16} /></button>
                  )}
                </div>
              </article>
            ))}
            {!scenes.length && (
              <div className="agent-scene-empty">
                <Layers3 size={28} />
                <b>还没有业务场景</b>
                <span>先在左侧新增一个场景，再创建智能体。</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </Drawer>
  )
}

function AgentDrawer({ open, form, models, scenes, canManage, onChange, onSave, onManageScenes, onClose }) {
  const [activeSection, setActiveSection] = useState('basic')

  useEffect(() => {
    if (open) setActiveSection('basic')
  }, [open, form?.id])

  if (!form) return null

  const changeScene = (value) => {
    const scene = scenes.find((item) => item.code === value)
    onChange({ ...form, sceneCode: value, sceneName: scene?.name || '' })
  }

  const toggleBuiltinTool = (value) => {
    const selected = new Set(form.builtinTools || [])
    if (selected.has(value)) selected.delete(value)
    else selected.add(value)
    onChange({ ...form, builtinTools: Array.from(selected) })
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

  const sections = [
    { key: 'basic', label: '基本信息', icon: Bot },
    { key: 'runtime', label: '模型与运行', icon: ServerCog },
    { key: 'prompt', label: '提示词', icon: FileText },
    { key: 'tools', label: '能力挂载', icon: Settings2 },
  ]

  return (
    <Drawer
      open={open}
      size="wide"
      title={form.id ? '编辑智能体' : '新增智能体'}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button disabled={!canManage} onClick={onSave}>保存智能体</Button>
        </>
      )}
    >
      <div className="agent-builder">
        <div className="agent-builder-hero">
          <span className="agent-builder-logo"><BrainCircuit size={24} /></span>
          <div>
            <h3>{form.name || '配置一个新的业务智能体'}</h3>
            <p>选择场景和模型，设置提示词，再按需挂载工具、Skill 与 MCP。</p>
          </div>
          <Badge tone={form.enabled !== false ? 'success' : 'neutral'}>{form.enabled !== false ? '启用' : '停用'}</Badge>
        </div>

        <nav className="agent-builder-tabs">
          {sections.map((section, index) => {
            const Icon = section.icon
            return (
              <button className={activeSection === section.key ? 'active' : ''} key={section.key} onClick={() => setActiveSection(section.key)}>
                <i>{index + 1}</i>
                <Icon size={17} />
                <span>{section.label}</span>
              </button>
            )
          })}
        </nav>

        <div className="agent-builder-panel">
          {activeSection === 'basic' && (
            <div className="agent-builder-section">
              <div className="agent-section-heading">
                <span><Bot size={18} /></span>
                <div><h3>基本信息</h3><p>定义智能体服务的业务场景和展示信息。</p></div>
              </div>
              <div className="agent-builder-grid">
                <Field label="业务场景" required hint="场景来自场景管理，可随时新增，不受系统枚举限制">
                  <div className="agent-scene-select-line">
                    <select value={form.sceneCode || ''} onChange={(event) => changeScene(event.target.value)}>
                      <option value="">请选择业务场景</option>
                      {scenes.filter((item) => item.enabled !== false || item.code === form.sceneCode).map((item) => (
                        <option value={item.code} key={item.code}>{item.name}</option>
                      ))}
                    </select>
                    <Button variant="secondary" icon={Plus} onClick={onManageScenes}>新增场景</Button>
                  </div>
                </Field>
                <Field label="智能体名称" required>
                  <input value={form.name || ''} placeholder="例如：线索用户画像助手" onChange={(event) => onChange({ ...form, name: event.target.value })} />
                </Field>
                <Field label="功能说明" className="agent-builder-wide">
                  <textarea rows="4" value={form.description || ''} placeholder="告诉使用者这个智能体能解决什么问题" onChange={(event) => onChange({ ...form, description: event.target.value })} />
                </Field>
                <div className="agent-choice-card">
                  <label>
                    <input type="checkbox" checked={form.enabled !== false} onChange={(event) => onChange({ ...form, enabled: event.target.checked })} />
                    <span><b>启用智能体</b><small>启用后会出现在智能体工作台</small></span>
                  </label>
                </div>
                <div className="agent-choice-card">
                  <label>
                    <input type="checkbox" checked={form.defaultForScene === true} onChange={(event) => onChange({ ...form, defaultForScene: event.target.checked })} />
                    <span><b>场景默认智能体</b><small>业务入口未指定智能体时优先使用</small></span>
                  </label>
                </div>
                <Field label="路由优先级" hint="同场景存在多个智能体时，数值越大优先级越高">
                  <input type="number" value={form.scenePriority ?? 0} onChange={(event) => onChange({ ...form, scenePriority: event.target.value })} />
                </Field>
              </div>
            </div>
          )}

          {activeSection === 'runtime' && (
            <div className="agent-builder-section">
              <div className="agent-section-heading">
                <span><ServerCog size={18} /></span>
                <div><h3>模型与运行</h3><p>选择模型配置并控制单次运行边界。</p></div>
              </div>
              <div className="agent-builder-grid">
                <Field label="大模型配置" className="agent-builder-wide">
                  <select value={form.modelConfigId || ''} onChange={(event) => changeModel(event.target.value)}>
                    <option value="">不使用模型配置，手动填写</option>
                    {models.map((item) => <option value={item.id} key={item.id}>{item.name} / {item.modelName}</option>)}
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
                <Field label="最大模型调用轮次" hint="默认 8 次，防止工具循环失控">
                  <input type="number" min="1" max="50" value={form.maxIters || 8} onChange={(event) => onChange({ ...form, maxIters: event.target.value })} />
                </Field>
                <Field label="运行方式">
                  <select value={form.workflowCode || 'STANDARD_AGENT'} onChange={(event) => onChange({ ...form, workflowCode: event.target.value })}>
                    {WORKFLOW_OPTIONS.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}
                  </select>
                </Field>
                <Field label="备注" className="agent-builder-wide">
                  <textarea rows="3" value={form.remark || ''} onChange={(event) => onChange({ ...form, remark: event.target.value })} />
                </Field>
              </div>
            </div>
          )}

          {activeSection === 'prompt' && (
            <div className="agent-builder-section">
              <div className="agent-section-heading">
                <span><FileText size={18} /></span>
                <div><h3>系统提示词</h3><p>描述身份、目标、边界和输出要求，运行时仅加载当前智能体的提示词。</p></div>
              </div>
              <Field label="系统提示词">
                <textarea className="agent-prompt-editor" rows="19" value={form.systemPrompt || ''} placeholder="输入智能体系统提示词…" onChange={(event) => onChange({ ...form, systemPrompt: event.target.value })} />
              </Field>
              <details className="agent-advanced-config">
                <summary>高级运行参数</summary>
                <Field label="附加 JSON" hint="用于结构化输出 Schema 等高级配置；普通智能体可以留空">
                  <textarea rows="8" value={form.extraConfigJson || ''} onChange={(event) => onChange({ ...form, extraConfigJson: event.target.value })} />
                </Field>
              </details>
            </div>
          )}

          {activeSection === 'tools' && (
            <div className="agent-builder-section">
              <div className="agent-section-heading">
                <span><Settings2 size={18} /></span>
                <div><h3>能力挂载</h3><p>为当前智能体选择内置工具，运行时仍会校验用户权限。</p></div>
              </div>
              <div className="agent-tool-options polished">
                {BUILTIN_TOOL_OPTIONS.map((item) => (
                  <label className={(form.builtinTools || []).includes(item.value) ? 'selected' : ''} key={item.value}>
                    <input type="checkbox" checked={(form.builtinTools || []).includes(item.value)} onChange={() => toggleBuiltinTool(item.value)} />
                    <span>{item.label}</span>
                  </label>
                ))}
              </div>
              <div className="agent-capability-note">
                <Wrench size={18} />
                <div><b>Skill 与 MCP</b><span>先保存智能体，再从详情抽屉中挂载 Skill 和 MCP 服务。</span></div>
              </div>
            </div>
          )}
        </div>
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
