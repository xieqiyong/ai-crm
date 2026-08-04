import { useEffect, useRef, useState } from 'react'
import {
  Bell, ChevronDown, HelpCircle, LogOut, Menu, PanelLeftClose, PanelLeftOpen, Search, Zap,
} from 'lucide-react'
import { Badge, BrandLogo, Button, Drawer } from '../components'
import { api } from '../api'

const SIDEBAR_COLLAPSED_KEY = 'crm.sidebar.collapsed'
const NAV_GROUPS_COLLAPSED_KEY = 'crm.sidebar.collapsedGroups'

function readSidebarCollapsed() {
  if (typeof window === 'undefined') return false
  try {
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}

function readCollapsedGroups() {
  if (typeof window === 'undefined') return []
  try {
    const value = JSON.parse(window.localStorage.getItem(NAV_GROUPS_COLLAPSED_KEY) || '[]')
    return Array.isArray(value) ? value.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
}

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
  const [notificationOpen, setNotificationOpen] = useState(false)
  const [notifications, setNotifications] = useState([])
  const [notificationLoading, setNotificationLoading] = useState(false)
  const [unreadCount, setUnreadCount] = useState(resolveUnreadCount(currentUser))
  const [notificationPopup, setNotificationPopup] = useState(null)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(readSidebarCollapsed)
  const [collapsedGroups, setCollapsedGroups] = useState(readCollapsedGroups)
  const unreadCountRef = useRef(resolveUnreadCount(currentUser))
  const unreadInitializedRef = useRef(false)
  const popupShownIdRef = useRef('')

  useEffect(() => {
    try {
      window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, sidebarCollapsed ? '1' : '0')
    } catch {
      return undefined
    }
    return undefined
  }, [sidebarCollapsed])

  useEffect(() => {
    try {
      window.localStorage.setItem(NAV_GROUPS_COLLAPSED_KEY, JSON.stringify(collapsedGroups))
    } catch {
      return undefined
    }
    return undefined
  }, [collapsedGroups])

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

  useEffect(() => {
    let canceled = false
    let timer
    const showLatestNotificationPopup = async () => {
      try {
        const data = await api.notification.page({ pageNo: 1, pageSize: 5 })
        if (canceled) return
        const latest = (data?.records || []).find((item) => !item.readAt)
        if (!latest || String(latest.id) === popupShownIdRef.current) return
        popupShownIdRef.current = String(latest.id)
        setNotificationPopup(latest)
      } catch {
        return
      }
    }
    const loadUnread = () => {
      api.notification.unreadCount()
        .then((data) => {
          if (canceled) return
          const nextCount = Number(data?.unreadCount || 0)
          const previousCount = unreadCountRef.current
          unreadCountRef.current = nextCount
          setUnreadCount(nextCount)
          if (unreadInitializedRef.current && nextCount > previousCount) {
            showLatestNotificationPopup()
          }
          unreadInitializedRef.current = true
        })
        .catch(() => {
          if (!canceled) {
            const nextCount = resolveUnreadCount(currentUser)
            unreadCountRef.current = nextCount
            setUnreadCount(nextCount)
            unreadInitializedRef.current = true
          }
        })
    }
    loadUnread()
    timer = window.setInterval(loadUnread, 30000)
    return () => {
      canceled = true
      window.clearInterval(timer)
    }
  }, [currentUser?.id, currentUser?.userId, currentUser?.username])

  const loadNotifications = async () => {
    setNotificationLoading(true)
    try {
      const data = await api.notification.page({ pageNo: 1, pageSize: 30 })
      setNotifications(data?.records || [])
    } catch (err) {
      onNotify(err.message || '通知加载失败', 'info')
    } finally {
      setNotificationLoading(false)
    }
  }

  const openNotifications = () => {
    setNotificationOpen(true)
    loadNotifications()
  }

  const readNotification = async (item) => {
    if (item.readAt) return
    try {
      await api.notification.read(item.id)
      setNotifications((values) => values.map((value) => (
        value.id === item.id ? { ...value, readAt: new Date().toISOString() } : value
      )))
      const nextCount = Math.max(0, unreadCountRef.current - 1)
      unreadCountRef.current = nextCount
      setUnreadCount(nextCount)
      if (notificationPopup?.id === item.id) {
        setNotificationPopup(null)
      }
    } catch (err) {
      onNotify(err.message || '通知状态更新失败', 'info')
    }
  }

  const readAllNotifications = async () => {
    try {
      await api.notification.readAll()
      const readAt = new Date().toISOString()
      setNotifications((values) => values.map((value) => ({ ...value, readAt })))
      unreadCountRef.current = 0
      setUnreadCount(0)
      setNotificationPopup(null)
      onNotify('全部通知已标记为已读')
    } catch (err) {
      onNotify(err.message || '通知状态更新失败', 'info')
    }
  }

  const go = (next) => {
    onNavigate(next)
    setMobileOpen(false)
  }

  const toggleGroup = (label) => {
    setCollapsedGroups((values) => (
      values.includes(label)
        ? values.filter((item) => item !== label)
        : [...values, label]
    ))
  }
  const rawRouteKey = String(routeKey || '')
  const activeRouteKey = rawRouteKey.startsWith('customers/detail/')
    ? 'customers'
    : rawRouteKey.startsWith('leads/detail/')
      ? 'leads'
      : routeKey
  const usageText = formatUsage(currentUser, tokenUsage)
  const unreadBadge = unreadCount > 99 ? '99+' : String(unreadCount)

  return (
    <div className={`app-shell ${sidebarCollapsed ? 'sidebar-is-collapsed' : ''}`}>
      {mobileOpen && <div className="mobile-scrim" onClick={() => setMobileOpen(false)} />}
      <aside className={`sidebar ${sidebarCollapsed ? 'desktop-collapsed' : ''} ${mobileOpen ? 'mobile-open' : ''}`}>
        <div className="sidebar-brand">
          <BrandLogo logo={logo} />
          <button
            type="button"
            className="icon-button sidebar-collapse-button"
            onClick={() => setSidebarCollapsed(true)}
            aria-label="收起左侧导航"
            title="收起左侧导航"
          >
            <PanelLeftClose size={19} />
          </button>
        </div>
        <nav className="nav-area" aria-label="主导航">
          {routeGroups.map((group) => {
            const visible = group.items.filter((item) => can(item.permission))
            if (!visible.length) return null
            const collapsed = collapsedGroups.includes(group.label)
            return (
              <div className={`nav-group ${collapsed ? 'collapsed' : ''}`} key={group.label}>
                <button
                  type="button"
                  className="nav-group-toggle"
                  onClick={() => toggleGroup(group.label)}
                  aria-expanded={!collapsed}
                >
                  <span>{group.label}</span>
                  <ChevronDown size={15} />
                </button>
                {!collapsed && (
                  <div className="nav-group-items">
                    {visible.map(({ key, label, icon: Icon }) => (
                      <button className={`nav-item ${activeRouteKey === key ? 'active' : ''}`} key={key} onClick={() => go(key)}>
                        <Icon size={19} />
                        <span>{label}</span>
                        {key === 'assistant' && <i className="nav-live" />}
                      </button>
                    ))}
                  </div>
                )}
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
          {sidebarCollapsed && (
            <button
              type="button"
              className="icon-button sidebar-expand-button"
              onClick={() => setSidebarCollapsed(false)}
              aria-label="展开左侧导航"
              title="展开左侧导航"
            >
              <PanelLeftOpen size={20} />
            </button>
          )}
          <div className="global-search"><Search size={18} /><input aria-label="全局搜索" placeholder="搜索线索、客户或商机…" /></div>
          <div className="topbar-actions">
            <span className="usage-pill topbar-usage" title={`AI用量 ${usageText}`}>
              <Zap size={12} />
              <span className="usage-pill-label">AI用量</span>
              <span className="usage-pill-value">{usageText}</span>
            </span>
            <button
              className="icon-button notification-button"
              onClick={openNotifications}
              aria-label={`通知，${unreadCount} 条未读`}
            >
              <Bell size={19} />
              {unreadCount > 0 && <span className="notification-count">{unreadBadge}</span>}
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

      <NotificationDrawer
        open={notificationOpen}
        records={notifications}
        loading={notificationLoading}
        unreadCount={unreadCount}
        onRead={readNotification}
        onReadAll={readAllNotifications}
        onClose={() => setNotificationOpen(false)}
      />
      <NotificationPopup
        item={notificationPopup}
        onOpen={() => {
          setNotificationPopup(null)
          openNotifications()
        }}
      />
    </div>
  )
}

function NotificationPopup({ item, onOpen }) {
  if (!item) return null
  return (
    <div className={`notification-popup ${item.level === 'WARNING' ? 'danger' : item.level === 'IMPORTANT' ? 'warning' : ''}`}>
      <div className="notification-popup-head">
        <span>{item.level === 'WARNING' ? '风险提醒' : item.level === 'IMPORTANT' ? '重要通知' : '系统通知'}</span>
      </div>
      <b>{item.title}</b>
      <p>{item.content}</p>
      <div className="notification-popup-foot">
        <small>{formatNotificationTime(item.createdAt)}</small>
        <button type="button" onClick={onOpen}>查看</button>
      </div>
    </div>
  )
}

function NotificationDrawer({
  open,
  records,
  loading,
  unreadCount,
  onRead,
  onReadAll,
  onClose,
}) {
  return (
    <Drawer
      open={open}
      title="系统通知"
      onClose={onClose}
      footer={(
        <div className="notification-drawer-footer">
          <span>{unreadCount > 0 ? `${unreadCount} 条未读` : '已全部读完'}</span>
          <Button variant="secondary" disabled={unreadCount <= 0} onClick={onReadAll}>全部已读</Button>
        </div>
      )}
    >
      <div className="notification-list">
        {records.map((item) => (
          <button
            type="button"
            className={item.readAt ? 'read' : 'unread'}
            onClick={() => onRead(item)}
            key={item.id}
          >
            <div>
              <span>{item.senderName || '系统管理员'}</span>
              <Badge tone={item.level === 'WARNING' ? 'danger' : item.level === 'IMPORTANT' ? 'warning' : 'neutral'}>
                {item.level === 'WARNING' ? '风险' : item.level === 'IMPORTANT' ? '重要' : '通知'}
              </Badge>
            </div>
            <b>{item.title}</b>
            <p>{item.content}</p>
            <small>{formatNotificationTime(item.createdAt)}</small>
          </button>
        ))}
        {!loading && !records.length && (
          <div className="notification-empty">
            <Bell size={25} />
            <b>暂无系统通知</b>
            <span>管理员发布的全站通知或私发消息会展示在这里</span>
          </div>
        )}
        {loading && !records.length && <div className="notification-empty"><b>正在加载通知…</b></div>}
      </div>
    </Drawer>
  )
}

function formatNotificationTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}
