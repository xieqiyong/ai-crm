import { Rocket } from 'lucide-react'
import { APP_NAME } from '../config/appConfig'

export function BrandLogo({ logo, compact = false, inverse = false }) {
  return (
    <div className={`brand-logo ${compact ? 'compact' : ''} ${inverse ? 'inverse' : ''}`}>
      <span className="logo-mark">
        {logo ? <img src={logo} alt="企业 Logo" /> : <Rocket size={compact ? 18 : 21} strokeWidth={2.4} />}
      </span>
      {!compact && (
        <span className="brand-copy">
          <strong>{APP_NAME}</strong>
        </span>
      )}
    </div>
  )
}
