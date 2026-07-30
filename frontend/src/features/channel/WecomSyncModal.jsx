import { useEffect, useMemo, useState } from 'react'
import { CheckCircle2, RefreshCw, UsersRound } from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Field, Modal, SecretInput, Select } from '../../components'
import { ownerOptionLabel, useOwnerOptions } from '../../hooks/useOwnerOptions'

const emptyConfig = {
  name: '',
  corpId: '',
  corpSecret: '',
  enabled: true,
  syncIntervalMinutes: 10,
  defaultOwnerId: '',
}

const statusText = {
  PENDING: '等待执行',
  RUNNING: '同步中',
  SUCCESS: '同步成功',
  FAILED: '同步失败',
  SKIPPED: '已跳过',
}

const statusTone = {
  PENDING: 'warning',
  RUNNING: 'info',
  SUCCESS: 'success',
  FAILED: 'danger',
  SKIPPED: 'neutral',
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function isRunning(task) {
  return task?.status === 'PENDING' || task?.status === 'RUNNING'
}

export function WecomSyncModal({
  open,
  canManage,
  canSync,
  onClose,
  notify,
  onSynced,
}) {
  const ownerOptions = useOwnerOptions(notify)
  const [config, setConfig] = useState(null)
  const [form, setForm] = useState(emptyConfig)
  const [bindings, setBindings] = useState([])
  const [task, setTask] = useState(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const crmOwnerOptions = useMemo(() => [
    { value: '', label: '暂不分配，进入公共渠道池' },
    ...ownerOptions.map((item) => ({
      value: String(item.id),
      label: ownerOptionLabel(item),
      description: item.username,
    })),
  ], [ownerOptions])

  const load = async () => {
    setLoading(true)
    try {
      const nextConfig = await api.wecom.configDetail()
      setConfig(nextConfig || null)
      setForm(nextConfig ? {
        ...emptyConfig,
        ...nextConfig,
        corpSecret: '',
        defaultOwnerId: nextConfig.defaultOwnerId ? String(nextConfig.defaultOwnerId) : '',
      } : emptyConfig)
      if (nextConfig?.id) {
        const [nextBindings, nextTask] = await Promise.all([
          api.wecom.bindingList(nextConfig.id),
          api.wecom.syncLatest(nextConfig.id),
        ])
        setBindings(nextBindings || [])
        setTask(nextTask || null)
      } else {
        setBindings([])
        setTask(null)
      }
    } catch (err) {
      notify(err.message || '企业微信配置加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) {
      load()
    }
  }, [open])

  useEffect(() => {
    if (!open || !isRunning(task)) return undefined
    let cancelled = false
    let timer
    const poll = async () => {
      try {
        const nextTask = await api.wecom.syncDetail(task.id)
        if (cancelled) return
        setTask(nextTask)
        if (!isRunning(nextTask)) {
          const nextConfig = await api.wecom.configDetail()
          if (cancelled) return
          setConfig(nextConfig || null)
          if (nextTask?.status === 'SUCCESS') {
            const nextBindings = await api.wecom.bindingList(nextTask.configId)
            if (cancelled) return
            setBindings(nextBindings || [])
            notify('企业微信客户同步完成', 'success')
            onSynced?.()
          } else if (nextTask?.status === 'FAILED') {
            notify(nextTask.errorMessage || '企业微信客户同步失败', 'info')
          }
          return
        }
        timer = window.setTimeout(poll, 1500)
      } catch (err) {
        if (cancelled) return
        notify(err.message || '同步状态读取失败', 'info')
      }
    }
    timer = window.setTimeout(poll, 800)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [open, task?.id])

  const saveConfig = async () => {
    if (!form.name?.trim() || !form.corpId?.trim()) {
      notify('请填写企业名称和企业ID', 'info')
      return
    }
    if (!config?.secretConfigured && !form.corpSecret?.trim()) {
      notify('首次配置必须填写客户联系Secret', 'info')
      return
    }
    setSaving(true)
    try {
      const saved = await api.wecom.configSave({
        id: config?.id,
        name: form.name.trim(),
        corpId: form.corpId.trim(),
        corpSecret: form.corpSecret || undefined,
        enabled: Boolean(form.enabled),
        syncIntervalMinutes: Number(form.syncIntervalMinutes || 10),
        defaultOwnerId: form.defaultOwnerId || null,
      })
      setConfig(saved)
      setForm({ ...form, id: saved.id, corpSecret: '' })
      notify('企业微信同步配置已保存', 'success')
    } catch (err) {
      notify(err.message || '企业微信同步配置保存失败', 'info')
    } finally {
      setSaving(false)
    }
  }

  const saveBindings = async () => {
    try {
      const result = await api.wecom.bindingSave({
        configId: config.id,
        bindings: bindings.map((item) => ({
          wecomUserId: item.wecomUserId,
          crmUserId: item.crmUserId || null,
        })),
      })
      setBindings(result || [])
      notify('员工负责人映射已保存', 'success')
    } catch (err) {
      notify(err.message || '员工负责人映射保存失败', 'info')
    }
  }

  const startSync = async () => {
    if (!config?.id) {
      notify('请先保存企业微信配置', 'info')
      return
    }
    try {
      const nextTask = await api.wecom.syncStart(config.id)
      setTask(nextTask)
      notify(isRunning(nextTask) ? '同步任务已启动' : '已有同步任务正在处理', 'success')
    } catch (err) {
      notify(err.message || '企业微信同步启动失败', 'info')
    }
  }

  const updateBinding = (index, value) => {
    const next = [...bindings]
    next[index] = { ...next[index], crmUserId: value || null }
    setBindings(next)
  }

  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>关闭</Button>
      {canManage && (
        <Button variant="secondary" disabled={saving} onClick={saveConfig}>
          {saving ? '保存中…' : '保存配置'}
        </Button>
      )}
      {canSync && (
        <Button icon={RefreshCw} disabled={!config?.id || isRunning(task)} onClick={startSync}>
          {isRunning(task) ? '正在同步' : '立即同步'}
        </Button>
      )}
    </>
  )

  return (
    <Modal open={open} title="企业微信客户同步" onClose={onClose} footer={footer} size="xl">
      {loading ? (
        <div className="wecom-empty">正在读取企业微信配置…</div>
      ) : (
        <div className="wecom-config-layout">
          <section className="wecom-config-section">
            <div className="wecom-section-head">
              <div>
                <h3>主动同步配置</h3>
                <p>CRM 定时主动拉取外部联系人和客户群，内网部署无需接收公网回调。</p>
              </div>
              <Badge tone={form.enabled ? 'success' : 'neutral'}>{form.enabled ? '已启用' : '已停用'}</Badge>
            </div>
            <div className="form-grid">
              <Field label="企业名称" required>
                <input
                  disabled={!canManage}
                  value={form.name || ''}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                  placeholder="企业微信中的企业名称"
                />
              </Field>
              <Field label="企业ID" required>
                <input
                  disabled={!canManage}
                  value={form.corpId || ''}
                  onChange={(event) => setForm({ ...form, corpId: event.target.value })}
                  placeholder="例如：wwxxxxxxxxxxxxxxxx"
                />
              </Field>
              <Field
                label="客户联系Secret"
                required={!config?.secretConfigured}
                hint={config?.secretConfigured ? '已配置；不修改时留空即可。' : '在企业微信客户联系应用中获取。'}
              >
                <SecretInput
                  disabled={!canManage}
                  value={form.corpSecret || ''}
                  onChange={(event) => setForm({ ...form, corpSecret: event.target.value })}
                  placeholder={config?.secretConfigured ? '已配置' : '请输入Secret'}
                />
              </Field>
              <Field label="同步间隔（分钟）" hint="最短 2 分钟，建议 5—15 分钟。">
                <input
                  type="number"
                  min="2"
                  max="1440"
                  disabled={!canManage}
                  value={form.syncIntervalMinutes || 10}
                  onChange={(event) => setForm({ ...form, syncIntervalMinutes: event.target.value })}
                />
              </Field>
              <Field label="默认负责人" hint="员工未建立映射时使用；不选则进入公共渠道池。">
                <Select
                  searchable
                  disabled={!canManage}
                  value={form.defaultOwnerId}
                  options={crmOwnerOptions}
                  onChange={(value) => setForm({ ...form, defaultOwnerId: value })}
                />
              </Field>
              <Field label="定时同步">
                <label className="wecom-switch">
                  <input
                    type="checkbox"
                    disabled={!canManage}
                    checked={Boolean(form.enabled)}
                    onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
                  />
                  <span>{form.enabled ? '自动同步已开启' : '仅允许人工同步'}</span>
                </label>
              </Field>
            </div>
          </section>

          <section className="wecom-config-section">
            <div className="wecom-section-head">
              <div>
                <h3>最近同步</h3>
                <p>重复客户按企业ID与外部联系人ID识别，不会反复生成渠道记录。</p>
              </div>
              {task?.status && (
                <Badge tone={statusTone[task.status] || 'neutral'}>
                  {statusText[task.status] || task.status}
                </Badge>
              )}
            </div>
            {!task ? (
              <div className="wecom-empty">保存配置后执行首次同步，这里会展示真实同步结果。</div>
            ) : (
              <>
                <div className="wecom-sync-stats">
                  <div><span>客户</span><b>{task.contactsFetched || 0}</b></div>
                  <div><span>客户群</span><b>{task.groupsFetched || 0}</b></div>
                  <div><span>群成员</span><b>{task.groupMembersFetched || 0}</b></div>
                  <div><span>新增渠道</span><b>{task.channelsCreated || 0}</b></div>
                  <div><span>更新渠道</span><b>{task.channelsUpdated || 0}</b></div>
                  <div><span>重复跳过</span><b>{task.duplicatesSkipped || 0}</b></div>
                </div>
                <div className="wecom-task-meta">
                  <span>开始时间：{formatTime(task.startedAt)}</span>
                  <span>完成时间：{formatTime(task.finishedAt)}</span>
                  {task.errorMessage && <strong>{task.errorMessage}</strong>}
                </div>
              </>
            )}
          </section>

          {config?.id && bindings.length > 0 && (
            <section className="wecom-config-section wecom-binding-section">
              <div className="wecom-section-head">
                <div>
                  <h3>员工负责人映射</h3>
                  <p>客户添加了哪位企业微信员工，就自动分配给对应的 CRM 用户。</p>
                </div>
                <UsersRound size={20} />
              </div>
              <div className="wecom-binding-list">
                {bindings.map((item, index) => (
                  <div className="wecom-binding-row" key={item.wecomUserId}>
                    <div>
                      <b>{item.wecomUserName || item.wecomUserId}</b>
                      <small>{item.wecomUserId}</small>
                    </div>
                    <Select
                      searchable
                      disabled={!canManage}
                      value={item.crmUserId || ''}
                      options={crmOwnerOptions}
                      onChange={(value) => updateBinding(index, value)}
                    />
                  </div>
                ))}
              </div>
              {canManage && (
                <div className="wecom-binding-actions">
                  <Button variant="secondary" icon={CheckCircle2} onClick={saveBindings}>
                    保存员工映射
                  </Button>
                </div>
              )}
            </section>
          )}
        </div>
      )}
    </Modal>
  )
}
