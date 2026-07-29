export const leadSourceOptions = [
  { value: 'WEBSITE', label: '官网' },
  { value: 'LANDING_PAGE', label: '落地页' },
  { value: 'SMS', label: '短信' },
  { value: 'WECHAT', label: '微信' },
  { value: 'WECHAT_GROUP', label: '微信群' },
  { value: 'PHONE', label: '电话' },
  { value: 'OFFLINE_EVENT', label: '线下活动' },
  { value: 'LIVE', label: '直播' },
  { value: 'REFERRAL', label: '转介绍' },
  { value: 'AD', label: '广告投放' },
  { value: 'OTHER', label: '其他' },
]

export const leadSourceText = leadSourceOptions.reduce((values, item) => {
  values[item.value] = item.label
  return values
}, {})
