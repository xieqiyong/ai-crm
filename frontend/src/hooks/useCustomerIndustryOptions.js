import { useEffect, useState } from 'react'
import { api } from '../api'

export function useCustomerIndustryOptions(notify, enabled = true) {
  const [options, setOptions] = useState([])

  useEffect(() => {
    let mounted = true
    if (!enabled) {
      setOptions([])
      return () => {
        mounted = false
      }
    }
    const load = async () => {
      try {
        const data = await api.customer.industryOptions()
        if (mounted) {
          setOptions(data || [])
        }
      } catch (error) {
        if (mounted) {
          notify?.(error.message || '行业选项加载失败', 'info')
        }
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [enabled])

  return options
}
