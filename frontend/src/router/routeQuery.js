export function readRouteQueryParam(routeKey, name) {
  const rawRoute = String(routeKey || '')
  const queryIndex = rawRoute.indexOf('?')
  if (queryIndex < 0) return ''
  const params = new URLSearchParams(rawRoute.slice(queryIndex + 1))
  return params.get(name) || ''
}
