import {
  BarChart3, BookOpen, Bot, BriefcaseBusiness, ClipboardCheck,
  LayoutDashboard, MessageSquareText, Network, Settings, UserRoundCog, Users,
} from 'lucide-react'
import { ModelConfigPage } from '../features/admin/ModelConfigPage'
import { OrganizationAdminPage } from '../features/admin/OrganizationAdminPage'
import { ChannelPage } from '../features/channel/ChannelPage'
import { CustomerPage } from '../features/customer/CustomerPage'
import {
  DashboardPage,
  LeadsPage,
  OpportunitiesPage,
  SettingsPage,
  SimpleModulePage,
} from '../features/pages'

export const DEFAULT_ROUTE = 'dashboard'

export const routeGroups = [
  {
    label: '业务工作台',
    items: [
      {
        key: 'dashboard',
        label: '工作台',
        icon: LayoutDashboard,
        permission: 'menu.dashboard',
        component: DashboardPage,
      },
      {
        key: 'leads',
        label: '线索管理',
        icon: BarChart3,
        permission: 'menu.leads',
        component: LeadsPage,
      },
      {
        key: 'channels',
        label: '渠道管理',
        icon: Network,
        permission: 'menu.channels',
        component: ChannelPage,
      },
      {
        key: 'customers',
        label: '客户管理',
        icon: Users,
        permission: 'menu.customers',
        component: CustomerPage,
      },
      {
        key: 'opportunities',
        label: '商机管理',
        icon: BriefcaseBusiness,
        permission: 'menu.opportunities',
        component: OpportunitiesPage,
      },
      {
        key: 'followups',
        label: '跟进记录',
        icon: MessageSquareText,
        permission: 'menu.followups',
        component: SimpleModulePage,
        pageType: 'followups',
      },
      {
        key: 'tasks',
        label: '销售任务',
        icon: ClipboardCheck,
        permission: 'menu.tasks',
        component: SimpleModulePage,
        pageType: 'tasks',
      },
    ],
  },
  {
    label: 'AI 与知识',
    items: [
      {
        key: 'assistant',
        label: 'AI 营销助手',
        icon: Bot,
        permission: 'menu.assistant',
        component: SimpleModulePage,
        pageType: 'assistant',
      },
      {
        key: 'knowledge',
        label: '知识库',
        icon: BookOpen,
        permission: 'menu.knowledge',
        component: SimpleModulePage,
        pageType: 'knowledge',
      },
      {
        key: 'model-configs',
        label: '大模型配置',
        icon: Bot,
        permission: 'menu.model_configs',
        component: ModelConfigPage,
      },
    ],
  },
  {
    label: '管理',
    items: [
      {
        key: 'organization',
        label: '组织与权限',
        icon: UserRoundCog,
        permission: 'menu.organization',
        component: OrganizationAdminPage,
      },
      {
        key: 'settings',
        label: '系统设置',
        icon: Settings,
        permission: 'menu.settings',
        component: SettingsPage,
      },
    ],
  },
]

export const routes = routeGroups.flatMap((group) => group.items)

export const routeMap = routes.reduce((map, route) => {
  map[route.key] = route
  return map
}, {})

export function resolveRoute(routeKey) {
  return routeMap[routeKey] || routeMap[DEFAULT_ROUTE]
}

export function canAccessRoute(routeKey, can) {
  const route = resolveRoute(routeKey)
  return can(route.permission)
}

export function renderRoute(routeKey, props) {
  const route = resolveRoute(routeKey)
  const Component = route.component
  if (route.pageType) {
    return <Component type={route.pageType} {...props} />
  }
  return <Component {...props} />
}
