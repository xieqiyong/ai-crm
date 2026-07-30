import {
  BarChart3, BookOpen, Bot, BriefcaseBusiness, ClipboardCheck, FileClock,
  LayoutDashboard, Mail, MessageSquareText, Network, Package, Settings, UserRoundCog, Users,
} from 'lucide-react'
import { AgentConfigPage } from '../features/agent/AgentConfigPage'
import { MarketingAssistantPage } from '../features/assistant/MarketingAssistantPage'
import { ModelConfigPage } from '../features/admin/ModelConfigPage'
import { OrganizationAdminPage } from '../features/admin/OrganizationAdminPage'
import { AuditLogPage } from '../features/admin/AuditLogPage'
import { ChannelPage } from '../features/channel/ChannelPage'
import { CustomerDetailPage } from '../features/customer/CustomerDetailPage'
import { CustomerPage } from '../features/customer/CustomerPage'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { FollowupPage } from '../features/followup/FollowupPage'
import { KnowledgePage } from '../features/knowledge/KnowledgePage'
import { LeadDetailPage } from '../features/lead/LeadDetailPage'
import { LeadPage } from '../features/lead/LeadPage'
import { SimpleModulePage } from '../features/module/SimpleModulePage'
import { OpportunityPage } from '../features/opportunity/OpportunityPage'
import { ProductPage } from '../features/product/ProductPage'
import { SettingsPage } from '../features/settings/SettingsPage'
import { MailPage } from '../features/mail/MailPage'

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
        component: LeadPage,
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
        component: OpportunityPage,
      },
      {
        key: 'followups',
        label: '跟进记录',
        icon: MessageSquareText,
        permission: 'menu.followups',
        component: FollowupPage,
      },
      {
        key: 'tasks',
        label: '销售任务',
        icon: ClipboardCheck,
        permission: 'menu.tasks',
        component: SimpleModulePage,
        pageType: 'tasks',
      },
      {
        key: 'channels',
        label: '渠道管理',
        icon: Network,
        permission: 'menu.channels',
        component: ChannelPage,
      },
    ],
  },
  {
    label: 'AI 与知识',
    items: [
      {
        key: 'assistant',
        label: 'AI 智能体助手',
        icon: Bot,
        permission: 'menu.assistant',
        component: MarketingAssistantPage,
      },
      {
        key: 'agent-config',
        label: '智能体配置',
        icon: Bot,
        permission: 'menu.agent_config',
        component: AgentConfigPage,
      },
      {
        key: 'knowledge',
        label: '知识库',
        icon: BookOpen,
        permission: 'menu.knowledge',
        component: KnowledgePage,
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
    label: '工具',
    items: [
      {
        key: 'mail',
        label: '客户邮件',
        icon: Mail,
        permission: 'menu.mail',
        component: MailPage,
      },
    ],
  },
  {
    label: '管理',
    items: [
      {
        key: 'products',
        label: '产品管理',
        icon: Package,
        permission: 'menu.products',
        component: ProductPage,
      },
      {
        key: 'organization',
        label: '组织与权限',
        icon: UserRoundCog,
        permission: 'menu.organization',
        component: OrganizationAdminPage,
      },
      {
        key: 'audit-logs',
        label: '审计日志',
        icon: FileClock,
        permission: 'menu.audit_logs',
        component: AuditLogPage,
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

export const hiddenRoutes = [
  {
    key: 'leads/detail',
    label: '线索详情',
    permission: 'menu.leads',
    component: LeadDetailPage,
    navKey: 'leads',
  },
  {
    key: 'customers/detail',
    label: '客户详情',
    permission: 'menu.customers',
    component: CustomerDetailPage,
    navKey: 'customers',
  },
]

export const routes = [...routeGroups.flatMap((group) => group.items), ...hiddenRoutes]

export const routeMap = routes.reduce((map, route) => {
  map[route.key] = route
  return map
}, {})

export function normalizeRouteKey(routeKey) {
  const key = String(routeKey || DEFAULT_ROUTE).split('?')[0]
  if (key.startsWith('leads/detail/')) {
    return 'leads/detail'
  }
  if (key.startsWith('customers/detail/')) {
    return 'customers/detail'
  }
  return key
}

export function resolveRouteParams(routeKey) {
  const key = String(routeKey || '')
  if (key.startsWith('leads/detail/')) {
    return { id: decodeURIComponent(key.slice('leads/detail/'.length).split('?')[0]) }
  }
  if (key.startsWith('customers/detail/')) {
    return { id: decodeURIComponent(key.slice('customers/detail/'.length).split('?')[0]) }
  }
  return {}
}

export function resolveRoute(routeKey) {
  return routeMap[normalizeRouteKey(routeKey)] || routeMap[DEFAULT_ROUTE]
}

export function canAccessRoute(routeKey, can) {
  const route = resolveRoute(routeKey)
  return can(route.permission)
}

export function renderRoute(routeKey, props) {
  const route = resolveRoute(routeKey)
  const Component = route.component
  const routeParams = resolveRouteParams(routeKey)
  if (route.pageType) {
    return <Component type={route.pageType} routeKey={routeKey} routeParams={routeParams} {...props} />
  }
  return <Component routeKey={routeKey} routeParams={routeParams} {...props} />
}
