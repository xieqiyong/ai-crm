import { useEffect, useMemo, useState } from 'react'
import { api, clearAuth, getStoredAuth, saveAuth } from './api'
import { AppShell, LoginPage, SetupPage, Toast } from './components'
import {
  CustomerPage,
  DashboardPage,
  LeadsPage,
  OpportunitiesPage,
  OrganizationPage,
  SettingsPage,
  SimpleModulePage,
} from './pages'

export const APP_NAME = '智能营销管理系统'

const pagePermissions = {
  dashboard: 'menu.dashboard',
  leads: 'menu.leads',
  customers: 'menu.customers',
  opportunities: 'menu.opportunities',
  followups: 'menu.followups',
  tasks: 'menu.tasks',
  assistant: 'menu.assistant',
  knowledge: 'menu.knowledge',
  organization: 'menu.organization',
  settings: 'menu.settings',
}

const getStored = (key, fallback) => {
  try {
    const value = localStorage.getItem(key)
    return value === null ? fallback : value
  } catch {
    return fallback
  }
}

const dataScopeLabel = {
  ALL: '全部数据',
  DEPARTMENT_AND_CHILD: '部门及下级数据',
  DEPARTMENT: '部门数据',
  SELF: '本人数据',
}

export default function App() {
  const storedAuth = getStoredAuth()
  const [booting, setBooting] = useState(true)
  const [installed, setInstalled] = useState(true)
  const [currentUser, setCurrentUser] = useState(storedAuth?.user || null)
  const [page, setPage] = useState(() => window.location.hash.replace('#/', '') || 'dashboard')
  const [theme, setTheme] = useState(() => getStored('crm.theme', 'light'))
  const [accent, setAccent] = useState(() => getStored('crm.accent', '#f45b0b'))
  const [density, setDensity] = useState(() => getStored('crm.density', 'comfortable'))
  const [logo, setLogo] = useState(() => getStored('crm.logo', ''))
  const [toast, setToast] = useState(null)

  const currentRole = useMemo(() => ({
    name: currentUser?.displayName || currentUser?.username || '用户',
    label: dataScopeLabel[currentUser?.dataScope] || '未授权',
    department: currentUser?.tenantId || 'default',
    permissions: currentUser?.permissions || [],
  }), [currentUser])

  const can = useMemo(() => (permission) => {
    const actionPermissions = currentUser?.permissions || []
    const menuPermissions = currentUser?.menuPermissions || []
    return actionPermissions.includes('*')
      || actionPermissions.includes(permission)
      || menuPermissions.includes(permission)
  }, [currentUser])

  useEffect(() => {
    let mounted = true
    const bootstrap = async () => {
      try {
        const status = await api.installStatus()
        if (!mounted) return
        setInstalled(status.installed)
        if (!status.installed) {
          clearAuth()
          setCurrentUser(null)
          setBooting(false)
          return
        }
        const auth = getStoredAuth()
        if (auth?.token) {
          const user = await api.me()
          if (!mounted) return
          const nextUser = { ...auth.user, ...user, token: auth.token }
          saveAuth(nextUser)
          setCurrentUser(nextUser)
        }
      } catch (error) {
        clearAuth()
        if (mounted) setCurrentUser(null)
      } finally {
        if (mounted) setBooting(false)
      }
    }
    bootstrap()
    return () => {
      mounted = false
    }
  }, [])

  useEffect(() => {
    const applyTheme = () => {
      const resolved = theme === 'system'
        ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
        : theme
      document.documentElement.dataset.theme = resolved
      document.documentElement.dataset.density = density
      document.documentElement.style.setProperty('--brand', accent)
    }
    applyTheme()
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    media.addEventListener?.('change', applyTheme)
    return () => media.removeEventListener?.('change', applyTheme)
  }, [theme, accent, density])

  useEffect(() => {
    const onHash = () => setPage(window.location.hash.replace('#/', '') || 'dashboard')
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  useEffect(() => {
    if (currentUser && !can(pagePermissions[page] || 'menu.dashboard')) navigate('dashboard')
  }, [page, currentUser, can])

  const notify = (message, tone = 'success') => {
    setToast({ message, tone, id: Date.now() })
  }

  const navigate = (nextPage) => {
    window.location.hash = `/${nextPage}`
    setPage(nextPage)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const login = async (payload) => {
    const response = await api.login(payload)
    saveAuth(response)
    setCurrentUser(response)
    navigate('dashboard')
  }

  const setup = async (payload) => {
    const response = await api.setupSuperAdmin(payload)
    saveAuth(response)
    setInstalled(true)
    setCurrentUser(response)
    navigate('dashboard')
  }

  const logout = () => {
    clearAuth()
    setCurrentUser(null)
  }

  const updatePreferences = (next) => {
    if (next.theme) {
      setTheme(next.theme)
      localStorage.setItem('crm.theme', next.theme)
    }
    if (next.accent) {
      setAccent(next.accent)
      localStorage.setItem('crm.accent', next.accent)
    }
    if (next.density) {
      setDensity(next.density)
      localStorage.setItem('crm.density', next.density)
    }
    if (Object.prototype.hasOwnProperty.call(next, 'logo')) {
      setLogo(next.logo)
      if (next.logo) localStorage.setItem('crm.logo', next.logo)
      else localStorage.removeItem('crm.logo')
    }
  }

  if (booting) {
    return <div className="boot-screen">正在检查系统状态…</div>
  }

  if (!installed) {
    return <SetupPage onSetup={setup} logo={logo} />
  }

  if (!currentUser) {
    return <LoginPage onLogin={login} logo={logo} />
  }

  const shared = { can, navigate, notify, currentUser, currentRole }
  const pages = {
    dashboard: <DashboardPage {...shared} />,
    leads: <LeadsPage {...shared} />,
    customers: <CustomerPage {...shared} />,
    opportunities: <OpportunitiesPage {...shared} />,
    followups: <SimpleModulePage type="followups" {...shared} />,
    tasks: <SimpleModulePage type="tasks" {...shared} />,
    assistant: <SimpleModulePage type="assistant" {...shared} />,
    knowledge: <SimpleModulePage type="knowledge" {...shared} />,
    organization: <OrganizationPage {...shared} />,
    settings: <SettingsPage {...shared} preferences={{ theme, accent, density, logo }} onUpdate={updatePreferences} />,
  }

  return (
    <>
      <AppShell
        page={page}
        onNavigate={navigate}
        can={can}
        currentUser={currentUser}
        currentRole={currentRole}
        onLogout={logout}
        logo={logo}
        onNotify={notify}
      >
        {pages[page] || pages.dashboard}
      </AppShell>
      {toast && <Toast key={toast.id} {...toast} onClose={() => setToast(null)} />}
    </>
  )
}
