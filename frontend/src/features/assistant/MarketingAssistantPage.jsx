import { Sparkles } from 'lucide-react'
import { Card, EmptyPermission, PageHeader } from '../../components'
import { MarketingAssistantChatBox } from './MarketingAssistantChatBox'

export function MarketingAssistantPage({
  routeKey,
  currentRole,
  navigate,
  notify,
  can,
}) {
  if (!can('crm:assistant:use')) {
    return <EmptyPermission onBack={() => navigate('dashboard')} />
  }

  return (
    <div className="page assistant-page">
      <PageHeader
        eyebrow="AI"
        title="AI 营销助手"
        description="基于真实线索、客户、渠道和商机数据，给销售可执行的建议。"
      />
      <Card className="assistant-panel-card">
        <div className="assistant-panel-head">
          <span><Sparkles size={18} /></span>
          <div>
            <strong>营销客服助手</strong>
            <small>接入业务数据和知识库，只读分析，不直接修改业务数据</small>
          </div>
        </div>
        <MarketingAssistantChatBox
          routeKey={routeKey}
          currentRole={currentRole}
          onNavigate={navigate}
          onNotify={notify}
        />
      </Card>
    </div>
  )
}
