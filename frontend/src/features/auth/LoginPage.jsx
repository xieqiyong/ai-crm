import { useState } from 'react'
import { ContactRound, Eye, EyeOff, LockKeyhole, ShieldCheck } from 'lucide-react'
import { api } from '../../api'
import { BrandLogo, Button, Field, Modal } from '../../components'
import { APP_NAME } from '../../config/appConfig'
import { LoginRequest } from '../../models/requests'
import { AuthLayout } from '../../layouts'

export function LoginPage({ onLogin, logo }) {
  const [showPassword, setShowPassword] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [resetOpen, setResetOpen] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    if (!username || !password) return
    setLoading(true)
    setError('')
    try {
      await onLogin(new LoginRequest({ username, password }))
    } catch (err) {
      setError(err.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout>
      <div className="login-form-wrap">
        <div className="login-mobile-brand">
          <BrandLogo logo={logo} />
        </div>
        <span className="eyebrow">欢迎回来</span>
        <h2>登录到{APP_NAME}</h2>
        <p className="login-subtitle">请使用系统管理员分配的账号登录，角色与权限将在认证成功后自动加载。</p>
        <form onSubmit={submit}>
          <Field label="邮箱 / 用户名" required>
            <div className="input-with-icon">
              <ContactRound size={18} />
              <input value={username} onChange={(event) => setUsername(event.target.value)} />
            </div>
          </Field>
          <Field label="密码" required>
            <div className="input-with-icon">
              <LockKeyhole size={18} />
              <input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} />
              <button type="button" onClick={() => setShowPassword(!showPassword)}>{showPassword ? <EyeOff /> : <Eye />}</button>
            </div>
          </Field>
          {error && <div className="form-error">{error}</div>}
          <div className="login-options">
            <label>
              <input type="checkbox" defaultChecked />
              保持登录状态
            </label>
            <button type="button" onClick={() => setResetOpen(true)}>忘记密码？</button>
          </div>
          <Button className="login-submit" type="submit" disabled={loading}>{loading ? '登录中…' : '登录系统'}</Button>
        </form>
        <div className="security-note">
          <ShieldCheck size={15} />
          已启用企业级身份认证与传输加密
        </div>
      </div>
      <ForgotPasswordModal
        open={resetOpen}
        defaultUsername={username}
        onClose={() => setResetOpen(false)}
      />
    </AuthLayout>
  )
}

function ForgotPasswordModal({ open, defaultUsername, onClose }) {
  const [stage, setStage] = useState('request')
  const [username, setUsername] = useState(defaultUsername || '')
  const [resetToken, setResetToken] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const requestReset = async () => {
    if (!username) return
    setLoading(true)
    setError('')
    try {
      const response = await api.auth.forgotPassword({ username })
      setMessage(response.message || '如果账号存在，系统已经生成密码重置申请')
      if (response.resetTokenExposed && response.resetToken) {
        setResetToken(response.resetToken)
        setStage('reset')
      }
    } catch (err) {
      setError(err.message || '提交失败')
    } finally {
      setLoading(false)
    }
  }

  const resetPassword = async () => {
    if (!resetToken || !newPassword) return
    if (newPassword !== confirmPassword) {
      setError('两次输入的新密码不一致')
      return
    }
    setLoading(true)
    setError('')
    try {
      await api.auth.resetPassword({ resetToken, newPassword })
      setMessage('密码已重置，请使用新密码登录')
      setStage('done')
    } catch (err) {
      setError(err.message || '重置失败')
    } finally {
      setLoading(false)
    }
  }

  const close = () => {
    setStage('request')
    setResetToken('')
    setNewPassword('')
    setConfirmPassword('')
    setMessage('')
    setError('')
    onClose()
  }

  return (
    <Modal
      open={open}
      title="忘记密码"
      onClose={close}
      footer={stage === 'request'
        ? <><Button variant="secondary" onClick={close}>取消</Button><Button onClick={requestReset} disabled={loading}>{loading ? '提交中…' : '提交申请'}</Button></>
        : stage === 'reset'
          ? <><Button variant="secondary" onClick={close}>取消</Button><Button onClick={resetPassword} disabled={loading}>{loading ? '重置中…' : '重置密码'}</Button></>
          : <Button onClick={close}>我知道了</Button>}
    >
      {stage === 'request' && (
        <>
          <Field label="用户名" required>
            <input value={username} onChange={(event) => setUsername(event.target.value)} />
          </Field>
          <p className="login-subtitle">提交后系统会生成一次性重置码。当前未接入邮件或短信服务时，可通过开发配置返回重置码。</p>
        </>
      )}
      {stage === 'reset' && (
        <>
          {message && <div className="form-error">{message}</div>}
          <Field label="重置码" required>
            <input value={resetToken} onChange={(event) => setResetToken(event.target.value)} />
          </Field>
          <Field label="新密码" required>
            <input type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
          </Field>
          <Field label="确认新密码" required>
            <input type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} />
          </Field>
        </>
      )}
      {stage === 'done' && <p className="login-subtitle">{message}</p>}
      {stage === 'request' && message && <div className="form-error">{message}</div>}
      {error && <div className="form-error">{error}</div>}
    </Modal>
  )
}
