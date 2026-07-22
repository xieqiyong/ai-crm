import { useEffect, useState } from 'react'
import { AlertTriangle, Check, Info, X, XCircle } from 'lucide-react'
import { Button } from './base'

export function Toast({ message, tone, onClose }) {
  useEffect(() => {
    const timer = setTimeout(onClose, 2600)
    return () => clearTimeout(timer)
  }, [onClose])

  const Icon = tone === 'success' ? Check : tone === 'danger' ? XCircle : Info

  return (
    <div className={`toast ${tone}`}>
      <span><Icon size={17} /></span>
      <div>
        <b>{tone === 'success' ? '操作成功' : tone === 'danger' ? '操作失败' : '系统提醒'}</b>
        <p>{message}</p>
      </div>
      <button type="button" onClick={onClose} aria-label="关闭提醒"><X size={15} /></button>
    </div>
  )
}

export function useConfirmDialog() {
  const [dialog, setDialog] = useState(null)

  const confirm = (options) => new Promise((resolve) => {
    setDialog({ ...options, resolve })
  })

  const close = (confirmed) => {
    if (dialog?.resolve) {
      dialog.resolve(confirmed)
    }
    setDialog(null)
  }

  return {
    confirm,
    dialogProps: dialog ? {
      ...dialog,
      open: true,
      onCancel: () => close(false),
      onConfirm: () => close(true),
    } : { open: false },
  }
}

export function ConfirmDialog({
  open,
  title = '请确认操作',
  description,
  target,
  tone = 'danger',
  icon: Icon = AlertTriangle,
  confirmText = '确认',
  cancelText = '取消',
  onConfirm,
  onCancel,
}) {
  if (!open) return null

  return (
    <div className="confirm-backdrop" role="presentation" onMouseDown={onCancel}>
      <div className={`confirm-dialog ${tone}`} role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <div className="confirm-icon">
          <Icon size={22} />
        </div>
        <div className="confirm-content">
          <span className="eyebrow">操作确认</span>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
          {target && <div className="confirm-target">{target}</div>}
        </div>
        <div className="confirm-actions">
          <Button variant="secondary" onClick={onCancel}>{cancelText}</Button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={onConfirm}>{confirmText}</Button>
        </div>
      </div>
    </div>
  )
}
