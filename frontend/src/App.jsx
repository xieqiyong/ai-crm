import { useEffect, useMemo, useState } from 'react'
import { api } from './api'
import { Toast } from './components'
import { DATA_SCOPE_LABELS } from './config/appConfig'
import { LoginPage } from './features/auth/LoginPage'
import { SetupPage } from './features/auth/SetupPage'
import { useHashRoute } from './hooks/useHashRoute'
import { useThemePreferences } from './hooks/useThemePreferences'
import { useToast } from './hooks/useToast'
import { AppLayout } from './layouts'
import { PublicMarketingFormPage } from './features/public/PublicMarketingFormPage'
import { canAccessRoute, DEFAULT_ROUTE, renderRoute, routeGroups } from './router/routes'
import { clearAuth, getStoredAuth, saveAuth } from './store/authStorage'

export default function App() {
  const storedAuth = getStoredAuth()
  const [booting, setBooting] = useState(true)
  const [installed, setInstalled] = useState(true)
  const [currentUser, setCurrentUser] = useState(storedAuth?.user || null)
  const [routeKey, navigate] = useHashRoute(DEFAULT_ROUTE)
  const [preferences, updatePreferences] = useThemePreferences()
  const { toast, notify, closeToast } = useToast()
  const publicFormRoute = String(routeKey || '').startsWith('public/forms/')

  const currentRole = useMemo(() => ({
    name: currentUser?.displayName || currentUser?.username || '用户',
    label: DATA_SCOPE_LABELS[currentUser?.dataScope] || '未授权',
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
        const status = await api.install.status()
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
          const user = await api.auth.me()
          if (!mounted) return
          const nextUser = { ...auth.user, ...user, token: auth.token }
          saveAuth(nextUser)
          setCurrentUser(nextUser)
        }
      } catch {
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
    if (publicFormRoute) return
    if (currentUser && !canAccessRoute(routeKey, can)) {
      navigate(DEFAULT_ROUTE)
    }
  }, [routeKey, currentUser, can, navigate, publicFormRoute])

  const login = async (payload) => {
    const response = await api.auth.login(payload)
    saveAuth(response)
    setCurrentUser(response)
    navigate(DEFAULT_ROUTE)
  }

  const setup = async (payload) => {
    const response = await api.install.setupSuperAdmin(payload)
    saveAuth(response)
    setInstalled(true)
    setCurrentUser(response)
    navigate(DEFAULT_ROUTE)
  }

  const logout = async () => {
    try {
      await api.auth.logout()
    } catch {
      return
    } finally {
      clearAuth()
      setCurrentUser(null)
    }
  }

  if (booting) {
    return <div className="boot-screen">正在检查系统状态…</div>
  }

  if (!installed) {
    return <SetupPage onSetup={setup} logo={preferences.logo} />
  }

  if (publicFormRoute) {
    return (
      <>
        <PublicMarketingFormPage routeKey={routeKey} logo={preferences.logo} />
        {toast && <Toast key={toast.id} {...toast} onClose={closeToast} />}
      </>
    )
  }

  if (!currentUser) {
    return <LoginPage onLogin={login} logo={preferences.logo} />
  }

  const sharedProps = {
    can,
    navigate,
    notify,
    currentUser,
    currentRole,
    preferences,
    onUpdate: updatePreferences,
  }

  return (
    <>
      <AppLayout
        routeKey={routeKey}
        routeGroups={routeGroups}
        onNavigate={navigate}
        can={can}
        currentUser={currentUser}
        currentRole={currentRole}
        onLogout={logout}
        logo={preferences.logo}
        onNotify={notify}
      >
        {renderRoute(routeKey, sharedProps)}
      </AppLayout>
      {toast && <Toast key={toast.id} {...toast} onClose={closeToast} />}
    </>
  )
}
