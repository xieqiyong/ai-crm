import { useEffect, useState } from 'react'
import {
  Activity,
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  Clock3,
  RefreshCw,
  Target,
  UserRoundCheck,
  Users,
} from 'lucide-react'
import { api } from '../../api'
import { Button, Card, PageHeader } from '../../components'
import { MarketingFunnel } from './MarketingFunnel'

const emptyOverview = {
  leadCount: 0,
  customerCount: 0,
  opportunityCount: 0,
  opportunityAmount: 0,
  wonAmount: 0,
  todayFollowupCount: 0,
  todayNewLeadCount: 0,
  todayLeadConversionCount: 0,
  todayPendingTaskCount: 0,
  overdueTaskCount: 0,
  todayCompletedTaskCount: 0,
  todayNewCustomerCount: 0,
  monthNewCustomerCount: 0,
  monthNewLeadCount: 0,
  normalFollowupCustomerCount: 0,
  warningFollowupCustomerCount: 0,
  criticalFollowupCustomerCount: 0,
  normalFollowupLeadCount: 0,
  warningFollowupLeadCount: 0,
  criticalFollowupLeadCount: 0,
  managementView: false,
  attentionCustomers: [],
  attentionLeads: [],
  marketingFunnel: [],
  todayFollowupRanking: [],
  todayTaskCompletionRanking: [],
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function getGreeting(date) {
  const hour = date.getHours()
  if (hour < 5) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 13) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

function formatToday(date) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(date)
}

function TodayActionPanel({ overview, navigate }) {
  const actions = [
    { label: '今日跟进', value: overview.todayFollowupCount, icon: Activity, tone: 'blue' },
    { label: '今日待办', value: overview.todayPendingTaskCount, icon: Clock3, tone: 'purple' },
    { label: '逾期任务', value: overview.overdueTaskCount, icon: AlertTriangle, tone: 'orange' },
    { label: '今日完成', value: overview.todayCompletedTaskCount, icon: CheckCircle2, tone: 'green' },
  ]
  return (
    <Card className="dashboard-action-card">
      <div className="card-heading">
        <div>
          <h2><CalendarDays size={17} />今日执行</h2>
          <p>聚焦今天需要完成的销售动作</p>
        </div>
      </div>
      <div className="dashboard-action-grid">
        {actions.map(({ label, value, icon: Icon, tone }) => (
          <div key={label} className={`dashboard-action-item ${tone}`}>
            <span><Icon size={16} /></span>
            <div><small>{label}</small><b>{formatNumber(value)}</b></div>
          </div>
        ))}
      </div>
      <Button variant="secondary" onClick={() => navigate('tasks')}>查看销售任务</Button>
    </Card>
  )
}

export function DashboardPage({ navigate, currentRole, notify }) {
  const [overview, setOverview] = useState(emptyOverview)
  const [loading, setLoading] = useState(true)
  const [now, setNow] = useState(() => new Date())

  const load = async () => {
    setLoading(true)
    try {
      setOverview(await api.dashboard.overview() || emptyOverview)
    } catch (err) {
      notify(err.message || '工作台数据加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60000)
    return () => window.clearInterval(timer)
  }, [])

  const warningCount = Number(overview.warningFollowupLeadCount || 0)
    + Number(overview.warningFollowupCustomerCount || 0)
  const criticalCount = Number(overview.criticalFollowupLeadCount || 0)
    + Number(overview.criticalFollowupCustomerCount || 0)
  const salesCards = [
    {
      label: '线索总数',
      value: overview.leadCount,
      icon: Target,
      tone: 'blue',
      detail: `正常跟进 ${formatNumber(overview.normalFollowupLeadCount)}`,
    },
    {
      label: '客户总数',
      value: overview.customerCount,
      icon: Users,
      tone: 'purple',
      detail: `正常跟进 ${formatNumber(overview.normalFollowupCustomerCount)}`,
    },
    {
      label: '今日新增线索',
      value: overview.todayNewLeadCount,
      icon: UserRoundCheck,
      tone: 'blue',
      detail: `本月新增 ${formatNumber(overview.monthNewLeadCount)}`,
    },
    {
      label: '今日新增客户',
      value: overview.todayNewCustomerCount,
      icon: CalendarDays,
      tone: 'purple',
      detail: `本月新增 ${formatNumber(overview.monthNewCustomerCount)}`,
    },
    {
      label: '黄灯待跟进',
      value: warningCount,
      icon: Clock3,
      tone: 'warning',
      detail: `线索 ${formatNumber(overview.warningFollowupLeadCount)} · 客户 ${formatNumber(overview.warningFollowupCustomerCount)}`,
      route: 'followup-attention?health=WARNING',
    },
    {
      label: '红灯急跟进',
      value: criticalCount,
      icon: AlertTriangle,
      tone: 'critical',
      detail: `线索 ${formatNumber(overview.criticalFollowupLeadCount)} · 客户 ${formatNumber(overview.criticalFollowupCustomerCount)}`,
      route: 'followup-attention?health=CRITICAL',
    },
  ]

  return (
    <div className="page dashboard-page dashboard-sales-page">
      <PageHeader
        eyebrow={overview.managementView ? '团队经营工作台' : '销售个人工作台'}
        title={`${getGreeting(now)}，${currentRole?.name || '用户'}`}
        description={`${formatToday(now)} · 先处理红黄灯线索和客户，再完成今天的销售任务`}
        actions={(
          <Button variant="secondary" icon={RefreshCw} onClick={load}>
            {loading ? '刷新中' : '刷新数据'}
          </Button>
        )}
      />

      <section className="dashboard-sales-summary">
        {salesCards.map(({ label, value, icon: Icon, tone, detail, route }) => (
          <Card
            className={`dashboard-customer-stat ${tone} ${route ? 'clickable' : ''}`}
            key={label}
            role={route ? 'button' : undefined}
            tabIndex={route ? 0 : undefined}
            aria-label={route ? `查看${label}列表，共${formatNumber(value)}条` : undefined}
            onClick={route ? () => navigate(route) : undefined}
            onKeyDown={route ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                navigate(route)
              }
            } : undefined}
          >
            <span><Icon size={19} /></span>
            <div>
              <small>{label}</small>
              <strong>{formatNumber(value)}</strong>
              <em>{detail}{route ? ' · 点击查看' : ''}</em>
            </div>
          </Card>
        ))}
      </section>

      <section className="dashboard-sales-workspace">
        <MarketingFunnel
          items={overview.marketingFunnel}
          loading={loading}
          navigate={navigate}
        />
        <TodayActionPanel overview={overview} navigate={navigate} />
      </section>

      <div className="dashboard-data-note">
        <Activity size={15} />
        营销漏斗按照实时业务数据统计，点击阶段后的列表数量与漏斗保持一致。
      </div>
    </div>
  )
}
