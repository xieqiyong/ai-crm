import { useEffect, useMemo, useRef, useState } from 'react'
import { Check, ChevronDown, Search } from 'lucide-react'

export function Select({
  value,
  options = [],
  placeholder = '请选择',
  searchPlaceholder = '搜索选项',
  emptyText = '没有匹配的选项',
  searchable = false,
  disabled = false,
  className = '',
  onChange,
}) {
  const rootRef = useRef(null)
  const searchRef = useRef(null)
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const safeValue = value === null || value === undefined ? '' : String(value)
  const normalizedOptions = useMemo(
    () => options.map((item) => ({ ...item, value: String(item.value) })),
    [options],
  )
  const selected = normalizedOptions.find((item) => item.value === safeValue)
  const visibleOptions = useMemo(() => {
    const searchValue = keyword.trim().toLowerCase()
    if (!searchValue) return normalizedOptions
    return normalizedOptions.filter((item) => (
      `${item.label || ''} ${item.description || ''}`.toLowerCase().includes(searchValue)
    ))
  }, [keyword, normalizedOptions])

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

  useEffect(() => {
    if (!open) {
      setKeyword('')
      return
    }
    if (searchable) {
      window.setTimeout(() => searchRef.current?.focus(), 0)
    }
  }, [open, searchable])

  const choose = (option) => {
    if (option.disabled) return
    onChange?.(option.value, option)
    setOpen(false)
  }

  return (
    <div
      ref={rootRef}
      className={['app-select', open ? 'open' : '', disabled ? 'disabled' : '', className]
        .filter(Boolean)
        .join(' ')}
    >
      <button
        type="button"
        className="app-select-trigger"
        aria-haspopup="listbox"
        aria-expanded={open}
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
      >
        <span className={selected ? '' : 'placeholder'}>{selected?.label || placeholder}</span>
        <ChevronDown size={17} />
      </button>
      {open && (
        <div className="app-select-dropdown">
          {searchable && (
            <div className="app-select-search">
              <Search size={15} />
              <input
                ref={searchRef}
                value={keyword}
                placeholder={searchPlaceholder}
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>
          )}
          <div className="app-select-options" role="listbox">
            {visibleOptions.map((option) => (
              <button
                type="button"
                role="option"
                aria-selected={option.value === safeValue}
                className={option.value === safeValue ? 'selected' : ''}
                disabled={option.disabled}
                onClick={() => choose(option)}
                key={option.value}
              >
                <span>
                  <b>{option.label}</b>
                  {option.description && <small>{option.description}</small>}
                </span>
                {option.value === safeValue && <Check size={17} />}
              </button>
            ))}
            {!visibleOptions.length && <div className="app-select-empty">{emptyText}</div>}
          </div>
        </div>
      )}
    </div>
  )
}
