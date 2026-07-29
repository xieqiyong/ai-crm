import { forwardRef, useState } from 'react'
import { Eye, EyeOff, LockKeyhole, X } from 'lucide-react'
import { APP_NAME } from '../config/appConfig'

export const Button = forwardRef(function Button({ children, variant = 'primary', icon: Icon, className = '', type = 'button', ...props }, ref) {
  return (
    <button ref={ref} type={type} className={`button ${variant} ${className}`} {...props}>
      {Icon && <Icon size={17} />}
      <span>{children}</span>
    </button>
  )
})

export function Card({ children, className = '', ai = false, ...props }) {
  return <section className={`card ${ai ? 'ai-card' : ''} ${className}`} {...props}>{children}</section>
}

export function Badge({ children, tone = 'neutral', dot = false }) {
  return <span className={`badge ${tone}`}>{dot && <i />}{children}</span>
}

export function SecretInput({ value, onChange, placeholder, disabled, autoComplete = 'off' }) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="secret-input">
      <input
        type={visible ? 'text' : 'password'}
        value={value || ''}
        onChange={onChange}
        placeholder={placeholder}
        disabled={disabled}
        autoComplete={autoComplete}
      />
      <button
        type="button"
        disabled={disabled}
        aria-label={visible ? '隐藏内容' : '显示内容'}
        onClick={() => setVisible(!visible)}
      >
        {visible ? <EyeOff size={17} /> : <Eye size={17} />}
      </button>
    </div>
  )
}

export function PageHeader({ eyebrow, title, description, actions, children }) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <span className="eyebrow">{eyebrow}</span>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
      {children}
    </div>
  )
}

export function EmptyPermission({ onBack }) {
  return (
    <div className="permission-empty">
      <span><LockKeyhole size={30} /></span>
      <h2>当前角色无权访问</h2>
      <p>该功能需要更高权限。如有工作需要，请联系系统管理员调整菜单权限或数据权限。</p>
      <Button onClick={onBack}>返回工作台</Button>
    </div>
  )
}

export function Modal({ open, title, children, onClose, footer, size = 'md' }) {
  if (!open) return null
  return (
    <div className="modal-backdrop" onMouseDown={onClose} role="presentation">
      <div className={`modal ${size}`} onMouseDown={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
        <div className="modal-head">
          <div>
            <span className="eyebrow">{APP_NAME}</span>
            <h2>{title}</h2>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="关闭"><X size={20} /></button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  )
}

export function Drawer({ open, title, children, onClose, footer, size = 'md' }) {
  if (!open) return null
  return (
    <div className="app-drawer-backdrop" onMouseDown={onClose} role="presentation">
      <aside className={`app-drawer ${size}`} onMouseDown={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
        <div className="app-drawer-head">
          <div>
            <span className="eyebrow">{APP_NAME}</span>
            <h2>{title}</h2>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="关闭"><X size={20} /></button>
        </div>
        <div className="app-drawer-body">{children}</div>
        {footer && <div className="app-drawer-footer">{footer}</div>}
      </aside>
    </div>
  )
}

export function Field({
  label,
  required,
  children,
  hint,
  className = '',
  as: Component = 'label',
}) {
  return (
    <Component className={['field', className].filter(Boolean).join(' ')}>
      <span>{label}{required && <em>*</em>}</span>
      {children}
      {hint && <small>{hint}</small>}
    </Component>
  )
}
