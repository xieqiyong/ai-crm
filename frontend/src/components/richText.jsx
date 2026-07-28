import { useEffect, useRef, useState } from 'react'
import {
  Bold,
  Heading2,
  ImagePlus,
  Italic,
  Link as LinkIcon,
  List,
  ListOrdered,
  Quote,
  Underline,
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

export function RichTextEditor({ value, onChange, placeholder, notify }) {
  const editorRef = useRef(null)
  const fileRef = useRef(null)
  const [focused, setFocused] = useState(false)
  const [uploading, setUploading] = useState(false)

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

  const runCommand = (command, commandValue) => {
    editorRef.current?.focus()
    exec(command, commandValue)
    updateValue()
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
    <div className="rich-editor">
      <div className="rich-editor-toolbar">
        <button type="button" onClick={() => runCommand('formatBlock', 'h3')} title="标题">
          <Heading2 size={15} />
        </button>
        <button type="button" onClick={() => runCommand('bold')} title="加粗">
          <Bold size={15} />
        </button>
        <button type="button" onClick={() => runCommand('italic')} title="斜体">
          <Italic size={15} />
        </button>
        <button type="button" onClick={() => runCommand('underline')} title="下划线">
          <Underline size={15} />
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
          setFocused(false)
          updateValue()
        }}
        onInput={updateValue}
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
