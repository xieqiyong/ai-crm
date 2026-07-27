import { Sparkles, X } from 'lucide-react'
import { MarketingAssistantChatBox, findAssistantRouteLabel } from './MarketingAssistantChatBox'

export function MarketingAssistantDrawer({
  open,
  routeKey,
  routeGroups,
  currentRole,
  onClose,
  onNavigate,
  onNotify,
}) {
  const routeLabel = findAssistantRouteLabel(routeGroups, routeKey)

  return (
    <aside className={`assistant-drawer ${open ? 'open' : ''}`}>
      <div className="assistant-head">
        <div className="ai-title-icon"><Sparkles size={18} /></div>
        <div><strong>AI 营销助手</strong><small>{routeLabel} · 真实业务数据</small></div>
        <button className="icon-button" onClick={onClose} aria-label="关闭"><X size={18} /></button>
      </div>
      <MarketingAssistantChatBox
        routeKey={routeKey}
        currentRole={currentRole}
        onNavigate={onNavigate}
        onNotify={onNotify}
      />
    </aside>
  )
}
