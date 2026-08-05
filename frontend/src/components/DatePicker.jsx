import { useEffect, useMemo, useRef, useState } from 'react'
import { CalendarDays, ChevronLeft, ChevronRight, X } from 'lucide-react'

const weekNames = ['一', '二', '三', '四', '五', '六', '日']

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
  const today = useMemo(() => new Date(), [])
  const days = useMemo(() => buildDays(viewDate), [viewDate])

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

  const choose = (date) => {
    onChange?.(formatYmd(date))
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
    setOpen(false)
  }

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
        onClick={() => setOpen((current) => !current)}
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
            <button type="button" onClick={() => changeMonth(-1)}><ChevronLeft size={18} /></button>
            <b>{viewDate.getFullYear()}年{viewDate.getMonth() + 1}月</b>
            <button type="button" onClick={() => changeMonth(1)}><ChevronRight size={18} /></button>
          </div>
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
          <div className="date-picker-foot">
            <button type="button" onClick={chooseToday}>今天</button>
            <button type="button" onClick={() => setOpen(false)}>完成</button>
          </div>
        </div>
      )}
    </div>
  )
}
