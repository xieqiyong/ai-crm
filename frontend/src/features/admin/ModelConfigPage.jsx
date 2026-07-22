import { useEffect, useState } from 'react'
import { Bot, CheckCircle2, Edit2, Plus, RefreshCw, ShieldCheck, Star, Trash2 } from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, Field, Modal, PageHeader } from '../../components'

const emptyForm = {
  provider: 'OPENAI',
  name: '',
  modelName: '',
  baseUrl: '',
  apiKeyEnv: '',
  remark: '',
  defaultConfig: false,
  enabled: true,
}

export function ModelConfigPage({ can, notify }) {
  const canManage = can('crm:model:manage')
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)

  const load = async () => {
    setLoading(true)
    try {
      setRows(await api.modelConfig.list())
    } catch (err) {
      notify(err.message || '加载大模型配置失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const checkStatus = async (id) => {
    const response = await api.modelConfig.status(id)
    notify(response.message, response.available ? 'success' : 'info')
    load()
  }

  const setDefault = async (id) => {
    await api.modelConfig.setDefault(id)
    notify('默认模型配置已更新')
    load()
  }

  const remove = async (id) => {
    await api.modelConfig.delete(id)
    notify('模型配置已删除')
    load()
  }

  return (
    <div className="page model-config-page">
      <PageHeader
        title="大模型配置"
        description="配置 Agent 和智能营销能力使用的大模型，不保存明文 API Key，只保存密钥环境变量名"
        actions={<><Button variant="secondary" icon={RefreshCw} onClick={load}>刷新</Button>{canManage && <Button icon={Plus} onClick={() => setEditing(emptyForm)}>新增模型</Button>}</>}
      />
      <Card className="table-card">
        <div className="data-table-wrap">
          <table className="data-table">
            <thead><tr><th>配置名称</th><th>供应商</th><th>模型</th><th>地址</th><th>密钥变量</th><th>状态</th><th>默认</th><th>操作</th></tr></thead>
            <tbody>{rows.map((row) => (
              <tr key={row.id}>
                <td><strong><Bot size={15} /> {row.name}</strong><small>{row.remark || '无备注'}</small></td>
                <td>{row.provider}</td>
                <td>{row.modelName}</td>
                <td>{row.baseUrl || '默认地址'}</td>
                <td><Badge tone={row.apiKeyConfigured ? 'success' : 'warning'}>{row.apiKeyEnv}</Badge></td>
                <td><Badge dot tone={row.enabled ? 'success' : 'danger'}>{row.enabled ? '启用' : '停用'}</Badge></td>
                <td>{row.defaultConfig ? <Badge tone="info"><Star size={12} />默认</Badge> : '-'}</td>
                <td>
                  <button className="icon-button" onClick={() => checkStatus(row.id)}><ShieldCheck size={17} /></button>
                  <button className="icon-button" disabled={!canManage} onClick={() => setEditing(row)}><Edit2 size={17} /></button>
                  <button className="icon-button" disabled={!canManage || row.defaultConfig} onClick={() => setDefault(row.id)}><CheckCircle2 size={17} /></button>
                  <button className="icon-button" disabled={!canManage} onClick={() => remove(row.id)}><Trash2 size={17} /></button>
                </td>
              </tr>
            ))}</tbody>
          </table>
          {!loading && !rows.length && <div className="empty-table"><Bot size={26} /><b>暂无大模型配置</b><span>新增后会写入 llm_model_config 表</span></div>}
          {loading && <div className="empty-table"><RefreshCw size={26} /><b>正在加载</b><span>读取后台大模型配置</span></div>}
        </div>
      </Card>
      <ModelConfigModal
        open={Boolean(editing)}
        data={editing}
        onClose={() => setEditing(null)}
        reload={load}
      />
    </div>
  )
}

function ModelConfigModal({ open, data, onClose, reload }) {
  const [form, setForm] = useState(emptyForm)

  useEffect(() => {
    setForm(data || emptyForm)
  }, [data, open])

  const save = async () => {
    await api.modelConfig.save(form)
    onClose()
    reload()
  }

  return (
    <Modal open={open} title={form.id ? '编辑模型配置' : '新增模型配置'} onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>取消</Button><Button onClick={save}>保存</Button></>}>
      <Field label="配置名称" required>
        <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
      </Field>
      <Field label="供应商" required>
        <select value={form.provider} onChange={(event) => setForm({ ...form, provider: event.target.value })}>
          <option value="OPENAI">OpenAI 兼容</option>
          <option value="DASHSCOPE">通义千问</option>
          <option value="DEEPSEEK">DeepSeek</option>
          <option value="CUSTOM">自定义</option>
        </select>
      </Field>
      <Field label="模型标识" required>
        <input value={form.modelName} onChange={(event) => setForm({ ...form, modelName: event.target.value })} placeholder="例如 gpt-4.1 或 deepseek-chat" />
      </Field>
      <Field label="Base URL">
        <input value={form.baseUrl || ''} onChange={(event) => setForm({ ...form, baseUrl: event.target.value })} placeholder="OpenAI 兼容接口地址，可为空" />
      </Field>
      <Field label="密钥环境变量" required hint="不保存明文 API Key，只填写后端进程可读取的环境变量名">
        <input value={form.apiKeyEnv} onChange={(event) => setForm({ ...form, apiKeyEnv: event.target.value })} placeholder="例如 OPENAI_API_KEY" />
      </Field>
      <Field label="备注">
        <textarea rows="3" value={form.remark || ''} onChange={(event) => setForm({ ...form, remark: event.target.value })} />
      </Field>
      <label className="density-options">
        <input type="checkbox" checked={Boolean(form.defaultConfig)} onChange={(event) => setForm({ ...form, defaultConfig: event.target.checked })} />
        <span><b>设为默认模型</b><small>保存后会取消其他默认配置</small></span>
      </label>
      <label className="density-options">
        <input type="checkbox" checked={form.enabled !== false} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} />
        <span><b>启用配置</b><small>停用后不会作为可用模型配置</small></span>
      </label>
    </Modal>
  )
}
