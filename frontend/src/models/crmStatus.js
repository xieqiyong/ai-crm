export const customerLevelText = {
  NORMAL: '普通客户',
  IMPORTANT: '重点客户',
  STRATEGIC: '战略客户',
}

export const customerLevelTone = {
  NORMAL: 'neutral',
  IMPORTANT: 'warning',
  STRATEGIC: 'success',
}

export const recommendedCustomerStatus = 'POTENTIAL'

export const customerStatusText = {
  POTENTIAL: '潜在客户',
  ACTIVE: '正常经营',
  DEALING: '商机推进',
  COOPERATED: '已合作',
  SLEEPING: '沉睡客户',
  CHURNED: '已流失',
  BLACKLIST: '黑名单',
}

export const customerStatusTone = {
  POTENTIAL: 'info',
  ACTIVE: 'success',
  DEALING: 'warning',
  COOPERATED: 'success',
  SLEEPING: 'neutral',
  CHURNED: 'danger',
  BLACKLIST: 'danger',
}

export const customerStatusOptions = Object.entries(customerStatusText).map(([value, label]) => ({ value, label }))

export const recommendedLeadStatus = 'NEW'

export const leadStatusText = {
  NEW: '新线索',
  CONTACTED: '已联系',
  FOLLOWING: '跟进中',
  QUALIFIED: '有效线索',
  NURTURING: '长期培育',
  CONVERTED: '已转化',
  INVALID: '无效线索',
  DUPLICATE: '重复线索',
  CLOSED: '已关闭',
}

export const leadStatusTone = {
  NEW: 'neutral',
  CONTACTED: 'info',
  FOLLOWING: 'info',
  QUALIFIED: 'warning',
  NURTURING: 'warning',
  CONVERTED: 'success',
  INVALID: 'danger',
  DUPLICATE: 'neutral',
  CLOSED: 'danger',
}

export const leadStatusOptions = Object.entries(leadStatusText).map(([value, label]) => ({ value, label }))
