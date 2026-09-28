import {
  Archive,
  BriefcaseBusiness,
  Building2,
  ChevronRight,
  Handshake,
  Target,
  Users,
} from 'lucide-react'
import { Card } from '../../components'

const stageIcons = {
  PUBLIC_POOL: Archive,
  ACTIVE_LEAD: Target,
  CUSTOMER: Users,
  TARGET_CUSTOMER: Building2,
  ACTIVE_OPPORTUNITY: BriefcaseBusiness,
  CONTRACT_CUSTOMER: Handshake,
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

export function MarketingFunnel({ items, loading, navigate }) {
  const stages = items || []

  return (
    <Card className="dashboard-marketing-funnel-card">
      <div className="card-heading dashboard-funnel-heading">
        <div>
          <h2><Target size={17} />营销漏斗</h2>
          <p>从渠道获客到合作成交，点击阶段查看对应业务数据</p>
        </div>
      </div>

      {stages.length ? (
        <div className="dashboard-funnel-scroll">
          <div className="dashboard-funnel-flow">
            {stages.map((item, index) => {
              const Icon = stageIcons[item.code] || Target
              const accessible = item.accessible !== false
              return (
                <button
                  type="button"
                  className={`dashboard-funnel-stage stage-${index + 1}`}
                  key={item.code}
                  disabled={!accessible}
                  onClick={() => accessible && item.targetRoute && navigate(item.targetRoute)}
                  title={accessible ? `查看${item.name}列表` : '当前账号没有查看权限'}
                >
                  <span className="dashboard-funnel-bar">
                    <i><Icon size={17} /></i>
                    {index < stages.length - 1 && <ChevronRight size={15} />}
                  </span>
                  <span className="dashboard-funnel-detail">
                    <small>{item.name}</small>
                    <strong>{accessible ? formatNumber(item.count) : '--'}</strong>
                    <em>{accessible ? item.description : '暂无查看权限'}</em>
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      ) : (
        <div className="dashboard-funnel-loading">
          {loading ? '正在加载营销漏斗' : '暂无可展示的漏斗数据'}
        </div>
      )}
    </Card>
  )
}
