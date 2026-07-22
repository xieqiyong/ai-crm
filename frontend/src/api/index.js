import { authApi } from './modules/authApi'
import { installApi } from './modules/installApi'
import { agentApi } from './modules/agentApi'
import { adminApi, modelConfigApi } from './modules/adminApi'
import { customerApi, leadApi, opportunityApi } from './modules/crmApi'
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
  opportunity: opportunityApi,
  agent: agentApi,
  admin: adminApi,
  modelConfig: modelConfigApi,
  knowledge: knowledgeApi,
  workflow: workflowApi,
  observability: observabilityApi,
}

export { request } from './httpClient'
