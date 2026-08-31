function empty(value) {
  return value === null || value === undefined || String(value).trim().length === 0
}

export function validateCustomerForm(form, industryOptions = []) {
  if (empty(form?.name)) return '请填写客户名称'
  if (empty(form?.industry)) return '请选择行业'
  if (!industryOptions.length) return '行业选项尚未加载完成，请稍后重试'
  if (!industryOptions.some((item) => String(item.value) === String(form.industry))) {
    return '请选择系统支持的行业'
  }
  if (empty(form?.contactName)) return '请填写主要联系人'
  if (empty(form?.contactPhone)) return '请填写联系电话'
  if (empty(form?.contactEmail)) return '请填写联系邮箱'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(form.contactEmail).trim())) {
    return '请填写正确的联系邮箱'
  }
  if (empty(form?.level)) return '请选择客户级别'
  if (empty(form?.status)) return '请选择客户状态'
  if (empty(form?.ownerId)) return '请选择负责人'
  if (empty(form?.productId)) return '请选择意向产品'
  return ''
}
