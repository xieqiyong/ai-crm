import { useEffect, useMemo, useRef, useState } from 'react'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight, X } from 'lucide-react'

const weekNames = ['一', '二', '三', '四', '五', '六', '日']
const monthNames = Array.from({ length: 12 }).map((_, index) => `${index + 1}月`)
const yearPageSize = 12

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatYmd(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function parseYmd(value) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const parts = value.split('-').map((item) => Number(item))
  const date = new Date(parts[0], parts[1] - 1, parts[2])
  if (
    date.getFullYear() !== parts[0]
    || date.getMonth() !== parts[1] - 1
    || date.getDate() !== parts[2]
  ) {
    return null
  }
  return date
}

function isSameDay(left, right) {
  return left
    && right
    && left.getFullYear() === right.getFullYear()
    && left.getMonth() === right.getMonth()
    && left.getDate() === right.getDate()
}

function buildDays(viewDate) {
  const firstDate = new Date(viewDate.getFullYear(), viewDate.getMonth(), 1)
  const offset = firstDate.getDay() === 0 ? 6 : firstDate.getDay() - 1
  const startDate = new Date(firstDate)
  startDate.setDate(firstDate.getDate() - offset)

  return Array.from({ length: 42 }).map((_, index) => {
    const date = new Date(startDate)
    date.setDate(startDate.getDate() + index)
    return date
  })
}

function formatDisplay(value) {
  const date = parseYmd(value)
  if (!date) return ''
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

export function DatePicker({
  value,
  placeholder = '请选择日期',
  disabled = false,
  className = '',
  onChange,
}) {
  const rootRef = useRef(null)
  const selectedDate = parseYmd(value)
  const [open, setOpen] = useState(false)
  const [viewDate, setViewDate] = useState(selectedDate || new Date())
  const [panel, setPanel] = useState('day')
  const today = useMemo(() => new Date(), [])
  const days = useMemo(() => buildDays(viewDate), [viewDate])
  const yearPageStart = Math.floor(viewDate.getFullYear() / yearPageSize) * yearPageSize
  const years = useMemo(
    () => Array.from({ length: yearPageSize }).map((_, index) => yearPageStart + index),
    [yearPageStart],
  )

  useEffect(() => {
    const nextDate = parseYmd(value)
    if (nextDate) {
      setViewDate(nextDate)
    }
  }, [value])

  useEffect(() => {
    if (!open) return undefined

    const closeOnOutside = (event) => {
      if (!rootRef.current?.contains(event.target)) {
        setOpen(false)
      }
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', closeOnOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  const changeMonth = (step) => {
    setViewDate((current) => new Date(current.getFullYear(), current.getMonth() + step, 1))
  }

  const changePanelPage = (step) => {
    if (panel === 'year') {
      setViewDate((current) => new Date(current.getFullYear() + (step * yearPageSize), current.getMonth(), 1))
      return
    }
    if (panel === 'month') {
      setViewDate((current) => new Date(current.getFullYear() + step, current.getMonth(), 1))
      return
    }
    changeMonth(step)
  }

  const chooseYear = (year) => {
    setViewDate((current) => new Date(year, current.getMonth(), 1))
    setPanel('month')
  }

  const chooseMonth = (month) => {
    setViewDate((current) => new Date(current.getFullYear(), month, 1))
    setPanel('day')
  }

  const openPanel = () => {
    if (panel === 'day') {
      setPanel('year')
      return
    }
    if (panel === 'month') {
      setPanel('year')
    }
  }

  const choose = (date) => {
    onChange?.(formatYmd(date))
    setPanel('day')
    setOpen(false)
  }

  const chooseToday = () => {
    const current = new Date()
    setViewDate(current)
    choose(current)
  }

  const clear = (event) => {
    event.stopPropagation()
    onChange?.('')
    setPanel('day')
    setOpen(false)
  }

  const toggleOpen = () => {
    setOpen((current) => {
      if (!current) {
        setViewDate(selectedDate || new Date())
        setPanel('day')
      }
      return !current
    })
  }

  const panelTitle = panel === 'year'
    ? `${yearPageStart} - ${yearPageStart + yearPageSize - 1}`
    : panel === 'month'
      ? `${viewDate.getFullYear()}年`
      : `${viewDate.getFullYear()}年${viewDate.getMonth() + 1}月`

  return (
    <div
      ref={rootRef}
      className={['date-picker', open ? 'open' : '', disabled ? 'disabled' : '', className]
        .filter(Boolean)
        .join(' ')}
    >
      <button
        type="button"
        className="date-picker-trigger"
        disabled={disabled}
        onClick={toggleOpen}
      >
        <CalendarDays size={17} />
        <span className={value ? '' : 'placeholder'}>{formatDisplay(value) || placeholder}</span>
        {value && (
          <i aria-label="清空日期" onClick={clear}>
            <X size={15} />
          </i>
        )}
      </button>
      {open && (
        <div className="date-picker-popover">
          <div className="date-picker-head">
            <button type="button" aria-label="上一页" onClick={() => changePanelPage(-1)}><ChevronLeft size={18} /></button>
            <button
              type="button"
              className="date-picker-title"
              aria-label={panel === 'day' ? '选择年份' : panel === 'month' ? '重新选择年份' : undefined}
              onClick={openPanel}
            >
              <b>{panelTitle}</b>
              {panel !== 'year' && <ChevronDown size={14} />}
            </button>
            <button type="button" aria-label="下一页" onClick={() => changePanelPage(1)}><ChevronRight size={18} /></button>
          </div>
          {panel === 'year' && (
            <div className="date-picker-options date-picker-years">
              {years.map((year) => (
                <button
                  type="button"
                  className={year === viewDate.getFullYear() ? 'selected' : ''}
                  onClick={() => chooseYear(year)}
                  key={year}
                >
                  {year}年
                </button>
              ))}
            </div>
          )}
          {panel === 'month' && (
            <div className="date-picker-options date-picker-months">
              {monthNames.map((name, index) => (
                <button
                  type="button"
                  className={index === viewDate.getMonth() ? 'selected' : ''}
                  onClick={() => chooseMonth(index)}
                  key={name}
                >
                  {name}
                </button>
              ))}
            </div>
          )}
          {panel === 'day' && (
            <>
              <div className="date-picker-week">
                {weekNames.map((name) => <span key={name}>{name}</span>)}
              </div>
              <div className="date-picker-days">
                {days.map((date) => {
                  const ymd = formatYmd(date)
                  const outside = date.getMonth() !== viewDate.getMonth()
                  const selected = isSameDay(date, selectedDate)
                  const current = isSameDay(date, today)
                  return (
                    <button
                      type="button"
                      className={[
                        outside ? 'outside' : '',
                        selected ? 'selected' : '',
                        current ? 'today' : '',
                      ].filter(Boolean).join(' ')}
                      onClick={() => choose(date)}
                      key={ymd}
                    >
                      {date.getDate()}
                    </button>
                  )
                })}
              </div>
            </>
          )}
          <div className="date-picker-foot">
            <button type="button" onClick={chooseToday}>今天</button>
            <button type="button" onClick={() => { setPanel('day'); setOpen(false) }}>完成</button>
          </div>
        </div>
      )}
    </div>
  )
}
