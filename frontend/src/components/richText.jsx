import { useEffect, useRef, useState } from 'react'
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Bold,
  Heading2,
  Highlighter,
  ImagePlus,
  Italic,
  Link as LinkIcon,
  List,
  ListOrdered,
  Palette,
  Quote,
  Redo2,
  RemoveFormatting,
  Underline,
  Undo2,
} from 'lucide-react'
import { api } from '../api'
import { runtimeConfig } from '../config/env'

const symbolItems = ['✅', '⚠️', '📌', '📞', '🤝', '💰']

function exec(command, value = null) {
  document.execCommand(command, false, value)
}

function sanitizeHtml(value) {
  return String(value || '')
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/\son[a-z]+="[^"]*"/gi, '')
    .replace(/\son[a-z]+='[^']*'/gi, '')
    .replace(/javascript:/gi, '')
}

function normalizeBackendBase() {
  const baseUrl = runtimeConfig.apiBaseUrl || ''
  if (!/^https?:\/\//i.test(baseUrl)) return ''
  return baseUrl.replace(/\/api\/?$/i, '').replace(/\/$/, '')
}

function resolveAssetUrl(url) {
  if (!url) return ''
  if (/^(https?:)?\/\//i.test(url) || url.startsWith('data:')) return url
  if (!url.startsWith('/')) return url
  if (!url.startsWith('/uploads')) return url
  const backendBase = normalizeBackendBase()
  return backendBase ? `${backendBase}${url}` : url
}

function ColorControl({ label, value, icon: Icon, onChange }) {
  return (
    <span
      className="rich-editor-color-control"
      title={label}
      style={{ '--rich-editor-tool-color': value }}
    >
      <Icon size={15} />
      <span aria-hidden="true" />
      <input
        type="color"
        value={value}
        aria-label={label}
        onChange={(event) => onChange(event.target.value)}
      />
    </span>
  )
}

export function RichTextEditor({
  value,
  onChange,
  placeholder,
  notify,
  showHeading = true,
  stableTypography = false,
}) {
  const editorRef = useRef(null)
  const fileRef = useRef(null)
  const selectionRef = useRef(null)
  const [focused, setFocused] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [textColor, setTextColor] = useState('#1f2937')
  const [highlightColor, setHighlightColor] = useState('#fff3a3')

  useEffect(() => {
    const editor = editorRef.current
    if (!editor || focused) return
    const nextValue = value || ''
    if (editor.innerHTML !== nextValue) {
      editor.innerHTML = nextValue
    }
  }, [value, focused])

  const updateValue = () => {
    const html = sanitizeHtml(editorRef.current?.innerHTML || '')
    onChange(html)
  }

  const rememberSelection = () => {
    const editor = editorRef.current
    const selection = window.getSelection()
    if (!editor || !selection || selection.rangeCount === 0) return
    const range = selection.getRangeAt(0)
    if (editor === range.commonAncestorContainer || editor.contains(range.commonAncestorContainer)) {
      selectionRef.current = range.cloneRange()
    }
  }

  const restoreSelection = () => {
    const range = selectionRef.current
    if (!range) return
    const selection = window.getSelection()
    if (!selection) return
    selection.removeAllRanges()
    selection.addRange(range)
  }

  const runCommand = (command, commandValue) => {
    editorRef.current?.focus()
    restoreSelection()
    if (['foreColor', 'hiliteColor'].includes(command)) {
      exec('styleWithCSS', true)
    }
    exec(command, commandValue)
    rememberSelection()
    updateValue()
  }

  const changeTextColor = (color) => {
    setTextColor(color)
    runCommand('foreColor', color)
  }

  const changeHighlightColor = (color) => {
    setHighlightColor(color)
    runCommand('hiliteColor', color)
  }

  const addLink = () => {
    const url = window.prompt('请输入链接地址')
    if (!url) return
    runCommand('createLink', url)
  }

  const insertSymbol = (symbol) => {
    runCommand('insertText', symbol)
  }

  const insertUploadedImage = async (file) => {
    setUploading(true)
    try {
      const response = await api.attachment.uploadImage(file)
      if (response?.url) {
        editorRef.current?.focus()
        exec('insertImage', resolveAssetUrl(response.url))
        updateValue()
      }
    } catch (error) {
      notify?.(error.message || '图片上传失败', 'info')
    } finally {
      setUploading(false)
    }
  }

  const uploadImage = async (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    await insertUploadedImage(file)
  }

  const handlePaste = async (event) => {
    const items = Array.from(event.clipboardData?.items || [])
    const imageItems = items.filter((item) => item.type?.startsWith('image/'))
    if (!imageItems.length) return
    event.preventDefault()
    for (const item of imageItems) {
      const file = item.getAsFile()
      if (file) await insertUploadedImage(file)
    }
  }

  return (
    <div className={`rich-editor ${stableTypography ? 'rich-editor-stable' : ''}`}>
      <div className="rich-editor-toolbar" onMouseDown={rememberSelection}>
        <ColorControl label="文字颜色" value={textColor} icon={Palette} onChange={changeTextColor} />
        <ColorControl label="文字高亮" value={highlightColor} icon={Highlighter} onChange={changeHighlightColor} />
        <span className="rich-editor-tool-divider" aria-hidden="true" />
        {showHeading && (
          <button type="button" onClick={() => runCommand('formatBlock', 'h3')} title="标题">
            <Heading2 size={15} />
          </button>
        )}
        <button type="button" onClick={() => runCommand('bold')} title="加粗">
          <Bold size={15} />
        </button>
        <button type="button" onClick={() => runCommand('italic')} title="斜体">
          <Italic size={15} />
        </button>
        <button type="button" onClick={() => runCommand('underline')} title="下划线">
          <Underline size={15} />
        </button>
        <button type="button" onClick={() => runCommand('justifyLeft')} title="左对齐">
          <AlignLeft size={15} />
        </button>
        <button type="button" onClick={() => runCommand('justifyCenter')} title="居中对齐">
          <AlignCenter size={15} />
        </button>
        <button type="button" onClick={() => runCommand('justifyRight')} title="右对齐">
          <AlignRight size={15} />
        </button>
        <button type="button" onClick={() => runCommand('insertUnorderedList')} title="无序列表">
          <List size={15} />
        </button>
        <button type="button" onClick={() => runCommand('insertOrderedList')} title="有序列表">
          <ListOrdered size={15} />
        </button>
        <button type="button" onClick={() => runCommand('formatBlock', 'blockquote')} title="引用">
          <Quote size={15} />
        </button>
        <button type="button" onClick={addLink} title="链接">
          <LinkIcon size={15} />
        </button>
        <button type="button" onClick={() => fileRef.current?.click()} disabled={uploading} title="上传图片">
          <ImagePlus size={15} />
        </button>
        <span className="rich-editor-tool-divider" aria-hidden="true" />
        <button type="button" onClick={() => runCommand('undo')} title="撤销">
          <Undo2 size={15} />
        </button>
        <button type="button" onClick={() => runCommand('redo')} title="重做">
          <Redo2 size={15} />
        </button>
        <button type="button" onClick={() => runCommand('removeFormat')} title="清除格式">
          <RemoveFormatting size={15} />
        </button>
        <span className="rich-editor-symbols">
          {symbolItems.map((item) => (
            <button type="button" key={item} onClick={() => insertSymbol(item)}>{item}</button>
          ))}
        </span>
        <input ref={fileRef} hidden type="file" accept="image/*" onChange={uploadImage} />
      </div>
      <div
        ref={editorRef}
        className="rich-editor-body"
        contentEditable
        data-placeholder={placeholder || '请输入内容'}
        onFocus={() => setFocused(true)}
        onBlur={() => {
          rememberSelection()
          setFocused(false)
          updateValue()
        }}
        onInput={() => {
          rememberSelection()
          updateValue()
        }}
        onKeyUp={rememberSelection}
        onMouseUp={rememberSelection}
        onPaste={handlePaste}
        suppressContentEditableWarning
      />
    </div>
  )
}

export function RichTextViewer({ value, empty = '暂无内容' }) {
  const html = sanitizeHtml(value)
  const hasImage = /<img[\s>]/i.test(html)
  if (!html || (!hasImage && html.replace(/<[^>]*>/g, '').trim().length === 0)) {
    return <p>{empty}</p>
  }
  return <div className="rich-viewer" dangerouslySetInnerHTML={{ __html: html }} />
}

export function CollapsibleRichText({
  value,
  empty = '暂无内容',
  maxHeight = 132,
  expandText = '展开内容',
  collapseText = '收起内容',
}) {
  const contentRef = useRef(null)
  const [expanded, setExpanded] = useState(false)
  const [collapsible, setCollapsible] = useState(false)

  useEffect(() => {
    setExpanded(false)
  }, [value])

  useEffect(() => {
    const element = contentRef.current
    if (!element) return undefined

    const measure = () => {
      setCollapsible(element.scrollHeight > Number(maxHeight || 132) + 2)
    }
    const frame = window.requestAnimationFrame(measure)
    let observer
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(measure)
      observer.observe(element)
    } else {
      window.addEventListener('resize', measure)
    }

    return () => {
      window.cancelAnimationFrame(frame)
      observer?.disconnect()
      window.removeEventListener('resize', measure)
    }
  }, [value, maxHeight])

  const collapsed = collapsible && !expanded

  return (
    <div
      className="collapsible-richtext"
      style={{ '--collapsible-richtext-height': `${maxHeight}px` }}
    >
      <div className={`collapsible-richtext-viewport ${collapsed ? 'is-collapsed' : ''}`}>
        <div ref={contentRef}>
          <RichTextViewer value={value} empty={empty} />
        </div>
        {collapsed && <span className="collapsible-richtext-fade" aria-hidden="true" />}
      </div>
      {collapsible && (
        <button
          type="button"
          className="collapsible-richtext-toggle"
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? collapseText : expandText}
        </button>
      )}
    </div>
  )
}
