import { useEffect, useState } from 'react'
import { api } from '../api'

export function productOptionLabel(item) {
  if (!item) return '-'
  const price = item.price == null ? '未定价' : `¥${Number(item.price || 0).toLocaleString('zh-CN')}`
  return `${item.name || item.id} · ${price}${item.unit ? `/${item.unit}` : ''}`
}

export function productSelectOptions(products = [], current = null) {
  const options = (products || []).map((item) => ({
    value: item.id,
    label: item.name,
    description: item.description || item.code,
  }))
  const currentId = current?.productId
  if (currentId && !options.some((item) => String(item.value) === String(currentId))) {
    options.unshift({
      value: currentId,
      label: current.productName || `产品 ${currentId}`,
      description: '历史关联产品，当前已停用',
      disabled: true,
    })
  }
  return options
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
        const records = await api.product.options()
        if (alive) {
          setOptions(records || [])
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
