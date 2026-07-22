import { useState } from 'react'
import {
  Bell, Bot, ChevronDown, HelpCircle, LogOut, Menu, Search, ShieldCheck,
  Sparkles, Target, X, Zap,
} from 'lucide-react'
import { Badge, BrandLogo, Button, Card } from '../components'
import { backendAddressLabel } from '../config/env'

export function AppLayout({
  children,
  routeKey,
  routeGroups,
  onNavigate,
  can,
  currentUser,
  currentRole,
  onLogout,
  logo,
  onNotify,
}) {
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
          {routeGroups.map((group) => {
            const visible = group.items.filter((item) => can(item.permission))
            if (!visible.length) return null
            return (
              <div className="nav-group" key={group.label}>
                <div className="nav-label">{group.label}</div>
                {visible.map(({ key, label, icon: Icon }) => (
                  <button className={`nav-item ${routeKey === key ? 'active' : ''}`} key={key} onClick={() => go(key)}>
                    <Icon size={19} />
                    <span>{label}</span>
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
          {can('crm:lead:create') && (
            <Button icon={Target} onClick={() => { go('leads'); onNotify('已打开线索创建入口') }}>
              新建线索
            </Button>
          )}
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
                    <small>后台：{backendAddressLabel()}</small>
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
          <div><strong>AI 营销洞察</strong><small>基于真实业务接口接入</small></div>
          <button className="icon-button" onClick={() => setAssistantOpen(false)}><X size={18} /></button>
        </div>
        <div className="assistant-body">
          <div className="assistant-intro"><Bot size={24} /><p>你好，{currentRole.name}。当前抽屉已接入统一布局，后续可直接对接 Agent 接口。</p></div>
          <Card className="insight-card">
            <Badge tone="info">系统提示</Badge>
            <h3>AI 能力入口已保留</h3>
            <p>这里不构造假数据。后续接入智能体接口后，再展示真实洞察、任务和建议。</p>
          </Card>
          <div className="ai-disclaimer"><ShieldCheck size={15} />AI 建议需要基于真实业务数据生成</div>
        </div>
        <div className="assistant-input">
          <input placeholder="向 AI 营销助手提问…" />
          <button onClick={() => onNotify('AI 对话接口待接入', 'info')}><Sparkles size={17} /></button>
        </div>
      </aside>
    </div>
  )
}
