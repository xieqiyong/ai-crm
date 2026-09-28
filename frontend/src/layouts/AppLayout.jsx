import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Bell, Bot, CalendarDays, ChevronDown, HelpCircle, LogOut, Menu, PanelLeftClose, PanelLeftOpen, Search, Sparkles, X, Zap,
} from 'lucide-react'
import { Badge, BrandLogo, Button, Drawer } from '../components'
import { api } from '../api'
import { MarketingAssistantPage } from '../features/assistant/MarketingAssistantPage'

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
  const [assistantOpen, setAssistantOpen] = useState(false)
  const [assistantMenuOpen, setAssistantMenuOpen] = useState(false)
  const [assistantAgents, setAssistantAgents] = useState([])
  const [assistantAgentId, setAssistantAgentId] = useState(null)
  const [globalSearchKeyword, setGlobalSearchKeyword] = useState('')
  const [globalSearchOpen, setGlobalSearchOpen] = useState(false)
  const [globalSearchLoading, setGlobalSearchLoading] = useState(false)
  const [globalSearchGroups, setGlobalSearchGroups] = useState([])
  const [sidebarCollapsed, setSidebarCollapsed] = useState(readSidebarCollapsed)
  const [collapsedGroups, setCollapsedGroups] = useState(readCollapsedGroups)
  const unreadCountRef = useRef(resolveUnreadCount(currentUser))
  const notificationOpenRef = useRef(false)
  const assistantMenuRef = useRef(null)
  const globalSearchRef = useRef(null)
  const globalSearchRequestRef = useRef(0)
  const calendarReturnRouteRef = useRef('dashboard')
  const canUseAssistant = can('crm:assistant:use') || can('menu.assistant')
  const canViewCalendar = can('crm:task:view') || can('menu.tasks')
  const canSearchLead = can('crm:lead:view') || can('menu.leads')
  const canSearchCustomer = can('crm:customer:view') || can('menu.customers')
  const canSearchOpportunity = can('crm:opportunity:view') || can('menu.opportunities')

  const refreshAssistantAgents = useCallback(async () => {
    if (!currentUser || !canUseAssistant) {
      setAssistantAgents([])
      setAssistantAgentId(null)
      return
    }
    try {
      const rows = await api.agent.assistantAgents()
      const values = rows || []
      setAssistantAgents(values)
      setAssistantAgentId((current) => (
        values.some((item) => String(item.id) === String(current)) ? current : values[0]?.id || null
      ))
    } catch {
      setAssistantAgents([])
    }
  }, [currentUser?.id, currentUser?.userId, currentUser?.username, canUseAssistant])

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
    refreshAssistantAgents()
  }, [refreshAssistantAgents])

  useEffect(() => {
    if (!assistantMenuOpen) return undefined
    const closeMenu = (event) => {
      if (!assistantMenuRef.current?.contains(event.target)) setAssistantMenuOpen(false)
    }
    document.addEventListener('mousedown', closeMenu)
    return () => document.removeEventListener('mousedown', closeMenu)
  }, [assistantMenuOpen])

  useEffect(() => {
    const closeSearch = (event) => {
      if (!globalSearchRef.current?.contains(event.target)) setGlobalSearchOpen(false)
    }
    document.addEventListener('mousedown', closeSearch)
    return () => document.removeEventListener('mousedown', closeSearch)
  }, [])

  useEffect(() => {
    const keyword = globalSearchKeyword.trim()
    const requestId = ++globalSearchRequestRef.current
    if (!keyword) {
      setGlobalSearchGroups([])
      setGlobalSearchLoading(false)
      return undefined
    }
    setGlobalSearchLoading(true)
    const timer = window.setTimeout(async () => {
      const searches = []
      if (canSearchLead) {
        searches.push({
          type: 'LEAD',
          label: '线索',
          load: () => api.lead.page({ keyword, pageNo: 1, pageSize: 5 }),
        })
      }
      if (canSearchCustomer) {
        searches.push({
          type: 'CUSTOMER',
          label: '客户',
          load: () => api.customer.page({ keyword, pageNo: 1, pageSize: 5 }),
        })
      }
      if (canSearchOpportunity) {
        searches.push({
          type: 'OPPORTUNITY',
          label: '商机',
          load: () => api.opportunity.page({ keyword, pageNo: 1, pageSize: 5 }),
        })
      }
      if (!searches.length) {
        setGlobalSearchGroups([])
        setGlobalSearchLoading(false)
        return
      }
      const results = await Promise.allSettled(searches.map((item) => item.load()))
      if (requestId !== globalSearchRequestRef.current) return
      setGlobalSearchGroups(searches.map((search, index) => {
        const result = results[index]
        const data = result.status === 'fulfilled' ? result.value : null
        return {
          type: search.type,
          label: search.label,
          total: Number(data?.total || 0),
          records: data?.records || [],
        }
      }))
      setGlobalSearchLoading(false)
      setGlobalSearchOpen(true)
    }, 320)
    return () => window.clearTimeout(timer)
  }, [globalSearchKeyword, canSearchLead, canSearchCustomer, canSearchOpportunity])

  useEffect(() => {
    notificationOpenRef.current = notificationOpen
  }, [notificationOpen])

  const refreshNotificationState = useCallback(async ({ allowPopup = true } = {}) => {
    try {
      const data = await api.notification.unreadCount()
      const nextCount = Number(data?.unreadCount || 0)
      unreadCountRef.current = nextCount
      setUnreadCount(nextCount)
      if (nextCount <= 0) {
        setNotificationPopup(null)
        return
      }
      if (!allowPopup) {
        setNotificationPopup(null)
        return
      }
      const page = await api.notification.page({ pageNo: 1, pageSize: 10 })
      const latest = (page?.records || []).find((item) => !item.readAt)
      setNotificationPopup((current) => {
        if (!latest) return null
        return current?.id === latest.id ? current : latest
      })
    } catch {
      const nextCount = resolveUnreadCount(currentUser)
      unreadCountRef.current = nextCount
      setUnreadCount(nextCount)
    }
  }, [currentUser])

  useEffect(() => {
    let timer
    const loadUnread = () => {
      refreshNotificationState({ allowPopup: !notificationOpenRef.current }).finally(() => undefined)
    }
    loadUnread()
    timer = window.setInterval(loadUnread, 10000)
    return () => {
      window.clearInterval(timer)
    }
  }, [refreshNotificationState])

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
    setNotificationPopup(null)
    loadNotifications()
  }

  const closeNotifications = () => {
    setNotificationOpen(false)
    window.setTimeout(() => {
      refreshNotificationState({ allowPopup: true })
    }, 0)
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
      window.setTimeout(() => {
        refreshNotificationState({ allowPopup: !notificationOpenRef.current })
      }, 0)
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

  const openGlobalSearchResult = (type, row) => {
    setGlobalSearchOpen(false)
    setGlobalSearchKeyword('')
    if (type === 'LEAD') {
      onNavigate(`leads/detail/${encodeURIComponent(row.id)}`)
      return
    }
    if (type === 'CUSTOMER') {
      onNavigate(`customers/detail/${encodeURIComponent(row.id)}`)
      return
    }
    onNavigate(`opportunities?openId=${encodeURIComponent(row.id)}`)
  }

  const firstGlobalSearchResult = () => {
    for (const group of globalSearchGroups) {
      if (group.records?.length) return { type: group.type, row: group.records[0] }
    }
    return null
  }
  const rawRouteKey = String(routeKey || '')
  const baseRouteKey = rawRouteKey.split('?')[0]
  const activeRouteKey = rawRouteKey.startsWith('customers/detail/')
    ? 'customers'
    : rawRouteKey.startsWith('leads/detail/')
      ? 'leads'
      : rawRouteKey.startsWith('followup-attention')
        ? 'dashboard'
      : rawRouteKey === 'calendar'
        ? 'tasks'
        : baseRouteKey
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
          <form
            className="global-search"
            ref={globalSearchRef}
            onSubmit={(event) => {
              event.preventDefault()
              const first = firstGlobalSearchResult()
              if (first) openGlobalSearchResult(first.type, first.row)
            }}
          >
            <Search size={18} />
            <input
              aria-label="全局搜索"
              value={globalSearchKeyword}
              placeholder="搜索线索、客户或商机…"
              autoComplete="off"
              onFocus={() => {
                if (globalSearchKeyword.trim()) setGlobalSearchOpen(true)
              }}
              onChange={(event) => {
                setGlobalSearchKeyword(event.target.value)
                setGlobalSearchOpen(Boolean(event.target.value.trim()))
              }}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  setGlobalSearchOpen(false)
                  event.currentTarget.blur()
                }
              }}
            />
            {globalSearchKeyword && (
              <button
                type="button"
                className="global-search-clear"
                onClick={() => {
                  setGlobalSearchKeyword('')
                  setGlobalSearchGroups([])
                  setGlobalSearchOpen(false)
                }}
                aria-label="清空搜索"
              ><X size={15} /></button>
            )}
            {globalSearchOpen && (
              <GlobalSearchDropdown
                keyword={globalSearchKeyword.trim()}
                loading={globalSearchLoading}
                groups={globalSearchGroups}
                onOpen={openGlobalSearchResult}
              />
            )}
          </form>
          <div className="topbar-actions">
            {canViewCalendar && (
              <button
                type="button"
                className={`topbar-calendar-trigger ${rawRouteKey === 'calendar' ? 'active' : ''}`}
                onClick={() => {
                  if (rawRouteKey === 'calendar') {
                    onNavigate(calendarReturnRouteRef.current || 'dashboard')
                    return
                  }
                  calendarReturnRouteRef.current = routeKey || 'dashboard'
                  setAssistantOpen(false)
                  setAssistantMenuOpen(false)
                  onNavigate('calendar')
                }}
                title={rawRouteKey === 'calendar' ? '收起销售日历' : '打开销售日历'}
              >
                <CalendarDays size={16} />
                <span>日历</span>
              </button>
            )}
            {canUseAssistant && (
              <div className="topbar-assistant" ref={assistantMenuRef}>
                <button
                  type="button"
                  className={`topbar-assistant-trigger ${assistantOpen ? 'active' : ''}`}
                  onClick={() => {
                    if (assistantOpen) {
                      setAssistantOpen(false)
                      setAssistantMenuOpen(false)
                      return
                    }
                    const nextOpen = !assistantMenuOpen
                    setAssistantMenuOpen(nextOpen)
                    if (nextOpen) refreshAssistantAgents()
                  }}
                  aria-expanded={assistantOpen || assistantMenuOpen}
                  title={assistantOpen ? '收起 AI 助手' : '选择 AI 智能体'}
                >
                  <Sparkles size={16} />
                  <span>AI 助手</span>
                  <ChevronDown size={14} />
                </button>
                {assistantMenuOpen && (
                  <div className="topbar-assistant-menu">
                    <div className="topbar-assistant-menu-head">
                      <strong>选择智能体</strong>
                      <small>选择后在右侧继续会话</small>
                    </div>
                    <div className="topbar-assistant-menu-list">
                      {assistantAgents.map((agent) => (
                        <button
                          type="button"
                          className={String(agent.id) === String(assistantAgentId) ? 'active' : ''}
                          key={agent.id}
                          onClick={() => {
                            setAssistantAgentId(agent.id)
                            setAssistantOpen(true)
                            setAssistantMenuOpen(false)
                          }}
                        >
                          <span><Bot size={16} /></span>
                          <div><strong>{agent.name}</strong><small>{agent.sceneName || agent.sceneCode || '通用场景'}</small></div>
                        </button>
                      ))}
                      {!assistantAgents.length && (
                        <div className="topbar-assistant-empty">暂无允许前台展示的智能体</div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
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
        onClose={closeNotifications}
      />
      <NotificationPopup
        item={notificationPopup}
        onRead={readNotification}
        onOpen={() => {
          setNotificationPopup(null)
          openNotifications()
        }}
      />
      {canUseAssistant && (
        <aside className={`global-agent-drawer ${assistantOpen ? 'open' : ''}`} aria-hidden={!assistantOpen}>
          <MarketingAssistantPage
            compact
            availableAgents={assistantAgents}
            preferredAgentId={assistantAgentId}
            onAgentChange={(agent) => setAssistantAgentId(agent?.id || null)}
            onClose={() => setAssistantOpen(false)}
            navigate={onNavigate}
            notify={onNotify}
            can={can}
          />
        </aside>
      )}
    </div>
  )
}

function GlobalSearchDropdown({ keyword, loading, groups, onOpen }) {
  const visibleGroups = groups.filter((group) => group.records?.length)
  return (
    <div className="global-search-dropdown">
      <div className="global-search-dropdown-head">
        <strong>全局搜索</strong>
        <span>{loading ? '正在查询…' : `“${keyword}”`}</span>
      </div>
      <div className="global-search-results">
        {visibleGroups.map((group) => (
          <section key={group.type}>
            <div className="global-search-group-head">
              <span>{group.label}</span>
              <small>共 {group.total} 条</small>
            </div>
            {group.records.map((row) => (
              <button type="button" key={row.id} onClick={() => onOpen(group.type, row)}>
                <span className={`global-search-type ${group.type.toLowerCase()}`}>{group.label.slice(0, 1)}</span>
                <div>
                  <strong>{globalSearchTitle(group.type, row)}</strong>
                  <small>{globalSearchDescription(group.type, row)}</small>
                </div>
                <em>查看</em>
              </button>
            ))}
          </section>
        ))}
        {!loading && !visibleGroups.length && (
          <div className="global-search-empty">
            <Search size={23} />
            <b>未找到匹配数据</b>
            <span>可以尝试公司名称、联系人、手机号或商机名称</span>
          </div>
        )}
        {loading && !visibleGroups.length && (
          <div className="global-search-empty loading">
            <Search size={23} />
            <b>正在查询业务数据</b>
          </div>
        )}
      </div>
      <div className="global-search-dropdown-foot">按 Enter 可打开第一条搜索结果</div>
    </div>
  )
}

function globalSearchTitle(type, row) {
  if (type === 'LEAD') return row.companyName || row.name || '未命名线索'
  if (type === 'CUSTOMER') return row.name || '未命名客户'
  return row.name || '未命名商机'
}

function globalSearchDescription(type, row) {
  let values
  if (type === 'LEAD') {
    values = [row.name, row.phone, row.productName]
  } else if (type === 'CUSTOMER') {
    values = [row.contactName, row.contactPhone, row.productName]
  } else {
    values = [row.customerName, row.ownerName]
  }
  return values.filter(Boolean).join(' · ') || '暂无补充信息'
}

function NotificationPopup({ item, onRead, onOpen }) {
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
        <button type="button" onClick={() => onRead(item)}>标记已读</button>
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
