import { useEffect, useState } from 'react'

function readHash(fallback) {
  return window.location.hash.replace('#/', '') || fallback
}

export function useHashRoute(fallback = 'dashboard') {
  const [routeKey, setRouteKey] = useState(() => readHash(fallback))

  useEffect(() => {
    const onHashChange = () => setRouteKey(readHash(fallback))
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [fallback])

  const navigate = (nextRoute) => {
    window.location.hash = `/${nextRoute}`
    setRouteKey(nextRoute)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return [routeKey, navigate]
}
