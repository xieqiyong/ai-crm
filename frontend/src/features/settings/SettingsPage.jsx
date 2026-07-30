import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Bell,
  Building2,
  Check,
  CheckCircle2,
  KeyRound,
  LogOut,
  Palette,
  Send,
  Settings2,
  ShieldCheck,
  Upload,
} from 'lucide-react'
import { api } from '../../api'
import { APP_NAME } from '../../config/appConfig'
import {
  Badge,
  Button,
  Card,
  Field,
  PageHeader,
  SecretInput,
  Select,
} from '../../components'

const PASSWORD_HINT = '8至64位，必须同时包含大写字母、小写字母、数字和特殊字符，不能包含空格'

export function SettingsPage({
  preferences,
  onUpdate,
  notify,
  currentUser,
  can,
}) {
  const [activeTab, setActiveTab] = useState('appearance')
  const [draft, setDraft] = useState(preferences)
  const [saved, setSaved] = useState(true)
  const fileRef = useRef(null)
  const accents = ['#f45b0b', '#2563eb', '#7c3aed', '#0891b2', '#16a34a']

  const update = (patch) => {
    const next = { ...draft, ...patch }
    setDraft(next)
    onUpdate(next)
    setSaved(false)
  }

  const uploadLogo = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      notify('请选择图片格式的 Logo', 'info')
      return
    }
    if (file.size > 1024 * 1024) {
      notify('Logo 图片请控制在 1MB 以内', 'info')
      return
    }
    const reader = new FileReader()
    reader.onload = () => update({ logo: reader.result })
    reader.readAsDataURL(file)
  }

  const save = () => {
    onUpdate(draft)
    setSaved(true)
    notify('品牌与外观设置已保存')
  }

  return (
    <div className="page settings-page">
      <PageHeader
        title="系统设置"
        description="管理品牌外观、登录安全、系统通知和运行参数"
        actions={activeTab === 'appearance'
          ? <Button icon={Check} disabled={saved} onClick={save}>{saved ? '已保存' : '保存更改'}</Button>
          : null}
      />
      <div className="settings-layout">
        <aside className="settings-nav">
          <button className={activeTab === 'appearance' ? 'active' : ''} onClick={() => setActiveTab('appearance')}><Palette size={17} />品牌与外观</button>
          <button className={activeTab === 'security' ? 'active' : ''} onClick={() => setActiveTab('security')}><ShieldCheck size={17} />登录与安全</button>
          <button className={activeTab === 'notifications' ? 'active' : ''} onClick={() => setActiveTab('notifications')}><Bell size={17} />消息通知</button>
          <button className={activeTab === 'system' ? 'active' : ''} onClick={() => setActiveTab('system')}><Settings2 size={17} />系统参数</button>
        </aside>

        <div className="settings-content">
          {activeTab === 'appearance' && (
            <AppearanceSettings
              draft={draft}
              accents={accents}
              fileRef={fileRef}
              saved={saved}
              update={update}
              uploadLogo={uploadLogo}
              save={save}
            />
          )}
          {activeTab === 'security' && (
            <SecuritySettings currentUser={currentUser} notify={notify} />
          )}
          {activeTab === 'notifications' && (
            <NotificationSettings
              canManage={can('crm:notification:manage')}
              notify={notify}
            />
          )}
          {activeTab === 'system' && <SystemSettings currentUser={currentUser} />}
        </div>
      </div>
    </div>
  )
}

function AppearanceSettings({
  draft,
  accents,
  fileRef,
  saved,
  update,
  uploadLogo,
  save,
}) {
  return (
    <>
      <Card className="settings-section">
        <div className="settings-section-head">
          <div><h2>企业品牌</h2><p>自定义 Logo 会应用于登录页、侧栏和移动端导航。</p></div>
          <Badge tone="success">已启用</Badge>
        </div>
        <div className="logo-setting">
          <div className="logo-preview">{draft.logo ? <img src={draft.logo} alt="当前 Logo" /> : <Building2 size={31} />}</div>
          <div>
            <b>企业 Logo</b>
            <p>建议使用透明背景 PNG 或 SVG，尺寸不小于 128 × 128px，最大 1MB。</p>
            <div>
              <Button variant="secondary" icon={Upload} onClick={() => fileRef.current?.click()}>上传 Logo</Button>
              {draft.logo && <Button variant="ghost" onClick={() => update({ logo: '' })}>恢复默认</Button>}
            </div>
            <input ref={fileRef} hidden type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" onChange={uploadLogo} />
          </div>
        </div>
        <Field label="系统名称"><input defaultValue={APP_NAME} disabled /></Field>
      </Card>

      <Card className="settings-section">
        <div className="settings-section-head"><div><h2>界面主题</h2><p>主题选项会即时预览，保存后应用于当前浏览器。</p></div></div>
        <div className="theme-options">
          {[
            ['light', '浅色模式'],
            ['dark', '深色模式'],
            ['system', '跟随系统'],
          ].map(([key, label]) => (
            <button className={draft.theme === key ? 'active' : ''} onClick={() => update({ theme: key })} key={key}>
              <span className={`theme-thumb ${key}`}><i /><b /><em /></span>
              <div><b>{label}</b><small>{key === 'light' ? '明亮清晰' : key === 'dark' ? '低光舒适' : '自动切换'}</small></div>
              {draft.theme === key && <Check size={16} />}
            </button>
          ))}
        </div>
      </Card>

      <Card className="settings-section">
        <div className="settings-section-head"><div><h2>品牌强调色</h2><p>用于主要按钮、选中状态和 AI 能力标识。</p></div></div>
        <div className="accent-options">
          {accents.map((color) => (
            <button className={draft.accent === color ? 'active' : ''} style={{ background: color }} key={color} onClick={() => update({ accent: color })}>
              {draft.accent === color && <Check size={17} />}
            </button>
          ))}
          <label><span>自定义</span><input type="color" value={draft.accent} onChange={(event) => update({ accent: event.target.value })} /></label>
        </div>
      </Card>

      <Card className="settings-section">
        <div className="settings-section-head"><div><h2>内容密度</h2><p>根据屏幕尺寸和工作习惯调整信息密度。</p></div></div>
        <div className="density-options">
          {[
            ['comfortable', '舒适', '更大的留白，适合日常办公'],
            ['compact', '紧凑', '同屏展示更多表格数据'],
          ].map(([key, label, desc]) => (
            <label className={draft.density === key ? 'active' : ''} key={key}>
              <input type="radio" checked={draft.density === key} onChange={() => update({ density: key })} />
              <span><b>{label}</b><small>{desc}</small></span>
            </label>
          ))}
        </div>
      </Card>

      <div className="settings-save-bar">
        <span>{saved ? <><CheckCircle2 size={16} />所有更改均已保存</> : '您有未保存的外观更改'}</span>
        <Button onClick={save} disabled={saved}>保存设置</Button>
      </div>
    </>
  )
}

function SecuritySettings({ currentUser, notify }) {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [saving, setSaving] = useState(false)
  const [revoking, setRevoking] = useState(false)

  const update = (key, value) => setForm({ ...form, [key]: value })

  const changePassword = async () => {
    const password = form.newPassword
    const passwordValid = password.length >= 8
      && password.length <= 64
      && /[A-Z]/.test(password)
      && /[a-z]/.test(password)
      && /[0-9]/.test(password)
      && /[^A-Za-z0-9]/.test(password)
      && !/\s/.test(password)
    if (!form.currentPassword || !passwordValid) {
      notify(PASSWORD_HINT, 'info')
      return
    }
    if (form.newPassword !== form.confirmPassword) {
      notify('两次输入的新密码不一致', 'info')
      return
    }
    setSaving(true)
    try {
      await api.auth.changePassword(form)
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
      notify('密码修改成功，其他设备已退出登录')
    } catch (err) {
      notify(err.message || '密码修改失败', 'info')
    } finally {
      setSaving(false)
    }
  }

  const revokeOther = async () => {
    setRevoking(true)
    try {
      await api.auth.revokeOtherSessions()
      notify('其他设备的登录会话已失效')
    } catch (err) {
      notify(err.message || '退出其他设备失败', 'info')
    } finally {
      setRevoking(false)
    }
  }

  return (
    <>
      <Card className="settings-section">
        <div className="settings-section-head">
          <div><h2>当前账号</h2><p>账号身份由登录令牌和服务端会话共同校验。</p></div>
          <Badge tone="success">会话有效</Badge>
        </div>
        <div className="security-account-grid">
          <div><span>登录用户名</span><b>{currentUser?.username || '-'}</b></div>
          <div><span>显示名称</span><b>{currentUser?.displayName || '-'}</b></div>
          <div><span>数据权限</span><b>{currentUser?.dataScope || '-'}</b></div>
          <div><span>会话编号</span><b>{currentUser?.sessionId || '-'}</b></div>
        </div>
      </Card>

      <Card className="settings-section">
        <div className="settings-section-head"><div><h2>修改登录密码</h2><p>修改成功后保留当前设备，其他已登记设备将被强制退出。</p></div><KeyRound size={21} /></div>
        <div className="settings-password-form">
          <Field label="当前密码" required><SecretInput autoComplete="current-password" value={form.currentPassword} onChange={(event) => update('currentPassword', event.target.value)} /></Field>
          <Field label="新密码" required hint={PASSWORD_HINT}><SecretInput autoComplete="new-password" value={form.newPassword} onChange={(event) => update('newPassword', event.target.value)} /></Field>
          <Field label="确认新密码" required><SecretInput autoComplete="new-password" value={form.confirmPassword} onChange={(event) => update('confirmPassword', event.target.value)} /></Field>
          <Button icon={KeyRound} disabled={saving} onClick={changePassword}>{saving ? '修改中…' : '修改密码'}</Button>
        </div>
      </Card>

      <Card className="settings-section security-session-card">
        <div>
          <span><LogOut size={19} /></span>
          <div><h2>其他登录设备</h2><p>立即撤销当前账号在其他浏览器或设备上的服务端会话，不影响本机。</p></div>
        </div>
        <Button variant="secondary" disabled={revoking} onClick={revokeOther}>{revoking ? '处理中…' : '退出其他设备'}</Button>
      </Card>
    </>
  )
}

function NotificationSettings({ canManage, notify }) {
  const [users, setUsers] = useState([])
  const [form, setForm] = useState({
    targetType: 'ALL',
    targetUserId: '',
    level: 'INFO',
    title: '',
    content: '',
  })
  const [sending, setSending] = useState(false)

  useEffect(() => {
    if (!canManage) return
    api.notification.recipients()
      .then((data) => setUsers(data || []))
      .catch((err) => notify(err.message || '销售人员列表加载失败', 'info'))
  }, [canManage])

  const userOptions = useMemo(() => users.map((user) => ({
    value: user.id,
    label: user.displayName || user.username,
    description: user.departmentName || user.username,
  })), [users])

  const update = (key, value) => setForm({ ...form, [key]: value })

  const send = async () => {
    if (!form.title.trim() || !form.content.trim()) {
      notify('请填写通知标题和内容', 'info')
      return
    }
    if (form.targetType === 'USER' && !form.targetUserId) {
      notify('请选择接收通知的销售', 'info')
      return
    }
    setSending(true)
    try {
      await api.notification.send({
        ...form,
        targetUserId: form.targetType === 'USER' ? form.targetUserId : null,
      })
      setForm({ ...form, title: '', content: '' })
      notify(form.targetType === 'ALL' ? '全站通知已发布' : '通知已发送给指定销售')
    } catch (err) {
      notify(err.message || '通知发布失败', 'info')
    } finally {
      setSending(false)
    }
  }

  if (!canManage) {
    return (
      <Card className="settings-section">
        <div className="settings-section-head"><div><h2>消息通知</h2><p>铃铛会展示管理员发送给您的真实通知和未读数量。</p></div><Bell size={21} /></div>
        <div className="settings-security-note">当前账号没有系统通知发布权限。</div>
      </Card>
    )
  }

  return (
    <Card className="settings-section">
      <div className="settings-section-head">
        <div><h2>发布系统通知</h2><p>可发送给当前租户的全部用户，或单独发送给某个销售。</p></div>
        <Badge tone="success">管理员功能</Badge>
      </div>
      <div className="notification-send-form">
        <div className="notification-form-row">
          <Field label="发送范围" required>
            <Select
              value={form.targetType}
              options={[
                { value: 'ALL', label: '全站通知', description: '发送给当前租户所有启用用户' },
                { value: 'USER', label: '指定销售', description: '仅发送给选中的用户' },
              ]}
              onChange={(targetType) => setForm({ ...form, targetType, targetUserId: '' })}
            />
          </Field>
          <Field label="通知级别" required>
            <Select
              value={form.level}
              options={[
                { value: 'INFO', label: '普通通知' },
                { value: 'IMPORTANT', label: '重要通知' },
                { value: 'WARNING', label: '风险提醒' },
              ]}
              onChange={(level) => update('level', level)}
            />
          </Field>
        </div>
        {form.targetType === 'USER' && (
          <Field label="接收销售" required>
            <Select searchable value={form.targetUserId} options={userOptions} placeholder="搜索销售姓名" onChange={(targetUserId) => update('targetUserId', targetUserId)} />
          </Field>
        )}
        <Field label="通知标题" required hint="最多128个字符"><input maxLength={128} value={form.title} onChange={(event) => update('title', event.target.value)} /></Field>
        <Field label="通知内容" required><textarea rows={6} value={form.content} onChange={(event) => update('content', event.target.value)} /></Field>
        <Button icon={Send} disabled={sending} onClick={send}>{sending ? '发布中…' : '发布通知'}</Button>
      </div>
    </Card>
  )
}

function SystemSettings({ currentUser }) {
  return (
    <Card className="settings-section">
      <div className="settings-section-head"><div><h2>运行参数</h2><p>展示当前会话实际生效的系统隔离与接口约束。</p></div><Settings2 size={21} /></div>
      <div className="system-parameter-list">
        <div><span>租户隔离</span><b>已启用</b><small>当前租户编号：{currentUser?.tenantId || '-'}</small></div>
        <div><span>服务端会话</span><b>Redis 共享会话</b><small>JWT 与服务端会话双重校验</small></div>
        <div><span>业务接口规范</span><b>POST</b><small>管理和业务请求统一使用 POST</small></div>
      </div>
    </Card>
  )
}
