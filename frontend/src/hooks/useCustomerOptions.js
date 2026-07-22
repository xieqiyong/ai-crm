import { useEffect, useState } from 'react'
import { api } from '../api'

export function useCustomerOptions(notify, enabled = true) {
  const [customerOptions, setCustomerOptions] = useState([])

  useEffect(() => {
    if (!enabled) {
      setCustomerOptions([])
      return undefined
    }
    let mounted = true
    const load = async () => {
      try {
        const data = await api.customer.page({ pageNo: 1, pageSize: 100 })
        if (mounted) {
          setCustomerOptions(data?.records || [])
        }
      } catch (err) {
        if (mounted && notify) {
          notify(err.message || '客户列表加载失败', 'info')
        }
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [enabled])

  return customerOptions
}

export function customerOptionLabel(item) {
  if (!item) return ''
  const contact = item.contactName ? ` · ${item.contactName}` : ''
  return `${item.name}${contact}`
}
