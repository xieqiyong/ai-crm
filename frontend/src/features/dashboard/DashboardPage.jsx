import { useEffect, useState } from 'react'
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  BriefcaseBusiness,
  CalendarDays,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  RefreshCw,
  Target,
  Trophy,
  UserRoundCheck,
  Users,
} from 'lucide-react'
import { api } from '../../api'
import { Button, Card, PageHeader } from '../../components'

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
  todayFollowupRanking: [],
  todayTaskCompletionRanking: [],
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatAmount(value) {
  const amount = Number(value || 0)
  if (amount >= 100000000) return `¥${(amount / 100000000).toFixed(2)}亿`
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(2)}万`
  return amount.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })
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

function formatDateTime(value, emptyText = '-') {
  if (!value) return emptyText
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return emptyText
  return date.toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function healthClass(health) {
  if (health === 'CRITICAL') return 'critical'
  if (health === 'WARNING') return 'warning'
  return 'normal'
}

function FollowupAttentionPanel({ overview, activeTarget, setActiveTarget, navigate }) {
  const leadActive = activeTarget === 'LEAD'
  const records = leadActive ? (overview.attentionLeads || []) : (overview.attentionCustomers || [])
  const targetName = leadActive ? '线索' : '客户'
  const listRoute = leadActive ? 'leads' : 'customers'
  const detailRoute = leadActive ? 'leads/detail' : 'customers/detail'
  const leadAttentionCount = Number(overview.warningFollowupLeadCount || 0)
    + Number(overview.criticalFollowupLeadCount || 0)
  const customerAttentionCount = Number(overview.warningFollowupCustomerCount || 0)
    + Number(overview.criticalFollowupCustomerCount || 0)
  return (
    <Card className="dashboard-attention-card">
      <div className="card-heading dashboard-attention-heading">
        <div>
          <h2><AlertTriangle size={17} />急需跟进{targetName}</h2>
          <div className="dashboard-attention-tabs">
            <button
              className={leadActive ? 'active' : ''}
              onClick={() => setActiveTarget('LEAD')}
            >
              线索预警 <b>{formatNumber(leadAttentionCount)}</b>
            </button>
            <button
              className={!leadActive ? 'active' : ''}
              onClick={() => setActiveTarget('CUSTOMER')}
            >
              客户预警 <b>{formatNumber(customerAttentionCount)}</b>
            </button>
          </div>
        </div>
        <button onClick={() => navigate(listRoute)}>全部{targetName} <ArrowRight size={14} /></button>
      </div>

      {records.length ? (
        <div className="data-table-wrap dashboard-attention-table-wrap">
          <table className="data-table dashboard-attention-table">
            <thead>
              <tr>
                <th>跟进状态</th>
                <th>{targetName}</th>
                <th>意向产品</th>
                {overview.managementView && <th>负责人</th>}
                <th>最近跟进</th>
                <th>计划跟进</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {records.map((item) => (
                <tr key={`${item.targetType}-${item.targetId}`}>
                  <td>
                    <div className={`dashboard-health-status ${healthClass(item.followupHealth)}`}>
                      <span><i />{item.followupHealthName}</span>
                      <small>{item.followupReason}</small>
                    </div>
                  </td>
                  <td>
                    <button
                      className="dashboard-customer-link"
                      onClick={() => navigate(`${detailRoute}/${encodeURIComponent(item.targetId)}`)}
                    >
                      <b>{item.companyName || item.targetName || `未命名${targetName}`}</b>
                      <small>
                        {[item.contactName, item.contactPhone].filter(Boolean).join(' · ') || '暂无联系人信息'}
                      </small>
                    </button>
                  </td>
                  <td><span className="dashboard-product-name">{item.productName || '-'}</span></td>
                  {overview.managementView && <td>{item.ownerName || '-'}</td>}
                  <td>{formatDateTime(item.lastFollowupAt, '尚未跟进')}</td>
                  <td>{formatDateTime(item.nextFollowTime, '按提醒周期')}</td>
                  <td>
                    <button
                      className="dashboard-followup-action"
                      onClick={() => navigate(`${detailRoute}/${encodeURIComponent(item.targetId)}`)}
                    >
                      去跟进
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="dashboard-attention-empty">
          <span><CheckCircle2 size={22} /></span>
          <b>当前没有超期{targetName}</b>
          <p>需要提醒的{targetName}会根据下次计划和系统跟进周期自动出现在这里。</p>
        </div>
      )}
    </Card>
  )
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

function RankingPanel({ title, icon: Icon, items, valueKey, timeKey, emptyText }) {
  const records = items || []
  const maxCount = records.reduce((max, item) => Math.max(max, Number(item[valueKey] || 0)), 0)
  return (
    <Card className="dashboard-ranking-card dashboard-team-card">
      <div className="card-heading">
        <div><h2><Icon size={17} />{title}</h2></div>
      </div>
      <div className="dashboard-ranking-list">
        {records.map((item) => {
          const count = Number(item[valueKey] || 0)
          const width = maxCount > 0 ? count / maxCount * 100 : 0
          return (
            <div className="dashboard-ranking-row" key={item.userId || item.rankNo}>
              <span className={`dashboard-rank-no rank-${item.rankNo}`}>{item.rankNo}</span>
              <div className="dashboard-rank-main">
                <div className="dashboard-rank-title">
                  <b>{item.userName || '未命名用户'}</b>
                  <em>{formatDateTime(item[timeKey], emptyText)}</em>
                </div>
                <i><span style={{ width: `${width}%` }} /></i>
              </div>
              <strong>{formatNumber(count)}</strong>
            </div>
          )
        })}
        {!records.length && <div className="dashboard-status-empty">暂无可展示人员</div>}
      </div>
    </Card>
  )
}

export function DashboardPage({ navigate, currentRole, notify }) {
  const [overview, setOverview] = useState(emptyOverview)
  const [loading, setLoading] = useState(true)
  const [now, setNow] = useState(() => new Date())
  const [activeAttentionTarget, setActiveAttentionTarget] = useState('LEAD')

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
      label: overview.managementView ? '权限内线索' : '我的线索',
      value: overview.leadCount,
      icon: Target,
      tone: 'blue',
      detail: `正常跟进 ${formatNumber(overview.normalFollowupLeadCount)}`,
    },
    {
      label: overview.managementView ? '权限内客户' : '我的客户',
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
    },
    {
      label: '红灯急跟进',
      value: criticalCount,
      icon: AlertTriangle,
      tone: 'critical',
      detail: `线索 ${formatNumber(overview.criticalFollowupLeadCount)} · 客户 ${formatNumber(overview.criticalFollowupCustomerCount)}`,
    },
  ]

  const businessCards = [
    { label: '线索总数', value: formatNumber(overview.leadCount), icon: Target, tone: 'orange' },
    { label: '客户总数', value: formatNumber(overview.customerCount), icon: Users, tone: 'blue' },
    { label: '商机总数', value: formatNumber(overview.opportunityCount), icon: BriefcaseBusiness, tone: 'purple' },
    { label: '商机金额', value: formatAmount(overview.opportunityAmount), icon: CircleDollarSign, tone: 'green' },
  ]

  return (
    <div className="page dashboard-page dashboard-sales-page">
      <PageHeader
        eyebrow={overview.managementView ? '团队经营工作台' : '销售个人工作台'}
        title={`${getGreeting(now)}，${currentRole?.name || '用户'}`}
        description={`${formatToday(now)} · 先处理红黄灯线索和客户，再完成今天的销售任务`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={load}>
              {loading ? '刷新中' : '刷新数据'}
            </Button>
            <Button variant="secondary" icon={Target} onClick={() => navigate('leads')}>线索管理</Button>
            <Button icon={Users} onClick={() => navigate('customers')}>客户管理</Button>
          </>
        )}
      />

      <section className="dashboard-sales-summary">
        {salesCards.map(({ label, value, icon: Icon, tone, detail }) => (
          <Card className={`dashboard-customer-stat ${tone}`} key={label}>
            <span><Icon size={19} /></span>
            <div>
              <small>{label}</small>
              <strong>{formatNumber(value)}</strong>
              <em>{detail}</em>
            </div>
          </Card>
        ))}
      </section>

      <section className="dashboard-sales-workspace">
        <FollowupAttentionPanel
          overview={overview}
          activeTarget={activeAttentionTarget}
          setActiveTarget={setActiveAttentionTarget}
          navigate={navigate}
        />
        <TodayActionPanel overview={overview} navigate={navigate} />
      </section>

      {overview.managementView && (
        <section className="dashboard-management-area">
          <div className="dashboard-section-heading">
            <div>
              <h2>团队经营概览</h2>
              <p>仅管理视角展示团队经营数据和今日执行排名</p>
            </div>
          </div>
          <div className="dashboard-business-summary">
            {businessCards.map(({ label, value, icon: Icon, tone }) => (
              <Card className="dashboard-business-card" key={label}>
                <span className={tone}><Icon size={18} /></span>
                <div><small>{label}</small><b>{value}</b></div>
              </Card>
            ))}
          </div>
          <div className="dashboard-team-grid">
            <RankingPanel
              title="今日跟进排行榜"
              icon={Trophy}
              items={overview.todayFollowupRanking}
              valueKey="followupCount"
              timeKey="lastFollowupAt"
              emptyText="今日暂无跟进"
            />
            <RankingPanel
              title="今日任务完成榜"
              icon={CheckCircle2}
              items={overview.todayTaskCompletionRanking}
              valueKey="completedTaskCount"
              timeKey="lastCompletedAt"
              emptyText="今日暂无完成"
            />
          </div>
        </section>
      )}

      <div className="dashboard-data-note">
        <Activity size={15} />
        线索和客户预警均根据真实跟进记录、下次计划和系统提醒周期动态计算。
      </div>
    </div>
  )
}
