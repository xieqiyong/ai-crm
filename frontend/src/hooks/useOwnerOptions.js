import { useEffect, useState } from 'react'
import { api } from '../api'

export function useOwnerOptions(notify) {
  const [ownerOptions, setOwnerOptions] = useState([])

  useEffect(() => {
    let mounted = true
    const load = async () => {
      try {
        const data = await api.auth.userOptions()
        if (mounted) {
          setOwnerOptions(data || [])
        }
      } catch (err) {
        if (mounted && notify) {
          notify(err.message || '负责人列表加载失败', 'info')
        }
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [])

  return ownerOptions
}

export function ownerName(row) {
  if (row?.ownerName) {
    return row.ownerName
  }
  if (row?.ownerId) {
    return '未匹配用户'
  }
  return '-'
}

export function ownerOptionLabel(item) {
  if (!item) return ''
  if (item.name && item.username && item.name !== item.username) {
    return `${item.name}（${item.username}）`
  }
  return item.name || item.username || ''
}
