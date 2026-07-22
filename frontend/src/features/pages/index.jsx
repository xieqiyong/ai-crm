import { useMemo, useRef, useState } from 'react'
import {
  Activity, AlertTriangle, ArrowRight, ArrowUpRight, BarChart3, Bell, Bot,
  BriefcaseBusiness, Building2, CalendarDays, Check, CheckCircle2, ChevronDown,
  CircleDollarSign, Clock3, CloudUpload, Download, Eye, FileText, Filter, Flame,
  Gauge, Globe2, GripVertical, KeyRound, LayoutGrid, List, Mail, MapPin,
  MessageCircleMore, MoreHorizontal, Network, Palette, Pencil, Phone, Plus,
  RefreshCw, Search, Send, Settings2, ShieldCheck, SlidersHorizontal, Sparkles,
  Target, TrendingUp, Upload, UserPlus, Users, Waypoints, X, Zap,
} from 'lucide-react'
import { APP_NAME } from '../../config/appConfig'
import { Badge, Button, Card, Field, Modal, PageHeader } from '../../components'

const fmt = new Intl.NumberFormat('zh-CN')

const statCards = [
  { label: '今日新线索', value: '24', change: '+12.5%', icon: Target, tone: 'orange', detail: '较昨日增加 3 条' },
  { label: '跟进中客户', value: '158', change: '+4.2%', icon: Users, tone: 'blue', detail: '本周新增 18 位' },
  { label: '推进中商机', value: '42', change: '-3.0%', icon: BriefcaseBusiness, tone: 'purple', detail: '7 个需优先处理' },
  { label: '本月预测营收', value: '¥3.2M', change: '+18.0%', icon: CircleDollarSign, tone: 'green', detail: '目标完成度 76%' },
]

const tasks = [
  { title: '与领航物流确认合同细节', meta: '今天 14:00 · 高优先级', tag: '跟进', tone: 'danger' },
  { title: '发送产品方案至极光教育', meta: '今天 16:30 · 方案文档', tag: '文档', tone: 'info' },
  { title: '复盘昨日线索质量', meta: '已完成 · 10:15', tag: '内部', tone: 'neutral', done: true },
]

export function DashboardPage({ navigate, currentRole, notify }) {
  const [period, setPeriod] = useState('本月')
  const [taskState, setTaskState] = useState(tasks)

  return (
    <div className="page dashboard-page">
      <PageHeader
        eyebrow="销售驾驶舱"
        title={`早安，${currentRole.name}`}
        description="这是今天的业务概览。AI 已为您识别 3 个值得优先关注的商机。"
        actions={<><Button variant="secondary" icon={RefreshCw} onClick={() => notify('数据已更新')}>刷新数据</Button><Button icon={Plus} onClick={() => navigate('leads')}>新建线索</Button></>}
      />

      <div className="stats-grid">
        {statCards.map(({ label, value, change, icon: Icon, tone, detail }) => (
          <Card className="stat-card" key={label}>
            <div className={`stat-icon ${tone}`}><Icon size={20} /></div>
            <div className="stat-copy"><span>{label}</span><strong>{value}</strong><small className={change.startsWith('-') ? 'down' : ''}><TrendingUp size={13} />{change}<em>{detail}</em></small></div>
          </Card>
        ))}
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-main">
          <div className="analytics-row">
            <Card className="funnel-card">
              <div className="card-heading"><div><h2>销售漏斗转化</h2><p>本月各阶段线索转化情况</p></div><button onClick={() => navigate('opportunities')}>查看商机 <ArrowRight size={15} /></button></div>
              <div className="funnel">
                <div style={{ '--width': '100%', '--alpha': '.98' }}><span>线索获取</span><b>1,200</b><small>100%</small></div>
                <div style={{ '--width': '78%', '--alpha': '.72' }}><span>有效沟通</span><b>780</b><small>65%</small></div>
                <div style={{ '--width': '53%', '--alpha': '.48' }}><span>方案报价</span><b>360</b><small>46%</small></div>
                <div style={{ '--width': '34%', '--alpha': '.28' }}><span>最终成交</span><b>144</b><small>40%</small></div>
              </div>
              <div className="funnel-note"><Sparkles size={15} /><span><b>AI 发现：</b>方案报价到成交的转化率较上月提升 6.8%</span></div>
            </Card>

            <Card className="trend-card">
              <div className="card-heading"><div><h2>业绩趋势</h2><p>预测营收与实际营收</p></div><select value={period} onChange={(e) => setPeriod(e.target.value)}><option>本月</option><option>本季度</option><option>本年度</option></select></div>
              <div className="chart-legend"><span><i className="actual" />实际营收</span><span><i className="predict" />AI 预测</span></div>
              <div className="bar-chart">
                {[42, 56, 48, 70, 66, 84, 78, 94].map((height, index) => (
                  <div className="bar-group" key={index}>
                    <i className="predict" style={{ height: `${Math.min(height + 8, 100)}%` }} /><i className="actual" style={{ height: `${height}%` }} />
                    <span>{index + 1}周</span>
                  </div>
                ))}
              </div>
              <div className="trend-summary"><div><span>本月累计</span><b>¥2,428,000</b></div><div><span>预计达成</span><b>¥3,200,000</b></div><Badge tone="success">目标达成率 106%</Badge></div>
            </Card>
          </div>

          <Card className="task-card">
            <div className="card-heading"><div><h2>今日待办事项</h2><p>还有 2 项任务需要处理</p></div><Button variant="ghost" icon={SlidersHorizontal}>筛选</Button></div>
            <div className="task-list">
              {taskState.map((task, index) => (
                <div className={`task-row ${task.done ? 'done' : ''}`} key={task.title}>
                  <button className="task-check" onClick={() => setTaskState(taskState.map((item, i) => i === index ? { ...item, done: !item.done } : item))}>{task.done && <Check size={15} />}</button>
                  <div><strong>{task.title}</strong><small><Clock3 size={13} />{task.meta}</small></div>
                  <Badge tone={task.tone}>{task.tag}</Badge>
                  <button className="icon-button"><MoreHorizontal size={18} /></button>
                </div>
              ))}
            </div>
          </Card>
        </div>

        <aside className="dashboard-aside">
          <Card ai className="ai-summary-card">
            <div className="ai-card-title"><span><Bot size={18} /></span><div><h2>AI 智能助手</h2><small>已分析 1,284 条业务动态</small></div></div>
            <div className="ai-priority">
              <div><span className="company-avatar">星</span><div><strong>星云科技</strong><small>刚刚</small></div><Badge tone="warning">重点关注</Badge></div>
              <p><b>建议：</b>下午联系其技术负责人，重点沟通私有化部署与数据安全能力。</p>
              <div className="reason"><Sparkles size={14} />客户昨日多次查看私有化部署方案</div>
              <Button onClick={() => navigate('customers')}>查看客户详情</Button>
            </div>
            <div className="ai-mini-insight"><AlertTriangle size={17} /><div><b>蓝图软件存在流失风险</b><p>超过 7 天未响应，建议立即激活。</p></div></div>
            <div className="ai-mini-insight"><Activity size={17} /><div><b>线索质量正在提升</b><p>官网咨询渠道转化率提升 12%。</p></div></div>
            <button className="text-link" onClick={() => navigate('assistant')}>查看全部 AI 洞察 <ArrowRight size={15} /></button>
          </Card>
          <Card className="goal-card">
            <div className="card-heading"><div><h2>本月目标</h2><p>距离月末还有 10 天</p></div><Gauge size={20} /></div>
            <div className="goal-ring"><span><b>76%</b><small>完成进度</small></span></div>
            <div className="goal-values"><span>已完成 <b>¥2.28M</b></span><span>目标 <b>¥3.00M</b></span></div>
          </Card>
        </aside>
      </div>
    </div>
  )
}

const leadRows = [
  { id: 1, name: '张晓明', company: '北京某科技有限公司', phone: '138 0000 1234', email: 'zhangxm@tech.com', score: 90, level: 'A 级', source: '官网咨询', status: '跟进中', follow: '明天 10:30', owner: '李经理' },
  { id: 2, name: '陈思思', company: '上海贸易实业有限公司', phone: '139 1111 5678', email: 'chenss@sh-trade.cn', score: 75, level: 'B 级', source: '市场活动', status: '新分配', follow: '今天 14:00', owner: '王销售' },
  { id: 3, name: '刘大勇', company: '大连物流仓储中心', phone: '155 8888 9999', email: 'liu_dy@wlcc.com', score: 50, level: 'C 级', source: '主动开发', status: '待处理', follow: '未安排', owner: '公海池' },
  { id: 4, name: '周文清', company: '深圳云杉信息技术', phone: '136 3288 0912', email: 'zhou@yunshan.ai', score: 86, level: 'A 级', source: '客户转介绍', status: '跟进中', follow: '周五 09:30', owner: '李经理' },
  { id: 5, name: '赵子涵', company: '杭州新零售品牌管理', phone: '137 5198 3301', email: 'zhao@newretail.cn', score: 68, level: 'B 级', source: '内容营销', status: '待处理', follow: '7 月 24 日', owner: '李雯' },
]

function ScoreRing({ score }) {
  return <span className={`score-ring ${score >= 80 ? 'high' : score >= 60 ? 'mid' : 'low'}`} style={{ '--score': `${score * 3.6}deg` }}><b>{score}</b></span>
}

function NewLeadModal({ open, onClose, onSubmit }) {
  const [form, setForm] = useState({ name: '', company: '', phone: '', source: '官网咨询' })
  const submit = () => {
    if (!form.name || !form.company) return
    onSubmit(form)
    setForm({ name: '', company: '', phone: '', source: '官网咨询' })
  }
  return (
    <Modal open={open} title="新建销售线索" onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>取消</Button><Button onClick={submit}>创建线索</Button></>}>
      <div className="form-grid">
        <Field label="联系人姓名" required><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="请输入姓名" /></Field>
        <Field label="公司名称" required><input value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })} placeholder="请输入公司名称" /></Field>
        <Field label="手机号码"><input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="请输入手机号" /></Field>
        <Field label="线索来源"><select value={form.source} onChange={(e) => setForm({ ...form, source: e.target.value })}><option>官网咨询</option><option>市场活动</option><option>客户转介绍</option><option>主动开发</option></select></Field>
        <Field label="线索备注"><textarea rows="4" placeholder="补充客户需求、预算或其他关键信息" /></Field>
        <div className="ai-form-tip"><Sparkles size={17} /><span><b>AI 自动补全</b><small>创建后将结合企业公开信息完善客户画像并给出首次跟进建议。</small></span></div>
      </div>
    </Modal>
  )
}

export function LeadsPage({ can, notify }) {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('全部状态')
  const [rows, setRows] = useState(leadRows)
  const [selected, setSelected] = useState(null)
  const [newOpen, setNewOpen] = useState(false)
  const [view, setView] = useState('list')

  const filtered = useMemo(() => rows.filter((row) => {
    const matchesSearch = `${row.name}${row.company}${row.phone}`.toLowerCase().includes(search.toLowerCase())
    return matchesSearch && (status === '全部状态' || row.status === status)
  }), [rows, search, status])

  const createLead = (form) => {
    setRows([{ id: Date.now(), ...form, email: '待补充', score: 62, level: 'B 级', status: '新分配', follow: '未安排', owner: '李雯' }, ...rows])
    setNewOpen(false)
    notify(`线索“${form.name}”已创建`)
  }

  return (
    <div className="page leads-page">
      <PageHeader title="线索管理" description={`当前共有 ${fmt.format(1284 + rows.length - leadRows.length)} 条线索，其中 42 条待跟进`} actions={<><div className="view-switch"><button className={view === 'list' ? 'active' : ''} onClick={() => setView('list')}><List size={16} />列表</button><button className={view === 'board' ? 'active' : ''} onClick={() => setView('board')}><LayoutGrid size={16} />看板</button></div>{can('crm:lead:create') && <Button icon={Plus} onClick={() => setNewOpen(true)}>新建线索</Button>}</>} />
      <Card className="filter-card">
        <div className="filter-search"><Search size={17} /><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="搜索姓名、公司或手机号" /></div>
        <label><span>状态</span><select value={status} onChange={(e) => setStatus(e.target.value)}><option>全部状态</option><option>跟进中</option><option>新分配</option><option>待处理</option></select></label>
        <label><span>来源</span><select><option>全部来源</option><option>官网咨询</option><option>市场活动</option><option>客户转介绍</option></select></label>
        <label><span>负责人</span><select><option>我的线索</option><option>下属线索</option><option>公海线索</option></select></label>
        <button className="more-filter"><Filter size={16} />更多筛选</button>
        {can('crm:lead:export') && <button className="export-button" onClick={() => notify('线索数据导出任务已创建')}><Download size={16} />导出</button>}
      </Card>

      {view === 'list' ? (
        <Card className="table-card">
          <div className="data-table-wrap">
            <table className="data-table leads-table">
              <thead><tr><th><input type="checkbox" aria-label="全选" /></th><th>姓名 / 公司</th><th>手机 / 邮箱</th><th>AI 评分</th><th>线索等级</th><th>来源</th><th>状态</th><th>下次跟进</th><th>负责人</th><th>操作</th></tr></thead>
              <tbody>
                {filtered.map((row) => (
                  <tr key={row.id} onClick={() => setSelected(row)}>
                    <td onClick={(e) => e.stopPropagation()}><input type="checkbox" aria-label={`选择${row.name}`} /></td>
                    <td><strong>{row.name}</strong><small>{row.company}</small></td>
                    <td><span>{row.phone}</span><small>{row.email}</small></td>
                    <td><ScoreRing score={row.score} /></td>
                    <td><Badge tone={row.level.startsWith('A') ? 'success' : row.level.startsWith('B') ? 'warning' : 'neutral'}>{row.level}</Badge></td>
                    <td>{row.source}</td>
                    <td><Badge dot tone={row.status === '跟进中' ? 'info' : row.status === '待处理' ? 'danger' : 'neutral'}>{row.status}</Badge></td>
                    <td className={row.follow.startsWith('今天') ? 'urgent' : ''}>{row.follow}</td>
                    <td><span className="owner"><i>{row.owner.slice(0, 1)}</i>{row.owner}</span></td>
                    <td><button className="icon-button" onClick={(e) => { e.stopPropagation(); setSelected(row) }}><MoreHorizontal size={18} /></button></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!filtered.length && <div className="empty-table"><Search size={26} /><b>未找到匹配线索</b><span>尝试调整搜索词或筛选条件</span></div>}
          </div>
          <div className="table-footer"><span>显示 1–{filtered.length} 条，共 1,284 条</span><div className="pagination"><button>‹</button><button className="active">1</button><button>2</button><button>3</button><button>›</button></div></div>
        </Card>
      ) : (
        <div className="lead-board">
          {['新分配', '待处理', '跟进中'].map((column) => (
            <Card key={column} className="lead-board-column"><div className="column-title"><span><i />{column}</span><b>{rows.filter((row) => row.status === column).length}</b></div>{rows.filter((row) => row.status === column).map((row) => <button className="lead-mini-card" onClick={() => setSelected(row)} key={row.id}><span><b>{row.name}</b><ScoreRing score={row.score} /></span><small>{row.company}</small><em>{row.owner} · {row.follow}</em></button>)}</Card>
          ))}
        </div>
      )}

      <aside className={`detail-drawer ${selected ? 'open' : ''}`}>
        {selected && <>
          <div className="drawer-head"><div><span className="company-avatar large">{selected.name.slice(0, 1)}</span><div><h2>{selected.name}</h2><p>{selected.company}</p></div></div><button className="icon-button" onClick={() => setSelected(null)}><X size={20} /></button></div>
          <div className="drawer-actions"><Button icon={Phone} onClick={() => notify('已唤起电话联系入口')}>立即联系</Button><Button variant="secondary" icon={MessageCircleMore}>添加跟进</Button></div>
          <div className="drawer-content">
            <Card ai className="lead-ai-analysis"><div className="ai-card-title"><span><Sparkles size={17} /></span><div><h3>AI 深度洞察</h3><small>刚刚更新</small></div></div><div className="lead-score"><ScoreRing score={selected.score} /><div><span>线索等级</span><b>{selected.level} · 高意向</b><small>较昨日上升 6 分</small></div></div><p>客户正在寻找可私有化部署的客户管理方案，预算与采购周期较明确，建议 24 小时内完成首次需求访谈。</p><div className="recommended-action"><b>推荐下一步</b><span><CheckCircle2 size={15} />发送行业案例和部署架构图</span><span><CheckCircle2 size={15} />确认决策链与预算审批节点</span></div></Card>
            <div className="drawer-section"><h3>基础信息</h3><dl><div><dt><Phone size={14} />手机号</dt><dd>{selected.phone}</dd></div><div><dt><Mail size={14} />电子邮箱</dt><dd>{selected.email}</dd></div><div><dt><Globe2 size={14} />线索来源</dt><dd>{selected.source}</dd></div><div><dt><Users size={14} />负责人</dt><dd>{selected.owner}</dd></div></dl></div>
            <div className="drawer-section risk"><h3>潜在风险</h3><p><AlertTriangle size={16} />AI 监测到客户近期浏览过竞品对比资料，建议突出数据安全与本地服务能力。</p></div>
          </div>
        </>}
      </aside>
      {selected && <div className="drawer-scrim" onClick={() => setSelected(null)} />}
      <NewLeadModal open={newOpen} onClose={() => setNewOpen(false)} onSubmit={createLead} />
    </div>
  )
}

export function CustomerPage({ can, notify }) {
  const [tab, setTab] = useState('概览')
  const tabs = ['概览', '联系人 (8)', '商机 (3)', '跟进记录', '任务 (12)', '相关文档', 'AI 智能分析']
  return (
    <div className="page customer-page">
      <Card className="customer-hero">
        <div className="customer-identity">
          <span className="company-logo"><Building2 size={29} /></span>
          <div><div className="customer-name"><h1>极星未来科技有限公司</h1><Badge tone="info">高价值客户</Badge></div><div className="customer-meta"><span><Building2 size={15} />智能制造 / 机器人</span><span><Users size={15} />1000–5000 人</span><span><MapPin size={15} />上海市浦东新区</span><span><Target size={15} />负责人：张明</span><span><Clock3 size={15} />最后跟进：2 小时前</span></div></div>
        </div>
        <div className="customer-actions">{can('crm:customer:edit') && <Button variant="secondary" icon={Pencil} onClick={() => notify('客户资料编辑入口已打开')}>编辑资料</Button>}<Button icon={Plus} onClick={() => notify('新商机创建入口已打开')}>新增商机</Button></div>
        <div className="customer-tabs">{tabs.map((item) => <button className={tab === item ? 'active' : ''} onClick={() => setTab(item)} key={item}>{item.startsWith('AI') && <Sparkles size={15} />}{item}</button>)}</div>
      </Card>

      {tab !== '概览' ? <CustomerTabContent tab={tab} /> : <div className="customer-layout">
        <div className="customer-main">
          <div className="customer-top-grid">
            <Card className="relation-card"><div className="card-heading"><div><h2><Network size={18} />决策链关系图</h2><p>已识别 4 位关键决策人</p></div><button>全屏查看</button></div><div className="relation-map"><i className="relation-line" /><span className="person cto"><b>CTO</b><small>刘伟</small></span><span className="person ceo"><b>CEO</b><small>王立国</small></span><span className="person purchase"><b>采购</b><small>赵宁</small></span><span className="person cfo"><b>CFO</b><small>李静</small></span></div></Card>
            <Card className="profile-card"><div className="card-heading"><div><h2><Target size={18} />客户画像标签</h2><p>由 AI 与业务数据共同生成</p></div><Button variant="ghost" icon={Pencil}>管理</Button></div><div className="tag-cloud"><Badge tone="info">上市企业</Badge><Badge tone="warning">数字化先锋</Badge><Badge tone="success">高复购率</Badge><Badge tone="neutral">核心代理商</Badge><Badge tone="danger">竞品防御中</Badge></div><dl className="profile-details"><div><dt>所属地区</dt><dd>上海市浦东新区</dd></div><div><dt>信用等级</dt><dd><Badge tone="success">AAA</Badge></dd></div><div><dt>年度采购额</dt><dd>¥8.6M</dd></div><div><dt>合作时长</dt><dd>3 年 4 个月</dd></div></dl></Card>
          </div>
          <Card className="timeline-card"><div className="card-heading"><div><h2><Waypoints size={18} />交互时间轴</h2><p>近 30 天关键客户互动</p></div><button>查看全部</button></div><div className="timeline"><div><span><Mail /></span><div><b>发送了季度数字化方案建议书</b><p>张明发送至刘总（CTO），对方已阅读 3 次</p></div><time>今天 10:45</time></div><div><span><Phone /></span><div><b>电话沟通：二期项目预算确认</b><p>通话时长 15 分钟，客户确认预算区间</p></div><time>昨天 16:20</time></div><div><span><Building2 /></span><div><b>线下拜访：工厂自动化车间实地勘察</b><p>已达成初步试点共识</p></div><time>7 月 18 日</time></div></div></Card>
          <div className="customer-bottom-grid"><Card><div className="card-heading"><div><h2>活跃商机</h2><p>当前 3 个推进中项目</p></div><button>查看全部</button></div><div className="active-deal"><div><b>智能产线二期扩容</b><strong>¥1.2M</strong></div><span>方案演示阶段</span><div className="progress"><i style={{ width: '70%' }} /></div><small>赢单率 70% · 预计 8 月成交</small></div></Card><Card><div className="card-heading"><div><h2>关注焦点 & 疑虑</h2><p>来自沟通记录的智能提取</p></div></div><div className="concern-list"><div><AlertTriangle /><span><b>系统兼容性担忧</b><small>担心与现有 ERP 系统无法无缝对接</small></span></div><div><Zap /><span><b>追求极致效率</b><small>重视 AI 对人力成本的替代率</small></span></div></div></Card></div>
        </div>
        <aside className="customer-ai-column">
          <Card ai className="customer-ai-card"><div className="ai-card-title"><span><Sparkles size={19} /></span><div><h2>AI 客户深度总结</h2><small>整合 86 条客户交互生成</small></div></div><span className="section-caption">当前核心需求</span><blockquote>极星未来正处于从“半自动化”向“全面智造”转型的关键期。其核心痛点是<b>多工厂协同数据同步延迟</b>。</blockquote><div className="ai-metrics"><div><small>预算范围</small><b>2.5M–4.0M</b></div><div><small>采购周期</small><b>3–6 个月</b></div></div><span className="section-caption">关键决策者意图</span><div className="intent-row"><span>CEO</span><div><b>王立国（倾向性）</b><i><em style={{ width: '88%' }} /></i></div><small>高</small></div><div className="intent-row"><span>CFO</span><div><b>李静（倾向性）</b><i><em style={{ width: '52%' }} /></i></div><small>中</small></div><div className="risk-box"><AlertTriangle size={18} /><div><b>潜在风险</b><p>竞争对手近期与客户采购部频繁接触，可能采取低价策略。</p></div></div><Button icon={Sparkles} onClick={() => notify('已生成 3 条下一步行动建议')}>获取 AI 建议的下一步行动</Button></Card>
          <Card className="timing-card"><span><Zap size={20} /></span><div><b>本周最佳时机</b><p>客户将在周四举行技术研讨会，建议提前提交技术白皮书。</p></div></Card>
        </aside>
      </div>}
    </div>
  )
}

function CustomerTabContent({ tab }) {
  const map = {
    '联系人 (8)': ['关键联系人', '8 位联系人，覆盖决策、技术、采购与财务角色。', Users],
    '商机 (3)': ['客户商机', '3 个活跃商机，预测金额合计 ¥3.85M。', BriefcaseBusiness],
    '跟进记录': ['跟进记录', '沉淀电话、邮件、会议与拜访记录。', MessageCircleMore],
    '任务 (12)': ['客户任务', '12 项协作任务，其中 3 项将在本周到期。', CheckCircle2],
    '相关文档': ['相关文档', '合同、方案、报价和会议纪要统一归档。', FileText],
    'AI 智能分析': ['AI 智能分析', '基于客户全生命周期数据生成预测、风险与行动建议。', Sparkles],
  }
  const [title, desc, Icon] = map[tab]
  return <Card className="tab-placeholder"><span><Icon size={26} /></span><h2>{title}</h2><p>{desc}</p><Button variant="secondary">进入{title}</Button></Card>
}

const opportunityColumns = [
  { key: 'Discovery', label: '需求发现', amount: '¥1.2M', tone: 'orange', cards: [
    { title: '企业级 AI 视觉识别系统采购', company: '特斯拉中国', amount: '¥450,000', probability: 25, risk: true, owner: '陈' },
    { title: '智慧物流数字化转型项目', company: '中外运', amount: '¥180,000', probability: 15, owner: '林' },
    { title: '零售店智能化升级第一期', company: '名创优品', amount: '¥320,000', probability: 45, ai: true, owner: '周' },
  ] },
  { key: 'Validation', label: '方案验证', amount: '¥2.4M', tone: 'blue', cards: [
    { title: '智能客服系统集成案', company: '携程', amount: '¥1,200,000', probability: 40, owner: '王' },
    { title: 'API 数据同步接口开发', company: '美团', amount: '¥680,000', probability: 35, owner: '李' },
  ] },
  { key: 'Solution', label: '方案报价', amount: '¥3.8M', tone: 'purple', cards: [
    { title: '海外市场 CRM 部署项目', company: '安克创新', amount: '¥2,400,000', probability: 60, owner: '赵' },
    { title: '渠道营销自动化平台', company: '海尔智家', amount: '¥860,000', probability: 68, owner: '徐' },
  ] },
  { key: 'Won', label: '已成交', amount: '¥5.6M', tone: 'green', cards: [
    { title: '全球总部智能管理系统', company: '极星未来', amount: '¥3,200,000', probability: 100, owner: '张' },
  ] },
]

export function OpportunitiesPage({ can, notify }) {
  const [view, setView] = useState('board')
  const [panelOpen, setPanelOpen] = useState(true)
  const [selected, setSelected] = useState(opportunityColumns[0].cards[0])
  return (
    <div className={`page opportunity-page ${panelOpen ? 'with-panel' : ''}`}>
      <PageHeader title="商机看板" description="共 42 个商机 · 预测总金额 ¥13.0M" actions={<><div className="view-switch"><button className={view === 'list' ? 'active' : ''} onClick={() => setView('list')}><List size={16} />列表</button><button className={view === 'board' ? 'active' : ''} onClick={() => setView('board')}><LayoutGrid size={16} />看板</button></div>{can('crm:opportunity:create') && <Button icon={Plus} onClick={() => notify('商机创建入口已打开')}>新建商机</Button>}</>} />
      <div className="opportunity-filters"><Badge tone="neutral">全部团队</Badge><Badge tone="neutral">本季度</Badge><button><Filter size={15} />更多筛选</button><span>拖动卡片即可推进商机阶段</span></div>
      {view === 'board' ? <div className="kanban-board">
        {opportunityColumns.map((column) => <div className="kanban-column" key={column.key}><div className={`kanban-title ${column.tone}`}><div><span>{column.label}</span><Badge>{column.cards.length}</Badge></div><b>{column.amount}</b></div><div className="kanban-stack">{column.cards.map((card) => <button className={`deal-card ${card.risk ? 'risk' : ''} ${selected?.title === card.title ? 'selected' : ''}`} key={card.title} onClick={() => { setSelected(card); setPanelOpen(true) }}><GripVertical className="drag" size={16} /> <div className="deal-flags">{card.risk ? <Badge tone="danger"><AlertTriangle size={11} />高风险</Badge> : card.ai ? <Badge tone="warning"><Sparkles size={11} />AI 推荐</Badge> : <Badge>常规</Badge>}<b>{card.probability}%</b></div><h3>{card.title}</h3><p>{card.company}</p><div className="deal-divider" /><small>商机金额</small><div className="deal-value"><strong>{card.amount}</strong><span className="avatar mini">{card.owner}</span></div>{card.ai && <div className="deal-ai-note"><Sparkles size={13} />采购负责人正在查看产品手册，建议立即致电</div>}</button>)}</div></div>)}
      </div> : <Card className="table-card"><div className="data-table-wrap"><table className="data-table"><thead><tr><th>商机名称</th><th>客户</th><th>阶段</th><th>金额</th><th>赢单率</th><th>负责人</th></tr></thead><tbody>{opportunityColumns.flatMap((column) => column.cards.map((card) => <tr key={card.title} onClick={() => { setSelected(card); setPanelOpen(true) }}><td><strong>{card.title}</strong></td><td>{card.company}</td><td><Badge>{column.label}</Badge></td><td>{card.amount}</td><td>{card.probability}%</td><td><span className="avatar mini">{card.owner}</span></td></tr>))}</tbody></table></div></Card>}
      <aside className={`opportunity-panel ${panelOpen ? 'open' : ''}`}>
        <div className="assistant-head"><div className="ai-title-icon"><Sparkles size={18} /></div><div><strong>AI 跟进建议</strong><small>针对当前商机实时生成</small></div><button className="icon-button" onClick={() => setPanelOpen(false)}><X size={18} /></button></div>
        {selected && <div className="opportunity-panel-body"><div className="selected-deal"><Badge tone={selected.risk ? 'danger' : 'warning'}>{selected.risk ? '赢率预警' : '关键商机'}</Badge><h2>{selected.title}</h2><p>{selected.company} · {selected.amount}</p><div className="probability-line"><span>当前赢单率</span><b>{selected.probability}%</b></div><div className="progress"><i style={{ width: `${selected.probability}%` }} /></div></div><Card className="action-suggestion"><h3><Sparkles size={17} />建议下一步</h3><p>项目近期缺乏有效互动，建议预约下周二的技术演示，并同步发送行业 ROI 测算。</p><Button onClick={() => notify('AI 邮件草案已生成')}>立即生成邮件草案</Button></Card><Card><span className="section-caption">关键动态</span><ul className="signal-list"><li><i />客户下载了《成本效益分析报告》</li><li><i />决策人在公开平台关注数字化新动向</li><li><i />采购周期预计缩短 2 周</li></ul></Card><div className="panel-chat"><input placeholder="问问 AI 营销助手…" /><button><Send size={17} /></button></div></div>}
      </aside>
    </div>
  )
}

const users = [
  { name: '张晓明', email: 'xiaoming.z@company.com', role: '高级销售主管', department: '华南事业部 / 一组', scope: '部门可见', ai: ['预测', '邮件'], status: '正常', color: 'blue' },
  { name: '王志强', email: 'zhiqiang.w@company.com', role: '区域经理', department: '华南事业部', scope: '全量数据', ai: ['助手', '预测', '外呼'], status: '正常', color: 'orange' },
  { name: '李雯', email: 'liwen@company.com', role: '销售顾问', department: '华南事业部 / 一组', scope: '本人数据', ai: ['助手'], status: '正常', color: 'purple' },
]

export function OrganizationPage({ can, notify }) {
  const [tab, setTab] = useState('users')
  return (
    <div className="page organization-page">
      <PageHeader title="组织与权限" description="管理组织架构、角色权限和 AI 能力授权" actions={can('crm:org:manage') && <Button icon={UserPlus} onClick={() => notify('添加用户入口已打开')}>添加新用户</Button>} />
      <div className="org-layout">
        <Card className="org-tree"><div className="card-heading"><div><h2>组织架构</h2><p>共 1,248 位员工</p></div>{can('crm:org:manage') && <button><Plus size={18} /></button>}</div><div className="tree-list"><button className="root active"><Building2 size={17} /><b>集团总部（HQ）</b><ChevronDown size={15} /></button><button className="depth-1"><BriefcaseBusiness size={16} />营销中心<ChevronDown size={14} /></button><button className="depth-2"><MapPin size={15} />华东事业部</button><button className="depth-2 selected"><MapPin size={15} />华南事业部</button><button className="depth-2"><MapPin size={15} />华北事业部</button><button className="depth-1"><Users size={16} />售前技术部</button><button className="depth-1"><ShieldCheck size={16} />客户成功部</button></div><div className="org-meter"><span>组织席位</span><b>1,248 / 1,500</b><div><i /></div></div></Card>
        <div className="org-content">
          <div className="org-tabs"><button className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>用户列表</button><button className={tab === 'roles' ? 'active' : ''} onClick={() => setTab('roles')}>角色定义</button><button className={tab === 'ai' ? 'active' : ''} onClick={() => setTab('ai')}>AI 工具授权</button></div>
          {tab === 'users' && <Card className="table-card org-table-card"><div className="table-toolbar"><div className="filter-search"><Search size={16} /><input placeholder="搜索员工或角色" /></div><Button variant="secondary" icon={Filter}>高级筛选</Button></div><div className="data-table-wrap"><table className="data-table"><thead><tr><th>员工姓名</th><th>角色</th><th>所属部门</th><th>数据权限</th><th>AI 权限</th><th>状态</th><th>操作</th></tr></thead><tbody>{users.map((user) => <tr key={user.email}><td><div className="user-cell"><span className={`avatar ${user.color}`}>{user.name.slice(0, 1)}</span><div><strong>{user.name}</strong><small>{user.email}</small></div></div></td><td><Badge tone="info">{user.role}</Badge></td><td>{user.department}</td><td><span className="scope-cell"><Eye size={14} />{user.scope}</span></td><td><div className="ai-permissions">{user.ai.map((item) => <span key={item}><Sparkles size={12} />{item}</span>)}</div></td><td><Badge dot tone="success">{user.status}</Badge></td><td><button className="icon-button" disabled={!can('crm:org:manage')}><MoreHorizontal size={18} /></button></td></tr>)}</tbody></table></div></Card>}
          {tab === 'roles' && <RoleDefinitions canManage={can('crm:org:manage')} notify={notify} />}
          {tab === 'ai' && <AiPermissions canManage={can('crm:org:manage')} notify={notify} />}
          <div className="permission-cards"><Card><span><ShieldCheck /></span><div><b>功能与数据权限</b><p>配置菜单、按钮可见性及行级/列级数据过滤规则。</p><button>前往配置 <ArrowRight size={14} /></button></div></Card><Card><span><Bot /></span><div><b>AI 工具权限</b><p>管理预测、内容生成、自动外呼及智能推荐的使用额度。</p><button>分配额度 <ArrowRight size={14} /></button></div></Card><Card><span><UserPlus /></span><div><b>角色工作流</b><p>定义岗位层级与自动审批流转逻辑。</p><button>定义流程 <ArrowRight size={14} /></button></div></Card></div>
        </div>
      </div>
    </div>
  )
}

function RoleDefinitions({ canManage, notify }) {
  const roles = [
    ['系统管理员', '全部模块与全部数据', '12', '最高'],
    ['销售经理', '团队业务模块与下属数据', '36', '高'],
    ['销售顾问', '业务模块与本人数据', '186', '标准'],
    ['市场运营', '线索导入、活动与分析数据', '42', '标准'],
  ]
  return <Card className="role-definitions"><div className="card-heading"><div><h2>角色定义</h2><p>权限由功能、数据范围、字段和 AI 能力共同组成</p></div>{canManage && <Button icon={Plus} onClick={() => notify('角色创建入口已打开')}>新建角色</Button>}</div><div className="role-grid">{roles.map(([name, desc, count, level]) => <div className="role-card" key={name}><span className="role-icon"><KeyRound size={18} /></span><div><h3>{name}</h3><p>{desc}</p><div><Badge>{count} 位用户</Badge><Badge tone={level === '最高' ? 'danger' : level === '高' ? 'warning' : 'neutral'}>{level}权限</Badge></div></div><button className="icon-button" disabled={!canManage}><MoreHorizontal size={18} /></button></div>)}</div></Card>
}

function AiPermissions({ canManage, notify }) {
  const tools = [['AI 客户洞察', '结合客户全量行为生成画像、风险与行动建议', 824, true], ['智能预测', '预测线索意向度、商机赢率与预计成交时间', 612, true], ['营销内容生成', '生成邮件、话术、活动文案与营销素材', 388, true], ['智能外呼', '批量外呼、对话摘要与意向自动识别', 96, false]]
  return <Card className="ai-tool-list"><div className="card-heading"><div><h2>AI 工具授权</h2><p>配置工具状态、授权人数和个人使用额度</p></div></div>{tools.map(([name, desc, count, enabled]) => <div className="ai-tool-row" key={name}><span><Bot size={20} /></span><div><b>{name}</b><p>{desc}</p></div><Badge tone="info">{count} 人已授权</Badge><button className={`toggle ${enabled ? 'on' : ''}`} disabled={!canManage} onClick={() => notify(`${name}状态已更新`)}><i /></button></div>)}</Card>
}

const moduleConfig = {
  followups: { title: '跟进记录', desc: '集中查看电话、邮件、会议和拜访记录', icon: MessageCircleMore, stats: [['今日跟进', '28'], ['待补充记录', '6'], ['本周客户触达', '142']] },
  tasks: { title: '销售任务', desc: '管理个人和团队任务，确保关键动作按时完成', icon: CheckCircle2, stats: [['今日任务', '12'], ['已完成', '8'], ['即将逾期', '3']] },
  assistant: { title: 'AI 营销助手', desc: '围绕客户、线索与商机数据进行智能问答与内容生成', icon: Bot, stats: [['今日对话', '36'], ['节省工时', '8.4h'], ['采纳建议', '18']] },
  knowledge: { title: '知识库', desc: '统一管理产品、行业、案例和销售方法论', icon: FileText, stats: [['知识文档', '428'], ['本周更新', '24'], ['AI 已索引', '96%']] },
}

export function SimpleModulePage({ type, notify }) {
  const config = moduleConfig[type]
  const Icon = config.icon
  return <div className="page simple-module"><PageHeader title={config.title} description={config.desc} actions={<Button icon={Plus} onClick={() => notify(`${config.title}创建入口已打开`)}>新建</Button>} /><div className="module-stats">{config.stats.map(([label, value]) => <Card key={label}><span><Icon size={18} /></span><small>{label}</small><b>{value}</b></Card>)}</div><Card className="module-main-card"><div className="module-illustration"><Icon size={32} /><Sparkles size={20} /></div><h2>{config.title}工作区</h2><p>此模块已纳入统一导航、权限和主题体系，可在后续阶段接入对应业务接口。</p><div className="module-demo-list">{['创建并分配新的工作项', '结合客户上下文生成智能建议', '查看团队进展与数据分析'].map((item, index) => <button key={item}><span>{index + 1}</span><b>{item}</b><ArrowRight size={16} /></button>)}</div></Card></div>
}

export function SettingsPage({ preferences, onUpdate, notify }) {
  const [draft, setDraft] = useState(preferences)
  const [saved, setSaved] = useState(true)
  const fileRef = useRef(null)
  const accents = ['#f45b0b', '#2563eb', '#7c3aed', '#0891b2', '#16a34a']

  const update = (patch) => {
    const next = { ...draft, ...patch }
    setDraft(next)
    onUpdate(next)
    setSaved(false)
  }
  const uploadLogo = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) return notify('请选择图片格式的 Logo', 'info')
    if (file.size > 1024 * 1024) return notify('Logo 图片请控制在 1MB 以内', 'info')
    const reader = new FileReader()
    reader.onload = () => update({ logo: reader.result })
    reader.readAsDataURL(file)
  }
  const save = () => { onUpdate(draft); setSaved(true); notify('品牌与外观设置已保存') }

  return <div className="page settings-page"><PageHeader title="系统设置" description="管理品牌、外观、登录安全和系统偏好" actions={<Button icon={Check} disabled={saved} onClick={save}>{saved ? '已保存' : '保存更改'}</Button>} /><div className="settings-layout"><aside className="settings-nav"><button className="active"><Palette size={17} />品牌与外观</button><button><ShieldCheck size={17} />登录与安全</button><button><Bell size={17} />消息通知</button><button><Settings2 size={17} />系统参数</button></aside><div className="settings-content">
    <Card className="settings-section"><div className="settings-section-head"><div><h2>企业品牌</h2><p>自定义 Logo 会应用于登录页、侧栏和移动端导航。</p></div><Badge tone="success">已启用</Badge></div><div className="logo-setting"><div className="logo-preview">{draft.logo ? <img src={draft.logo} alt="当前 Logo" /> : <Building2 size={31} />}</div><div><b>企业 Logo</b><p>建议使用透明背景 PNG 或 SVG，尺寸不小于 128 × 128px，最大 1MB。</p><div><Button variant="secondary" icon={Upload} onClick={() => fileRef.current?.click()}>上传 Logo</Button>{draft.logo && <Button variant="ghost" onClick={() => update({ logo: '' })}>恢复默认</Button>}</div><input ref={fileRef} hidden type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" onChange={uploadLogo} /></div></div><Field label="系统名称"><input defaultValue={APP_NAME} disabled /><small>产品主名称已按本项目要求统一，不建议租户侧修改。</small></Field></Card>
    <Card className="settings-section"><div className="settings-section-head"><div><h2>界面主题</h2><p>主题选项会即时预览，保存后应用于当前浏览器。</p></div></div><div className="theme-options">{[['light', '浅色模式'], ['dark', '深色模式'], ['system', '跟随系统']].map(([key, label]) => <button className={draft.theme === key ? 'active' : ''} onClick={() => update({ theme: key })} key={key}><span className={`theme-thumb ${key}`}><i /><b /><em /></span><div><b>{label}</b><small>{key === 'light' ? '明亮清晰' : key === 'dark' ? '低光舒适' : '自动切换'}</small></div>{draft.theme === key && <Check size={16} />}</button>)}</div></Card>
    <Card className="settings-section"><div className="settings-section-head"><div><h2>品牌强调色</h2><p>用于主要按钮、选中状态和 AI 能力标识。</p></div></div><div className="accent-options">{accents.map((color) => <button className={draft.accent === color ? 'active' : ''} style={{ background: color }} key={color} onClick={() => update({ accent: color })}>{draft.accent === color && <Check size={17} />}</button>)}<label><span>自定义</span><input type="color" value={draft.accent} onChange={(e) => update({ accent: e.target.value })} /></label></div></Card>
    <Card className="settings-section"><div className="settings-section-head"><div><h2>内容密度</h2><p>根据屏幕尺寸和工作习惯调整信息密度。</p></div></div><div className="density-options">{[['comfortable', '舒适', '更大的留白，适合日常办公'], ['compact', '紧凑', '同屏展示更多表格数据']].map(([key, label, desc]) => <label className={draft.density === key ? 'active' : ''} key={key}><input type="radio" checked={draft.density === key} onChange={() => update({ density: key })} /><span><b>{label}</b><small>{desc}</small></span></label>)}</div></Card>
    <div className="settings-save-bar"><span>{saved ? <><CheckCircle2 size={16} />所有更改均已保存</> : '您有未保存的外观更改'}</span><Button onClick={save} disabled={saved}>保存设置</Button></div>
  </div></div></div>
}
