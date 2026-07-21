import { forwardRef, useEffect, useRef, useState } from 'react'
import {
  BarChart3, Bell, BookOpen, Bot, BriefcaseBusiness, Building2, Check,
  ChevronDown, ClipboardCheck, Clock3, ContactRound, Eye, EyeOff, HelpCircle,
  LayoutDashboard, LockKeyhole, LogOut, Menu, MessageSquareText, Palette,
  Rocket, Search, Settings, ShieldCheck, Sparkles, Target, UserRoundCog, Users,
  X, Zap,
} from 'lucide-react'
import { APP_NAME } from './App'

export function BrandLogo({ logo, compact = false, inverse = false }) {
  return (
    <div className={`brand-logo ${compact ? 'compact' : ''} ${inverse ? 'inverse' : ''}`}>
      <span className="logo-mark">
        {logo ? <img src={logo} alt="企业 Logo" /> : <Rocket size={compact ? 18 : 21} strokeWidth={2.4} />}
      </span>
      {!compact && (
        <span className="brand-copy">
          <strong>{APP_NAME}</strong>
          <small>AI MARKETING CRM</small>
        </span>
      )}
    </div>
  )
}

export const Button = forwardRef(function Button({ children, variant = 'primary', icon: Icon, className = '', ...props }, ref) {
  return (
    <button ref={ref} className={`button ${variant} ${className}`} {...props}>
      {Icon && <Icon size={17} />}
      <span>{children}</span>
    </button>
  )
})

export function Card({ children, className = '', ai = false, ...props }) {
  return <section className={`card ${ai ? 'ai-card' : ''} ${className}`} {...props}>{children}</section>
}

export function Badge({ children, tone = 'neutral', dot = false }) {
  return <span className={`badge ${tone}`}>{dot && <i />}{children}</span>
}

export function PageHeader({ eyebrow, title, description, actions, children }) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <span className="eyebrow">{eyebrow}</span>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
      {children}
    </div>
  )
}

export function EmptyPermission({ onBack }) {
  return (
    <div className="permission-empty">
      <span><LockKeyhole size={30} /></span>
      <h2>当前角色无权访问</h2>
      <p>该功能需要更高权限。如有工作需要，请联系系统管理员调整角色或数据范围。</p>
      <Button onClick={onBack}>返回工作台</Button>
    </div>
  )
}

export function Modal({ open, title, children, onClose, footer, size = 'md' }) {
  if (!open) return null
  return (
    <div className="modal-backdrop" onMouseDown={onClose} role="presentation">
      <div className={`modal ${size}`} onMouseDown={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
        <div className="modal-head">
          <div><span className="eyebrow">智能营销管理系统</span><h2>{title}</h2></div>
          <button className="icon-button" onClick={onClose} aria-label="关闭"><X size={20} /></button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  )
}

export function Field({ label, required, children, hint }) {
  return (
    <label className="field">
      <span>{label}{required && <em>*</em>}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  )
}

export function Toast({ message, tone, onClose }) {
  useEffect(() => {
    const timer = setTimeout(onClose, 2600)
    return () => clearTimeout(timer)
  }, [onClose])
  return (
    <div className={`toast ${tone}`}>
      <span>{tone === 'success' ? <Check size={17} /> : <Sparkles size={17} />}</span>
      {message}
    </div>
  )
}

const navGroups = [
  {
    label: '业务工作台',
    items: [
      ['dashboard', '工作台', LayoutDashboard, 'menu.dashboard'],
      ['leads', '线索管理', BarChart3, 'menu.leads'],
      ['customers', '客户管理', Users, 'menu.customers'],
      ['opportunities', '商机管理', BriefcaseBusiness, 'menu.opportunities'],
      ['followups', '跟进记录', MessageSquareText, 'menu.followups'],
      ['tasks', '销售任务', ClipboardCheck, 'menu.tasks'],
    ],
  },
  {
    label: 'AI 与知识',
    items: [
      ['assistant', 'AI 营销助手', Bot, 'menu.assistant'],
      ['knowledge', '知识库', BookOpen, 'menu.knowledge'],
    ],
  },
  {
    label: '管理',
    items: [
      ['organization', '组织与权限', UserRoundCog, 'menu.organization'],
      ['settings', '系统设置', Settings, 'menu.settings'],
    ],
  },
]

export function AppShell({ children, page, onNavigate, can, currentUser, currentRole, onLogout, logo, onNotify }) {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [assistantOpen, setAssistantOpen] = useState(false)

  const go = (next) => {
    onNavigate(next)
    setMobileOpen(false)
  }

  return (
    <div className="app-shell">
      {mobileOpen && <div className="mobile-scrim" onClick={() => setMobileOpen(false)} />}
      <aside className={`sidebar ${mobileOpen ? 'mobile-open' : ''}`}>
        <div className="sidebar-brand"><BrandLogo logo={logo} /></div>
        <nav className="nav-area" aria-label="主导航">
          {navGroups.map((group) => {
            const visible = group.items.filter((item) => can(item[3]))
            if (!visible.length) return null
            return (
              <div className="nav-group" key={group.label}>
                <div className="nav-label">{group.label}</div>
                {visible.map(([key, label, Icon]) => (
                  <button className={`nav-item ${page === key ? 'active' : ''}`} key={key} onClick={() => go(key)}>
                    <Icon size={19} /><span>{label}</span>
                    {key === 'assistant' && <i className="nav-live" />}
                  </button>
                ))}
              </div>
            )
          })}
        </nav>
        <div className="sidebar-foot">
          <div className="workspace-meter">
            <span><Zap size={14} /> AI 用量</span><b>68%</b>
            <div><i /></div>
          </div>
          {can('crm:lead:create') && <Button icon={Target} onClick={() => { go('leads'); onNotify('已打开线索创建入口') }}>新建线索</Button>}
        </div>
      </aside>

      <div className="app-main">
        <header className="topbar">
          <button className="icon-button menu-button" onClick={() => setMobileOpen(true)} aria-label="打开菜单"><Menu size={21} /></button>
          <div className="global-search"><Search size={18} /><input aria-label="全局搜索" placeholder="搜索线索、客户或商机…" /></div>
          <div className="topbar-actions">
            {can('crm:assistant:use') && <button className="ai-top-button" onClick={() => setAssistantOpen(true)}><Sparkles size={17} />AI 洞察</button>}
            <button className="icon-button notification-button" onClick={() => onNotify('暂无新的未读通知', 'info')} aria-label="通知"><Bell size={19} /><i /></button>
            <button className="icon-button hide-mobile" onClick={() => onNotify('帮助中心正在完善中', 'info')} aria-label="帮助"><HelpCircle size={19} /></button>
            <div className="profile-wrap">
              <button className="profile-button" onClick={() => setProfileOpen(!profileOpen)}>
                <span className="avatar">{currentRole.name.slice(0, 1)}</span>
                <span className="profile-copy"><strong>{currentRole.name}</strong><small>{currentRole.label}</small></span>
                <ChevronDown size={15} />
              </button>
              {profileOpen && (
                <div className="profile-menu">
                  <div className="profile-menu-head">
                    <strong>{currentUser.username}</strong>
                    <small>租户：{currentUser.tenantId} · 数据权限：{currentRole.label}</small>
                  </div>
                  <button className="logout" onClick={onLogout}><LogOut size={16} />退出登录</button>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className="content">{children}</main>
      </div>

      <aside className={`assistant-drawer ${assistantOpen ? 'open' : ''}`}>
        <div className="assistant-head">
          <div className="ai-title-icon"><Sparkles size={18} /></div>
          <div><strong>AI 营销洞察</strong><small>基于实时业务数据生成</small></div>
          <button className="icon-button" onClick={() => setAssistantOpen(false)}><X size={18} /></button>
        </div>
        <div className="assistant-body">
          <div className="assistant-intro"><Bot size={24} /><p>上午好，{currentRole.name}。我发现了 3 个值得关注的业务信号。</p></div>
          <Card className="insight-card warning">
            <Badge tone="danger">流失风险</Badge>
            <h3>蓝图软件 7 天未响应</h3>
            <p>采购负责人近期查看了竞品报价，建议今天完成一次价值回访。</p>
            <Button variant="secondary" onClick={() => onNotify('已生成客户回访话术')}>生成回访话术</Button>
          </Card>
          <Card className="insight-card">
            <Badge tone="success">成交信号</Badge>
            <h3>极星未来意向度上升</h3>
            <p>客户昨日 3 次查看私有化部署方案，预计成交概率提升至 78%。</p>
            <Button variant="secondary" onClick={() => go('customers')}>查看客户详情</Button>
          </Card>
          <div className="ai-disclaimer"><ShieldCheck size={15} />AI 建议仅供业务决策参考</div>
        </div>
        <div className="assistant-input"><input placeholder="问问 AI 营销助手…" /><button onClick={() => onNotify('AI 对话已加入产品交互入口', 'info')}><Sparkles size={17} /></button></div>
      </aside>
    </div>
  )
}

export function LoginPage({ onLogin, logo }) {
  const [showPassword, setShowPassword] = useState(false)
  const [tenantId, setTenantId] = useState('default')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const submitRef = useRef()

  const submit = async (event) => {
    event.preventDefault()
    if (!username || !password) return
    setLoading(true)
    setError('')
    try {
      await onLogin({ tenantId, username, password })
    } catch (err) {
      setError(err.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <section className="login-visual">
        <div className="login-visual-inner">
          <BrandLogo logo={logo} inverse />
          <div className="visual-content">
            <span className="visual-orbit"><Sparkles size={34} /></span>
            <p className="visual-kicker">AI-NATIVE MARKETING CRM</p>
            <h1>让每一次客户互动<br /><em>更接近成交</em></h1>
            <p>从线索识别到商机预测，用智能洞察连接营销与销售，让团队把时间花在最有价值的客户上。</p>
            <div className="visual-features">
              <div><BarChart3 /><span><b>精准预测</b><small>实时识别高意向线索</small></span></div>
              <div><Bot /><span><b>智能协作</b><small>自动生成下一步建议</small></span></div>
              <div><ShieldCheck /><span><b>企业级权限</b><small>角色、数据与操作隔离</small></span></div>
            </div>
          </div>
          <div className="visual-proof"><span className="proof-avatars"><i>陈</i><i>李</i><i>王</i></span><span><b>1,200+ 支团队正在使用</b><small>让增长更清晰、更可预测</small></span></div>
        </div>
      </section>
      <section className="login-form-side">
        <div className="login-form-wrap">
          <div className="login-mobile-brand"><BrandLogo logo={logo} /></div>
          <span className="eyebrow">欢迎回来</span>
          <h2>登录到{APP_NAME}</h2>
          <p className="login-subtitle">请使用系统管理员分配的账号登录，角色与权限将在认证成功后自动加载。</p>
          <form onSubmit={submit}>
            <Field label="租户">
              <div className="input-with-icon"><Building2 size={18} /><input value={tenantId} onChange={(e) => setTenantId(e.target.value)} /></div>
            </Field>
            <Field label="邮箱 / 用户名">
              <div className="input-with-icon"><ContactRound size={18} /><input value={username} onChange={(e) => setUsername(e.target.value)} /></div>
            </Field>
            <Field label="密码">
              <div className="input-with-icon"><LockKeyhole size={18} /><input type={showPassword ? 'text' : 'password'} value={password} onChange={(e) => setPassword(e.target.value)} /><button type="button" onClick={() => setShowPassword(!showPassword)}>{showPassword ? <EyeOff /> : <Eye />}</button></div>
            </Field>
            {error && <div className="form-error">{error}</div>}
            <div className="login-options"><label><input type="checkbox" defaultChecked />保持登录状态</label><button type="button">忘记密码？</button></div>
            <Button ref={submitRef} className="login-submit" type="submit" disabled={loading}>{loading ? '登录中…' : '登录系统'}</Button>
          </form>
          <div className="security-note"><ShieldCheck size={15} />已启用企业级身份认证与传输加密</div>
        </div>
        <footer>© 2026 {APP_NAME} · 隐私政策 · 服务条款</footer>
      </section>
    </div>
  )
}

export function SetupPage({ onSetup, logo }) {
  const [form, setForm] = useState({ tenantId: 'default', username: '', displayName: '', password: '', confirmPassword: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

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
      await onSetup({
        tenantId: form.tenantId,
        username: form.username,
        displayName: form.displayName,
        password: form.password,
      })
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
          <Field label="租户">
            <input value={form.tenantId} onChange={(e) => setForm({ ...form, tenantId: e.target.value })} />
          </Field>
          <Field label="超管用户名" required>
            <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          </Field>
          <Field label="显示名称">
            <input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          </Field>
          <Field label="登录密码" required>
            <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </Field>
          <Field label="确认密码" required>
            <input type="password" value={form.confirmPassword} onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} />
          </Field>
          {error && <div className="form-error">{error}</div>}
          <Button className="login-submit" type="submit" disabled={loading}>{loading ? '初始化中…' : '完成初始化'}</Button>
        </form>
      </div>
    </div>
  )
}
