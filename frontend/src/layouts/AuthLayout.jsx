import { APP_NAME } from '../config/appConfig'

export function AuthLayout({ children, footer = true }) {
  return (
    <div className="login-page">
      <section className="login-form-side">
        {children}
        {footer && <footer>© 2026 {APP_NAME} · 隐私政策 · 服务条款</footer>}
      </section>
    </div>
  )
}
