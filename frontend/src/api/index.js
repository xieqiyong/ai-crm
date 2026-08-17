import { authApi } from './modules/authApi'
import { installApi } from './modules/installApi'
import { agentApi } from './modules/agentApi'
import { adminApi, modelConfigApi } from './modules/adminApi'
import { assistantApi } from './modules/assistantApi'
import { attachmentApi } from './modules/attachmentApi'
import { customerApi, followupApi, leadApi, opportunityApi, productApi, taskApi } from './modules/crmApi'
import { channelApi } from './modules/channelApi'
import { dashboardApi } from './modules/dashboardApi'
import { knowledgeApi } from './modules/knowledgeApi'
import { observabilityApi } from './modules/observabilityApi'
import { wecomApi } from './modules/wecomApi'
import { notificationApi } from './modules/notificationApi'
import { mailApi } from './modules/mailApi'
import { settingsApi } from './modules/settingsApi'

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
  task: taskApi,
  agent: agentApi,
  assistant: assistantApi,
  attachment: attachmentApi,
  admin: adminApi,
  modelConfig: modelConfigApi,
  knowledge: knowledgeApi,
  observability: observabilityApi,
  wecom: wecomApi,
  notification: notificationApi,
  mail: mailApi,
  settings: settingsApi,
}

export { request } from './httpClient'
