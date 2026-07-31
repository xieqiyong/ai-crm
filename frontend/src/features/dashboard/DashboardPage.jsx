import { useEffect, useMemo, useState } from 'react'
import {
  Activity,
  ArrowRight,
  BriefcaseBusiness,
  CheckCircle2,
  CircleDollarSign,
  Network,
  Plus,
  RefreshCw,
  Target,
  Users,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, EChart, PageHeader } from '../../components'

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
  leadStatusCounts: [],
  customerStatusCounts: [],
  opportunityStageCounts: [],
  channelStatusCounts: [],
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

const opportunityColors = {
  DISCOVERY: '#5b8ff9',
  QUALIFICATION: '#5ad8a6',
  PROPOSAL: '#f6bd16',
  NEGOTIATION: '#f6903d',
  WON: '#42b983',
  LOST: '#e8684a',
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

function getCount(items, code) {
  return Number((items || []).find((item) => item.code === code)?.count || 0)
}

function totalCount(items) {
  return (items || []).reduce((sum, item) => sum + Number(item.count || 0), 0)
}

function hasData(items) {
  return (items || []).some((item) => Number(item.count || 0) > 0)
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

function leadChartOption(items) {
  return (theme) => ({
    baseOption: {
      animationDuration: 500,
      color: items.map((item) => leadColors[item.code] || theme.brand),
      tooltip: {
        trigger: 'item',
        backgroundColor: theme.surface,
        borderColor: theme.line,
        textStyle: { color: theme.text },
        formatter: '{b}<br/>线索数量：{c} 条<br/>占比：{d}%',
      },
      legend: {
        type: 'scroll',
        orient: 'vertical',
        top: 'middle',
        right: 8,
        width: '42%',
        itemWidth: 9,
        itemHeight: 9,
        itemGap: 12,
        textStyle: {
          color: theme.muted,
          fontSize: 12,
        },
        formatter: (name) => {
          const count = items.find((item) => item.name === name)?.count || 0
          return `${name}  ${formatNumber(count)}`
        },
      },
      series: [{
        name: '线索状态',
        type: 'pie',
        center: ['31%', '48%'],
        radius: ['51%', '73%'],
        avoidLabelOverlap: true,
        padAngle: 2,
        itemStyle: {
          borderColor: theme.surface,
          borderRadius: 6,
          borderWidth: 2,
        },
        label: { show: false },
        emphasis: {
          scaleSize: 5,
          label: { show: false },
        },
        data: items.map((item) => ({
          name: item.name,
          value: Number(item.count || 0),
        })),
      }],
    },
    media: [{
      query: { maxWidth: 460 },
      option: {
        legend: {
          orient: 'horizontal',
          top: '78%',
          right: 12,
          left: 12,
          width: 'auto',
          itemGap: 10,
        },
        series: [{
          center: ['50%', '38%'],
          radius: ['40%', '61%'],
        }],
      },
    }],
  })
}

function opportunityChartOption(items) {
  return (theme) => ({
    animationDuration: 500,
    grid: {
      top: 12,
      right: 72,
      bottom: 18,
      left: 82,
      containLabel: false,
    },
    tooltip: {
      trigger: 'item',
      backgroundColor: theme.surface,
      borderColor: theme.line,
      textStyle: { color: theme.text },
      formatter: (params) => {
        const item = items[params.dataIndex] || {}
        return `${item.name}<br/>商机数量：${formatNumber(item.count)} 个<br/>商机金额：${formatAmount(item.amount)}`
      },
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {
        color: theme.muted,
        fontSize: 11,
        formatter: (value) => formatNumber(value),
      },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: {
        lineStyle: {
          color: theme.line,
          type: 'dashed',
        },
      },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: items.map((item) => item.name),
      axisLabel: {
        color: theme.text,
        fontSize: 12,
      },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [{
      name: '商机数量',
      type: 'bar',
      barWidth: 16,
      showBackground: true,
      backgroundStyle: {
        color: theme.line,
        borderRadius: 8,
        opacity: 0.45,
      },
      label: {
        show: true,
        position: 'right',
        color: theme.text,
        fontSize: 12,
        fontWeight: 700,
        formatter: ({ value }) => `${formatNumber(value)} 个`,
      },
      itemStyle: {
        borderRadius: [0, 8, 8, 0],
        color: (params) => opportunityColors[items[params.dataIndex]?.code] || theme.brand,
      },
      data: items.map((item) => Number(item.count || 0)),
    }],
  })
}

function EmptyChart({ icon: Icon, title }) {
  return (
    <div className="dashboard-chart-empty">
      <span><Icon size={22} /></span>
      <b>{title}</b>
      <small>新增业务数据后，这里会自动生成图表</small>
    </div>
  )
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
  const leadTotal = totalCount(leadItems)
  const customerTotal = totalCount(customerItems)
  const channelTotal = totalCount(channelItems)
  const leadOption = useMemo(() => leadChartOption(leadItems), [leadItems])
  const opportunityOption = useMemo(() => opportunityChartOption(opportunityItems), [opportunityItems])

  const statCards = [
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
        <div className="stats-grid">
          {statCards.map(({ label, value, icon: Icon, tone, detail }) => (
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
      </section>

      <section className="dashboard-layer">
        <div className="dashboard-layer-heading">
          <span>02</span>
          <div>
            <h2>业务分布图</h2>
            <p>从线索结构和商机阶段观察销售推进情况</p>
          </div>
        </div>
        <div className="dashboard-chart-grid">
          <Card className="dashboard-chart-card">
            <div className="card-heading">
              <div>
                <h2>线索状态分布</h2>
                <p>按当前线索状态聚合数量与占比</p>
              </div>
              <Badge tone="info">共 {formatNumber(overview.leadCount)} 条</Badge>
            </div>
            {hasData(leadItems) ? (
              <div className="dashboard-donut-wrap">
                <EChart
                  option={leadOption}
                  className="dashboard-echart"
                  ariaLabel="线索状态分布环形图"
                />
                <div className="dashboard-donut-total">
                  <strong>{formatNumber(overview.leadCount)}</strong>
                  <small>线索总数</small>
                </div>
              </div>
            ) : (
              <EmptyChart icon={Target} title="暂无线索统计" />
            )}
          </Card>

          <Card className="dashboard-chart-card">
            <div className="card-heading">
              <div>
                <h2>商机阶段分布</h2>
                <p>按推进阶段查看商机数量，悬停可查看金额</p>
              </div>
              <button onClick={() => navigate('opportunities')}>查看商机 <ArrowRight size={15} /></button>
            </div>
            {hasData(opportunityItems) ? (
              <EChart
                option={opportunityOption}
                className="dashboard-echart"
                ariaLabel="商机阶段分布横向柱状图"
              />
            ) : (
              <EmptyChart icon={BriefcaseBusiness} title="暂无商机统计" />
            )}
          </Card>

        </div>
      </section>

      <section className="dashboard-layer">
        <div className="dashboard-layer-heading">
          <span>03</span>
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
        </div>
      </section>

      <div className="dashboard-data-note">
        <Activity size={15} />
        页面不生成预测数据；新增或更新业务记录后，点击“刷新数据”即可同步当前统计。
      </div>
    </div>
  )
}
