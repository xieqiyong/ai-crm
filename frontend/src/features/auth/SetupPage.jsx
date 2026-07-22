import { useState } from 'react'
import { BrandLogo, Button, Field } from '../../components'
import { SetupSuperAdminRequest } from '../../models/requests'

export function SetupPage({ onSetup, logo }) {
  const [form, setForm] = useState({ tenantId: 'default', username: '', displayName: '', password: '', confirmPassword: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const update = (key, value) => setForm({ ...form, [key]: value })

  const submit = async (event) => {
    event.preventDefault()
    if (!form.username || !form.password) return
    if (form.password !== form.confirmPassword) {
      setError('两次输入的密码不一致')
      return
    }
    setLoading(true)
    setError('')
    try {
      await onSetup(new SetupSuperAdminRequest(form))
    } catch (err) {
      setError(err.message || '初始化失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="setup-page">
      <div className="setup-card">
        <BrandLogo logo={logo} />
        <span className="eyebrow">首次安装</span>
        <h1>创建超级管理员</h1>
        <p>系统未检测到超级管理员账号。完成初始化后，将自动进入系统并启用完整菜单权限与全部数据权限。</p>
        <form onSubmit={submit}>
          <Field label="租户 / 顶级部门" hint="初始化后会作为组织架构的顶级部门">
            <input value={form.tenantId} onChange={(event) => update('tenantId', event.target.value)} />
          </Field>
          <Field label="超管用户名" required>
            <input value={form.username} onChange={(event) => update('username', event.target.value)} />
          </Field>
          <Field label="显示名称">
            <input value={form.displayName} onChange={(event) => update('displayName', event.target.value)} />
          </Field>
          <Field label="登录密码" required>
            <input type="password" value={form.password} onChange={(event) => update('password', event.target.value)} />
          </Field>
          <Field label="确认密码" required>
            <input type="password" value={form.confirmPassword} onChange={(event) => update('confirmPassword', event.target.value)} />
          </Field>
          {error && <div className="form-error">{error}</div>}
          <Button className="login-submit" type="submit" disabled={loading}>{loading ? '初始化中…' : '完成初始化'}</Button>
        </form>
      </div>
    </div>
  )
}
