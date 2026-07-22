import { useEffect } from 'react'
import { Check, Sparkles } from 'lucide-react'

export function Toast({ message, tone, onClose }) {
  useEffect(() => {
    const timer = setTimeout(onClose, 2600)
    return () => clearTimeout(timer)
  }, [onClose])

  return (
    <div className={`toast ${tone}`}>
      <span>{tone === 'success' ? <Check size={17} /> : <Sparkles size={17} />}</span>
      {message}
    </div>
  )
}
