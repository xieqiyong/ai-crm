import { useState } from 'react'

export function useToast() {
  const [toast, setToast] = useState(null)

  const notify = (message, tone = 'success') => {
    setToast({ message, tone, id: Date.now() })
  }

  const closeToast = () => setToast(null)

  return { toast, notify, closeToast }
}
