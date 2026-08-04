import { useEffect, useState } from 'react'
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  BriefcaseBusiness,
  CheckCircle2,
  ClipboardCheck,
  CircleDollarSign,
  Network,
  Plus,
  RefreshCw,
  Target,
  Trophy,
  Users,
} from 'lucide-react'
import { api } from '../../api'
import { Button, Card, PageHeader } from '../../components'

const emptyOverview = {
  leadCount: 0,
  customerCount: 0,
  opportunityCount: 0,
  channelCount: 0,
  opportunityAmount: 0,
  wonAmount: 0,
  todayFollowupCount: 0,
  todayChannelUserCount: 0,
  todayLeadConversionCount: 0,
  todayNewLeadCount: 0,
  todayPendingTaskCount: 0,
  overdueTaskCount: 0,
  todayCompletedTaskCount: 0,
  leadStatusCounts: [],
  customerStatusCounts: [],
  opportunityStageCounts: [],
  channelStatusCounts: [],
  todayFollowupRanking: [],
  todayTaskCompletionRanking: [],
}

const leadColors = {
  NEW: '#5b8ff9',
  CONTACTED: '#62b2fd',
  FOLLOWING: '#5ad8a6',
  QUALIFIED: '#f6bd16',
  NURTURING: '#6f5ef9',
  CONVERTED: '#42b983',
  INVALID: '#b8c2cc',
  DUPLICATE: '#f6903d',
  CLOSED: '#e8684a',
}

const customerColors = ['#5b8ff9', '#5ad8a6', '#f6bd16', '#42b983', '#8b95a5', '#e8684a', '#6f5ef9']
const channelColors = ['#5b8ff9', '#62b2fd', '#5ad8a6', '#f6bd16', '#6f5ef9', '#42b983']

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatAmount(value) {
  const amount = Number(value || 0)
  if (amount >= 100000000) {
    return `¥${(amount / 100000000).toFixed(2)}亿`
  }
  if (amount >= 10000) {
    return `¥${(amount / 10000).toFixed(2)}万`
  }
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

function formatFollowupTime(value) {
  if (!value) {
    return '今日暂无跟进'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '今日暂无跟进'
  }
  return `最近 ${new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)}`
}

function formatCompletionTime(value) {
  if (!value) {
    return '今日暂无完成'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '今日暂无完成'
  }
  return `最近 ${new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)}`
}

function getCount(items, code) {
  return Number((items || []).find((item) => item.code === code)?.count || 0)
}

function totalCount(items) {
  return (items || []).reduce((sum, item) => sum + Number(item.count || 0), 0)
}

function resolveStatusColor(colors, item, index, fallback) {
  if (!Array.isArray(colors) && colors?.[item?.code]) {
    return colors[item.code]
  }
  if (Array.isArray(colors) && colors.length) {
    return colors[index % colors.length]
  }
  return fallback
}

function StatusPanel({ title, description, icon: Icon, items, total, colors, onOpen }) {
  return (
    <Card className="dashboard-status-panel">
      <div className="card-heading">
        <div>
          <h2><Icon size={17} />{title}</h2>
          <p>{description}</p>
        </div>
        <button onClick={onOpen}>查看 <ArrowRight size={14} /></button>
      </div>
      <div className="dashboard-status-list">
        {(items || []).map((item, index) => {
          const count = Number(item.count || 0)
          const width = total > 0 ? count / total * 100 : 0
          const color = resolveStatusColor(colors, item, index, '#f45b0b')
          return (
            <div className="dashboard-status-row" key={item.code}>
              <div>
                <span><i style={{ background: color }} />{item.name}</span>
                <b>{formatNumber(count)}</b>
              </div>
              <em>
                <i style={{ width: `${width}%`, background: color }} />
              </em>
            </div>
          )
        })}
        {!(items || []).length && <div className="dashboard-status-empty">暂无状态统计</div>}
      </div>
    </Card>
  )
}

function FollowupRankingPanel({ items, onOpen }) {
  const records = items || []
  const maxCount = records.reduce((max, item) => Math.max(max, Number(item.followupCount || 0)), 0)
  return (
    <Card className="dashboard-status-panel dashboard-ranking-card">
      <div className="card-heading">
        <div>
          <h2><Trophy size={17} />今日跟进排行榜</h2>
        </div>
        <button onClick={onOpen}>查看 <ArrowRight size={14} /></button>
      </div>
      <div className="dashboard-ranking-list">
        {records.map((item) => {
          const count = Number(item.followupCount || 0)
          const width = maxCount > 0 ? count / maxCount * 100 : 0
          return (
            <div className="dashboard-ranking-row" key={item.userId || item.rankNo}>
              <span className={`dashboard-rank-no rank-${item.rankNo}`}>{item.rankNo}</span>
              <div className="dashboard-rank-main">
                <div className="dashboard-rank-title">
                  <b>{item.userName || '未命名用户'}</b>
                  <em>{formatFollowupTime(item.lastFollowupAt)}</em>
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

function TaskCompletionRankingPanel({ items, onOpen }) {
  const records = items || []
  const maxCount = records.reduce((max, item) => Math.max(max, Number(item.completedTaskCount || 0)), 0)
  return (
    <Card className="dashboard-status-panel dashboard-ranking-card">
      <div className="card-heading">
        <div>
          <h2><ClipboardCheck size={17} />今日任务完成榜</h2>
        </div>
        <button onClick={onOpen}>查看 <ArrowRight size={14} /></button>
      </div>
      <div className="dashboard-ranking-list">
        {records.map((item) => {
          const count = Number(item.completedTaskCount || 0)
          const width = maxCount > 0 ? count / maxCount * 100 : 0
          return (
            <div className="dashboard-ranking-row" key={item.userId || item.rankNo}>
              <span className={`dashboard-rank-no rank-${item.rankNo}`}>{item.rankNo}</span>
              <div className="dashboard-rank-main">
                <div className="dashboard-rank-title">
                  <b>{item.userName || '未命名用户'}</b>
                  <em>{formatCompletionTime(item.lastCompletedAt)}</em>
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

  const leadItems = overview.leadStatusCounts || []
  const customerItems = overview.customerStatusCounts || []
  const opportunityItems = overview.opportunityStageCounts || []
  const channelItems = overview.channelStatusCounts || []
  const followupRanking = overview.todayFollowupRanking || []
  const taskCompletionRanking = overview.todayTaskCompletionRanking || []
  const leadTotal = totalCount(leadItems)
  const customerTotal = totalCount(customerItems)
  const channelTotal = totalCount(channelItems)

  const statGroups = [
    {
      title: '业务总览',
      description: '看当前业务盘子有多大',
      cards: [
        {
          label: '线索总数',
          value: formatNumber(overview.leadCount),
          icon: Target,
          tone: 'orange',
          detail: `已转化 ${formatNumber(getCount(leadItems, 'CONVERTED'))} 条`,
        },
        {
          label: '客户总数',
          value: formatNumber(overview.customerCount),
          icon: Users,
          tone: 'blue',
          detail: `已合作 ${formatNumber(getCount(customerItems, 'COOPERATED'))} 家`,
        },
        {
          label: '商机总数',
          value: formatNumber(overview.opportunityCount),
          icon: BriefcaseBusiness,
          tone: 'purple',
          detail: `已成交 ${formatNumber(getCount(opportunityItems, 'WON'))} 个`,
        },
        {
          label: '商机金额',
          value: formatAmount(overview.opportunityAmount),
          icon: CircleDollarSign,
          tone: 'green',
          detail: `成交金额 ${formatAmount(overview.wonAmount)}`,
        },
      ],
    },
    {
      title: '今日动作',
      description: '看今天新增、跟进和转化',
      cards: [
        {
          label: '今日跟进数',
          value: formatNumber(overview.todayFollowupCount),
          icon: Activity,
          tone: 'blue',
          detail: '按今日实际跟进时间统计',
        },
        {
          label: '今日渠道新增用户数',
          value: formatNumber(overview.todayChannelUserCount),
          icon: Network,
          tone: 'purple',
          detail: '按今日渠道入库时间统计',
        },
        {
          label: '今日线索转化数',
          value: formatNumber(overview.todayLeadConversionCount),
          icon: CheckCircle2,
          tone: 'green',
          detail: '按今日线索转化时间统计',
        },
        {
          label: '今日新增线索数',
          value: formatNumber(overview.todayNewLeadCount),
          icon: Plus,
          tone: 'orange',
          detail: '按今日线索创建时间统计',
        },
      ],
    },
    {
      title: '任务预警',
      description: '看待办压力和执行结果',
      cards: [
        {
          label: '今日待办任务',
          value: formatNumber(overview.todayPendingTaskCount),
          icon: ClipboardCheck,
          tone: 'purple',
          detail: '按今日任务截止时间统计',
        },
        {
          label: '逾期任务',
          value: formatNumber(overview.overdueTaskCount),
          icon: AlertTriangle,
          tone: 'orange',
          detail: '未完成且已超过截止时间',
        },
        {
          label: '今日完成任务',
          value: formatNumber(overview.todayCompletedTaskCount),
          icon: CheckCircle2,
          tone: 'green',
          detail: '按今日任务完成时间统计',
        },
      ],
    },
  ]

  return (
    <div className="page dashboard-page">
      <PageHeader
        eyebrow="销售驾驶舱"
        title={`${getGreeting(now)}，${currentRole?.name || '用户'}`}
        description={`${formatToday(now)} · 以下统计基于当前账号可访问的真实业务数据`}
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={load}>
              {loading ? '刷新中' : '刷新数据'}
            </Button>
            <Button icon={Plus} onClick={() => navigate('leads')}>新建线索</Button>
          </>
        )}
      />

      <section className="dashboard-layer">
        <div className="dashboard-layer-heading">
          <span>01</span>
          <div>
            <h2>核心数据统计</h2>
            <p>快速查看当前业务盘面的关键数据</p>
          </div>
        </div>
        <div className="dashboard-stat-groups">
          {statGroups.map((group) => (
            <div className="dashboard-stat-group" key={group.title}>
              <div className="dashboard-stat-group-head">
                <b>{group.title}</b>
                <span>{group.description}</span>
              </div>
              <div className="stats-grid">
                {group.cards.map(({ label, value, icon: Icon, tone, detail }) => (
                  <Card className="stat-card" key={label}>
                    <div className={`stat-icon ${tone}`}><Icon size={20} /></div>
                    <div className="stat-copy">
                      <span>{label}</span>
                      <strong>{value}</strong>
                      <small><CheckCircle2 size={13} /><em>{detail}</em></small>
                    </div>
                  </Card>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="dashboard-layer">
        <div className="dashboard-layer-heading">
          <span>02</span>
          <div>
            <h2>各状态统计</h2>
            <p>展开查看线索、客户与渠道的完整状态明细</p>
          </div>
        </div>
        <div className="dashboard-status-columns">
          <StatusPanel
            title="线索进展"
            description={`共 ${formatNumber(overview.leadCount)} 条线索`}
            icon={Target}
            items={leadItems}
            total={leadTotal}
            colors={leadColors}
            onOpen={() => navigate('leads')}
          />
          <StatusPanel
            title="客户状态"
            description={`共 ${formatNumber(overview.customerCount)} 家客户`}
            icon={Users}
            items={customerItems}
            total={customerTotal}
            colors={customerColors}
            onOpen={() => navigate('customers')}
          />
          <StatusPanel
            title="渠道处理"
            description={`共 ${formatNumber(overview.channelCount)} 条渠道记录`}
            icon={Network}
            items={channelItems}
            total={channelTotal}
            colors={channelColors}
            onOpen={() => navigate('channels')}
          />
          <FollowupRankingPanel
            items={followupRanking}
            onOpen={() => navigate('followups')}
          />
          <TaskCompletionRankingPanel
            items={taskCompletionRanking}
            onOpen={() => navigate('tasks')}
          />
        </div>
      </section>

      <div className="dashboard-data-note">
        <Activity size={15} />
        页面不生成预测数据；新增或更新业务记录后，点击“刷新数据”即可同步当前统计。
      </div>
    </div>
  )
}
