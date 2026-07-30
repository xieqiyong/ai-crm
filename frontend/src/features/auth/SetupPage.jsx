import { useState } from 'react'
import { BrandLogo, Button, Field, SecretInput } from '../../components'
import { SetupSuperAdminRequest } from '../../models/requests'

export function SetupPage({ onSetup, logo }) {
  const [form, setForm] = useState({ tenantName: '默认租户', username: '', displayName: '', password: '', confirmPassword: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const update = (key, value) => setForm({ ...form, [key]: value })

  const submit = async (event) => {
    event.preventDefault()
    if (!form.username || !form.password) return
    if (!/^[A-Za-z][A-Za-z0-9_.-]{3,31}$/.test(form.username)) {
      setError('用户名为4至32位，必须以字母开头，仅支持字母、数字、下划线、短横线和点')
      return
    }
    if (
      form.password.length < 8
      || form.password.length > 64
      || !/[A-Z]/.test(form.password)
      || !/[a-z]/.test(form.password)
      || !/[0-9]/.test(form.password)
      || !/[^A-Za-z0-9]/.test(form.password)
      || /\s/.test(form.password)
    ) {
      setError('密码为8至64位，必须同时包含大写字母、小写字母、数字和特殊字符，不能包含空格')
      return
    }
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
          <Field label="租户名称 / 顶级部门" hint="系统会自动生成数字租户ID，并将该名称作为组织架构顶级部门">
            <input value={form.tenantName} onChange={(event) => update('tenantName', event.target.value)} />
          </Field>
          <Field label="超管用户名" required hint="4至32位，以字母开头，仅支持字母、数字、下划线、短横线和点">
            <input maxLength={32} value={form.username} onChange={(event) => update('username', event.target.value)} />
          </Field>
          <Field label="显示名称">
            <input value={form.displayName} onChange={(event) => update('displayName', event.target.value)} />
          </Field>
          <Field label="登录密码" required hint="8至64位，需包含大写字母、小写字母、数字和特殊字符，不能包含空格">
            <SecretInput autoComplete="new-password" value={form.password} onChange={(event) => update('password', event.target.value)} />
          </Field>
          <Field label="确认密码" required>
            <SecretInput autoComplete="new-password" value={form.confirmPassword} onChange={(event) => update('confirmPassword', event.target.value)} />
          </Field>
          {error && <div className="form-error">{error}</div>}
          <Button className="login-submit" type="submit" disabled={loading}>{loading ? '初始化中…' : '完成初始化'}</Button>
        </form>
      </div>
    </div>
  )
}
