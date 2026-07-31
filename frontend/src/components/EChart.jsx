import { useEffect, useRef } from 'react'
import { BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

use([
  BarChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

function readThemeColors() {
  const style = getComputedStyle(document.documentElement)
  return {
    brand: style.getPropertyValue('--brand').trim() || '#f45b0b',
    line: style.getPropertyValue('--line').trim() || '#e7e9ed',
    muted: style.getPropertyValue('--muted').trim() || '#67717e',
    surface: style.getPropertyValue('--surface').trim() || '#ffffff',
    text: style.getPropertyValue('--text').trim() || '#17202a',
  }
}

export function EChart({ option, className = '', ariaLabel = '数据图表' }) {
  const containerRef = useRef(null)
  const chartRef = useRef(null)
  const optionRef = useRef(option)
  optionRef.current = option

  useEffect(() => {
    if (!containerRef.current) return undefined

    const chart = init(containerRef.current)
    chartRef.current = chart
    const render = () => {
      const source = optionRef.current
      const nextOption = typeof source === 'function'
        ? source(readThemeColors())
        : source
      chart.setOption(nextOption || {}, { notMerge: true, lazyUpdate: true })
    }
    render()

    const resizeObserver = typeof ResizeObserver === 'undefined'
      ? null
      : new ResizeObserver(() => chart.resize())
    resizeObserver?.observe(containerRef.current)

    const themeObserver = new MutationObserver(render)
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme', 'style'],
    })

    const resize = () => chart.resize()
    window.addEventListener('resize', resize)
    return () => {
      window.removeEventListener('resize', resize)
      resizeObserver?.disconnect()
      themeObserver.disconnect()
      chart.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    if (!chartRef.current) return
    const nextOption = typeof option === 'function'
      ? option(readThemeColors())
      : option
    chartRef.current.setOption(nextOption || {}, { notMerge: true, lazyUpdate: true })
  }, [option])

  return (
    <div
      ref={containerRef}
      className={`echart ${className}`.trim()}
      role="img"
      aria-label={ariaLabel}
    />
  )
}
