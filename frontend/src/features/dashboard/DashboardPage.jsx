import { useEffect, useMemo, useState } from 'react'
import {
  ArrowRight,
  Bot,
  BriefcaseBusiness,
  CheckCircle2,
  CircleDollarSign,
  Network,
  Plus,
  RefreshCw,
  Sparkles,
  Target,
  TrendingUp,
  Users,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, PageHeader } from '../../components'

const emptyOverview = {
  leadCount: 0,
  customerCount: 0,
  opportunityCount: 0,
  channelCount: 0,
  opportunityAmount: 0,
  wonAmount: 0,
  leadStatusCounts: [],
  customerStatusCounts: [],
  opportunityStageCounts: [],
  channelStatusCounts: [],
}

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

function maxCount(items) {
  return Math.max(1, ...items.map((item) => Number(item.count || 0)))
}

export function DashboardPage({ navigate, currentRole, notify }) {
  const [overview, setOverview] = useState(emptyOverview)
  const [loading, setLoading] = useState(true)

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

  const stageMax = useMemo(() => maxCount(overview.opportunityStageCounts || []), [overview.opportunityStageCounts])
  const leadMax = useMemo(() => maxCount(overview.leadStatusCounts || []), [overview.leadStatusCounts])
  const customerMax = useMemo(() => maxCount(overview.customerStatusCounts || []), [overview.customerStatusCounts])
  const channelMax = useMemo(() => maxCount(overview.channelStatusCounts || []), [overview.channelStatusCounts])

  const statCards = [
    { label: '线索总数', value: formatNumber(overview.leadCount), icon: Target, tone: 'orange', detail: '来自真实线索表' },
    { label: '客户总数', value: formatNumber(overview.customerCount), icon: Users, tone: 'blue', detail: '来自真实客户表' },
    { label: '商机总数', value: formatNumber(overview.opportunityCount), icon: BriefcaseBusiness, tone: 'purple', detail: '来自真实商机表' },
    { label: '商机金额', value: formatAmount(overview.opportunityAmount), icon: CircleDollarSign, tone: 'green', detail: `已成交 ${formatAmount(overview.wonAmount)}` },
  ]

  return (
    <div className="page dashboard-page">
      <PageHeader
        eyebrow="销售驾驶舱"
        title={`早安，${currentRole.name}`}
        description="工作台已接入后台真实业务统计，空库时不会展示模拟数据。"
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={load}>{loading ? '刷新中' : '刷新数据'}</Button>
            <Button icon={Plus} onClick={() => navigate('leads')}>新建线索</Button>
          </>
        )}
      />

      <div className="stats-grid">
        {statCards.map(({ label, value, icon: Icon, tone, detail }) => (
          <Card className="stat-card" key={label}>
            <div className={`stat-icon ${tone}`}><Icon size={20} /></div>
            <div className="stat-copy">
              <span>{label}</span>
              <strong>{value}</strong>
              <small><TrendingUp size={13} /><em>{detail}</em></small>
            </div>
          </Card>
        ))}
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-main">
          <div className="analytics-row">
            <Card className="funnel-card">
              <div className="card-heading">
                <div>
                  <h2>商机阶段分布</h2>
                  <p>按真实商机阶段聚合数量和金额</p>
                </div>
                <button onClick={() => navigate('opportunities')}>查看商机 <ArrowRight size={15} /></button>
              </div>
              <div className="funnel">
                {(overview.opportunityStageCounts || []).map((item) => (
                  <div
                    style={{ '--width': `${Math.max(8, Number(item.count || 0) / stageMax * 100)}%`, '--alpha': '.78' }}
                    key={item.code}
                  >
                    <span>{item.name}</span>
                    <b>{formatNumber(item.count)}</b>
                    <small>{formatAmount(item.amount)}</small>
                  </div>
                ))}
              </div>
              {!(overview.opportunityStageCounts || []).length && (
                <div className="empty-table dashboard-empty"><BriefcaseBusiness size={24} /><b>暂无商机统计</b></div>
              )}
            </Card>

            <Card className="trend-card">
              <div className="card-heading">
                <div>
                  <h2>线索状态分布</h2>
                  <p>按真实线索状态聚合</p>
                </div>
                <Badge tone="info">共 {formatNumber(overview.leadCount)} 条</Badge>
              </div>
              <div className="dashboard-count-scroll">
                <div className="bar-chart dashboard-count-chart">
                  {(overview.leadStatusCounts || []).map((item) => (
                    <div className="bar-group" key={item.code}>
                      <i className="actual" style={{ height: `${Math.max(5, Number(item.count || 0) / leadMax * 100)}%` }} />
                      <span>{item.name}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div className="trend-summary">
                <div><span>客户</span><b>{formatNumber(overview.customerCount)}</b></div>
                <div><span>渠道</span><b>{formatNumber(overview.channelCount)}</b></div>
                <Badge tone="success">真实统计</Badge>
              </div>
              <span className="section-caption">客户状态</span>
              <div className="dashboard-status-grid">
                {(overview.customerStatusCounts || []).map((item) => (
                  <div className="dashboard-status-item" key={item.code}>
                    <span>{item.name}</span>
                    <b>{formatNumber(item.count)}</b>
                    <i><em style={{ width: `${Math.max(4, Number(item.count || 0) / customerMax * 100)}%` }} /></i>
                  </div>
                ))}
              </div>
            </Card>
          </div>

          <Card className="task-card">
            <div className="card-heading">
              <div>
                <h2>渠道状态</h2>
                <p>渠道池与音视频导入处理状态</p>
              </div>
              <Button variant="ghost" icon={Network} onClick={() => navigate('channels')}>进入渠道</Button>
            </div>
            <div className="dashboard-status-grid">
              {(overview.channelStatusCounts || []).map((item) => (
                <div className="dashboard-status-item" key={item.code}>
                  <span>{item.name}</span>
                  <b>{formatNumber(item.count)}</b>
                  <i><em style={{ width: `${Math.max(4, Number(item.count || 0) / channelMax * 100)}%` }} /></i>
                </div>
              ))}
            </div>
          </Card>
        </div>

        <aside className="dashboard-aside">
          <Card ai className="ai-summary-card">
            <div className="ai-card-title">
              <span><Bot size={18} /></span>
              <div>
                <h2>AI 能力接入位</h2>
                <small>等待模型配置和业务数据沉淀</small>
              </div>
            </div>
            <div className="ai-mini-insight">
              <Sparkles size={17} />
              <div>
                <b>当前不生成模拟洞察</b>
                <p>后续接入大模型后，可基于客户、线索、渠道和商机真实数据生成建议。</p>
              </div>
            </div>
            <Button onClick={() => navigate('model-configs')}>配置大模型</Button>
          </Card>
          <Card className="goal-card">
            <div className="card-heading">
              <div>
                <h2>成交金额</h2>
                <p>来自已成交商机</p>
              </div>
              <CheckCircle2 size={20} />
            </div>
            <div className="goal-ring"><span><b>{formatAmount(overview.wonAmount)}</b><small>已成交</small></span></div>
            <div className="goal-values">
              <span>商机总金额 <b>{formatAmount(overview.opportunityAmount)}</b></span>
              <span>商机数量 <b>{formatNumber(overview.opportunityCount)}</b></span>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  )
}
