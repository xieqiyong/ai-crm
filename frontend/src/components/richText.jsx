import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
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
  Minus,
  Palette,
  Plus,
  Quote,
  Redo2,
  RemoveFormatting,
  Underline,
  Undo2,
  X,
  ZoomIn,
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
  const [preview, setPreview] = useState(null)
  const [previewScale, setPreviewScale] = useState(1)

  useEffect(() => {
    if (!preview) return undefined
    const originalOverflow = document.body.style.overflow
    const closeByKeyboard = (event) => {
      if (event.key === 'Escape') setPreview(null)
    }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', closeByKeyboard)
    return () => {
      document.body.style.overflow = originalOverflow
      window.removeEventListener('keydown', closeByKeyboard)
    }
  }, [preview])

  if (!html || (!hasImage && html.replace(/<[^>]*>/g, '').trim().length === 0)) {
    return <p>{empty}</p>
  }
  const openImage = (event) => {
    const image = event.target?.closest?.('img')
    if (!image || !event.currentTarget.contains(image)) return
    event.preventDefault()
    event.stopPropagation()
    setPreviewScale(1)
    setPreview({
      src: image.currentSrc || image.src,
      alt: image.alt || '跟进记录图片',
    })
  }
  const changePreviewScale = (offset) => {
    setPreviewScale((current) => Math.min(4, Math.max(1, Number((current + offset).toFixed(2)))))
  }
  const closePreview = () => {
    setPreview(null)
    setPreviewScale(1)
  }
  return (
    <>
      <div className="rich-viewer" onClick={openImage} dangerouslySetInnerHTML={{ __html: html }} />
      {preview && createPortal(
        <div className="rich-image-preview" role="dialog" aria-modal="true" aria-label="图片预览" onMouseDown={closePreview}>
          <div className="rich-image-preview-toolbar" onMouseDown={(event) => event.stopPropagation()}>
            <button type="button" disabled={previewScale <= 1} onClick={() => changePreviewScale(-0.25)} aria-label="缩小图片">
              <Minus size={18} />
            </button>
            <button type="button" className="rich-image-preview-scale" onClick={() => setPreviewScale(1)} title="恢复原始预览比例">
              {Math.round(previewScale * 100)}%
            </button>
            <button type="button" disabled={previewScale >= 4} onClick={() => changePreviewScale(0.25)} aria-label="放大图片">
              <Plus size={18} />
            </button>
          </div>
          <button type="button" className="rich-image-preview-close" onClick={closePreview} aria-label="关闭图片预览">
            <X size={22} />
          </button>
          <div className="rich-image-preview-content" onMouseDown={(event) => event.stopPropagation()}>
            <div
              className={`rich-image-preview-stage ${previewScale > 1 ? 'zoomed' : ''}`}
              onWheel={(event) => {
                if (!event.ctrlKey) return
                event.preventDefault()
                changePreviewScale(event.deltaY > 0 ? -0.25 : 0.25)
              }}
            >
              <img
                src={preview.src}
                alt={preview.alt}
                style={{ transform: `scale(${previewScale})` }}
                onDoubleClick={() => setPreviewScale((current) => current > 1 ? 1 : 2)}
              />
            </div>
            <span><ZoomIn size={14} />双击快速放大，按住 Ctrl 滚动鼠标滚轮可连续缩放</span>
          </div>
        </div>,
        document.body,
      )}
    </>
  )
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
