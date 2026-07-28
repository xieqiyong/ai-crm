import { authApi } from './modules/authApi'
import { installApi } from './modules/installApi'
import { agentApi } from './modules/agentApi'
import { adminApi, modelConfigApi } from './modules/adminApi'
import { assistantApi } from './modules/assistantApi'
import { attachmentApi } from './modules/attachmentApi'
import { customerApi, followupApi, leadApi, opportunityApi, productApi } from './modules/crmApi'
import { channelApi } from './modules/channelApi'
import { dashboardApi } from './modules/dashboardApi'
import { knowledgeApi } from './modules/knowledgeApi'
import { observabilityApi } from './modules/observabilityApi'
import { workflowApi } from './modules/workflowApi'

export const api = {
  auth: authApi,
  install: installApi,
  dashboard: dashboardApi,
  lead: leadApi,
  channel: channelApi,
  customer: customerApi,
  product: productApi,
  opportunity: opportunityApi,
  followup: followupApi,
  agent: agentApi,
  assistant: assistantApi,
  attachment: attachmentApi,
  admin: adminApi,
  modelConfig: modelConfigApi,
  knowledge: knowledgeApi,
  workflow: workflowApi,
  observability: observabilityApi,
}

export { request } from './httpClient'
