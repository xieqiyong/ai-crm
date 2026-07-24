import { useEffect, useState } from 'react'
import {
  Bell, Bot, ChevronDown, HelpCircle, LogOut, Menu, Search, ShieldCheck,
  Sparkles, X, Zap,
} from 'lucide-react'
import { Badge, BrandLogo, Card } from '../components'
import { api } from '../api'
import { backendAddressLabel } from '../config/env'

function formatUsage(user, tokenUsage) {
  if (tokenUsage?.dailyTokenLimit > 0) {
    const used = Number(tokenUsage.totalTokenCount || 0) + Number(tokenUsage.reservedTokenCount || 0)
    const limit = Number(tokenUsage.dailyTokenLimit)
    if (Number.isFinite(used) && Number.isFinite(limit) && limit > 0) {
      const percent = Math.max(0, Math.min((used / limit) * 100, 999))
      return `${percent.toFixed(percent % 1 === 0 ? 0 : 1)}%`
    }
  }
  if (tokenUsage && Number(tokenUsage.dailyTokenLimit || 0) <= 0) return '不限额'
  const raw = user?.usagePercent
    ?? user?.aiUsagePercent
    ?? user?.quotaUsagePercent
    ?? user?.usageRate
  if (raw === undefined || raw === null || raw === '') return '--'
  const numberValue = Number(raw)
  if (!Number.isFinite(numberValue)) return String(raw)
  const percent = numberValue <= 1 ? numberValue * 100 : numberValue
  return `${Math.max(0, percent).toFixed(percent % 1 === 0 ? 0 : 1)}%`
}

function resolveUnreadCount(user) {
  const raw = user?.unreadNotificationCount
    ?? user?.notificationUnreadCount
    ?? user?.unreadMessageCount
    ?? user?.noticeUnreadCount
    ?? 0
  const numberValue = Number(raw)
  if (!Number.isFinite(numberValue)) return 0
  return Math.max(0, Math.floor(numberValue))
}

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
  const [tokenUsage, setTokenUsage] = useState(null)

  useEffect(() => {
    let canceled = false
    if (!currentUser) {
      setTokenUsage(null)
      return () => {
        canceled = true
      }
    }
    api.agent.tokenToday()
      .then((data) => {
        if (!canceled) setTokenUsage(data)
      })
      .catch(() => {
        if (!canceled) setTokenUsage(null)
      })
    return () => {
      canceled = true
    }
  }, [currentUser?.id, currentUser?.userId, currentUser?.username])

  const go = (next) => {
    onNavigate(next)
    setMobileOpen(false)
  }
  const activeRouteKey = String(routeKey || '').startsWith('customers/detail/') ? 'customers' : routeKey
  const usageText = formatUsage(currentUser, tokenUsage)
  const unreadCount = resolveUnreadCount(currentUser)
  const unreadBadge = unreadCount > 99 ? '99+' : String(unreadCount)

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
                  <button className={`nav-item ${activeRouteKey === key ? 'active' : ''}`} key={key} onClick={() => go(key)}>
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
          <div className="sidebar-profile-wrap">
            <button className="sidebar-profile-button" onClick={() => setProfileOpen(!profileOpen)}>
              <span className="avatar">{currentRole.name.slice(0, 1)}</span>
              <span className="sidebar-profile-copy">
                <strong>{currentRole.name}</strong>
                <small>{currentRole.label}</small>
              </span>
              <span className="usage-pill" title={`AI用量 ${usageText}`}>
                <Zap size={12} />
                <span className="usage-pill-label">AI用量</span>
                <span className="usage-pill-value">{usageText}</span>
              </span>
              <ChevronDown size={14} />
            </button>
            {profileOpen && (
              <div className="profile-menu sidebar-profile-menu">
                <div className="profile-menu-head">
                  <strong>{currentUser.username}</strong>
                </div>
                <button className="logout" onClick={onLogout}><LogOut size={16} />退出登录</button>
              </div>
            )}
          </div>
        </div>
      </aside>

      <div className="app-main">
        <header className="topbar">
          <button className="icon-button menu-button" onClick={() => setMobileOpen(true)} aria-label="打开菜单"><Menu size={21} /></button>
          <div className="global-search"><Search size={18} /><input aria-label="全局搜索" placeholder="搜索线索、客户或商机…" /></div>
          <div className="topbar-actions">
            {can('crm:assistant:use') && <button className="ai-top-button" onClick={() => setAssistantOpen(true)}><Sparkles size={17} />AI 洞察</button>}
            <button
              className="icon-button notification-button"
              onClick={() => onNotify(unreadCount > 0 ? `有 ${unreadCount} 条未读消息` : '暂无未读消息', 'info')}
              aria-label={`通知，${unreadCount} 条未读`}
            >
              <Bell size={19} />
              <span className="notification-count">{unreadBadge}</span>
            </button>
            <button className="icon-button hide-mobile" onClick={() => onNotify('帮助中心正在完善中', 'info')} aria-label="帮助"><HelpCircle size={19} /></button>
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
