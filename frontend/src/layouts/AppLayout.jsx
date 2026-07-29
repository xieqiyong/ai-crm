import { useEffect, useState } from 'react'
import {
  Bell, ChevronDown, HelpCircle, LogOut, Menu, Search, Zap,
} from 'lucide-react'
import { BrandLogo } from '../components'
import { api } from '../api'

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
            <span className="usage-pill topbar-usage" title={`AI用量 ${usageText}`}>
              <Zap size={12} />
              <span className="usage-pill-label">AI用量</span>
              <span className="usage-pill-value">{usageText}</span>
            </span>
            <button
              className="icon-button notification-button"
              onClick={() => onNotify(unreadCount > 0 ? `有 ${unreadCount} 条未读消息` : '暂无未读消息', 'info')}
              aria-label={`通知，${unreadCount} 条未读`}
            >
              <Bell size={19} />
              <span className="notification-count">{unreadBadge}</span>
            </button>
            <button
              className="icon-button hide-mobile"
              onClick={() => onNotify('帮助中心正在完善中', 'info')}
              aria-label="帮助"
            >
              <HelpCircle size={19} />
            </button>
          </div>
        </header>
        <main className="content">{children}</main>
      </div>

    </div>
  )
}
