import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, UserRound } from 'lucide-react'
import { ownerOptionLabel } from '../hooks/useOwnerOptions'
import { Button, Field, Modal } from './base'
import { Select } from './Select'

export function OwnerAssignModal({
  open,
  title = '分配负责人',
  recordName,
  currentOwnerId,
  currentOwnerName,
  ownerOptions = [],
  submitting = false,
  onClose,
  onConfirm,
}) {
  const [ownerId, setOwnerId] = useState('')

  const currentOwnerAvailable = useMemo(
    () => ownerOptions.some((item) => String(item.id) === String(currentOwnerId || '')),
    [currentOwnerId, ownerOptions],
  )

  useEffect(() => {
    if (!open) return
    setOwnerId(currentOwnerAvailable ? String(currentOwnerId) : '')
  }, [open, currentOwnerId, currentOwnerAvailable])

  const selectedOwner = ownerOptions.find((item) => String(item.id) === String(ownerId))
  const selectOptions = useMemo(() => ownerOptions.map((item) => ({
    value: String(item.id),
    label: item.name || item.username || '未命名用户',
    description: item.name && item.username && item.name !== item.username
      ? `登录账号：${item.username}`
      : '',
  })), [ownerOptions])

  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>取消</Button>
          <Button
            onClick={() => onConfirm(ownerId)}
            disabled={submitting || !ownerId}
          >
            {submitting ? '分配中' : '确认分配'}
          </Button>
        </>
      )}
    >
      <div className="owner-assign-panel">
        <div className="owner-assign-target">
          <span><UserRound size={20} /></span>
          <div>
            <small>当前分配对象</small>
            <b>{recordName || '-'}</b>
          </div>
        </div>
        <div className="owner-assign-flow">
          <div>
            <small>当前负责人</small>
            <b>{currentOwnerName || '暂未分配'}</b>
          </div>
          <ArrowRight size={18} />
          <div>
            <small>分配后负责人</small>
            <b>{selectedOwner ? ownerOptionLabel(selectedOwner) : '请选择'}</b>
          </div>
        </div>
        <Field label="选择负责人" required as="div">
          <Select
            className="owner-assign-select"
            value={ownerId}
            options={selectOptions}
            placeholder="请选择可用用户"
            searchPlaceholder="搜索姓名或登录账号"
            emptyText="没有匹配的可用用户"
            searchable
            disabled={submitting || !ownerOptions.length}
            onChange={setOwnerId}
          />
        </Field>
        {!ownerOptions.length && (
          <div className="owner-assign-empty">当前没有可分配的启用用户，请先在用户管理中检查账号状态。</div>
        )}
      </div>
    </Modal>
  )
}
