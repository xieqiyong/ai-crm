import { useEffect, useState } from 'react'
import { api } from '../api'

export function productOptionLabel(item) {
  if (!item) return '-'
  const price = item.price == null ? '未定价' : `¥${Number(item.price || 0).toLocaleString('zh-CN')}`
  return `${item.name || item.id} · ${price}${item.unit ? `/${item.unit}` : ''}`
}

export function useProductOptions(notify, enabled = true, canLoad = true) {
  const [options, setOptions] = useState([])

  useEffect(() => {
    let alive = true
    if (!canLoad) {
      setOptions([])
      return () => {
        alive = false
      }
    }
    async function load() {
      try {
        const page = await api.product.page({ pageNo: 1, pageSize: 200, enabled })
        if (alive) {
          setOptions(page?.records || [])
        }
      } catch (error) {
        notify?.(error.message || '产品选项加载失败', 'info')
      }
    }
    load()
    return () => {
      alive = false
    }
  }, [enabled, canLoad])

  return options
}
