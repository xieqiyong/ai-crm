(() => {
  if (window.__crmChannelCollectorHooked) return
  window.__crmChannelCollectorHooked = true

  const MESSAGE_SOURCE = 'crm-channel-collector-page'
  let armedUntil = 0
  let lastCaptureKey = ''

  function isArmed() {
    return Date.now() < armedUntil
  }

  function isSpreadsheet(contentType, fileName) {
    const type = String(contentType || '').toLowerCase()
    const name = String(fileName || '').toLowerCase()
    return type.includes('spreadsheet')
      || type.includes('excel')
      || name.endsWith('.xlsx')
      || name.endsWith('.xls')
  }

  function looksLikeExportUrl(value) {
    const url = String(value || '').toLowerCase()
    return /export|download|xlsx|excel|sheet_export|file_export/.test(url)
  }

  function requestUrl(input) {
    if (typeof input === 'string') return input
    if (input instanceof URL) return input.href
    return input?.url || ''
  }

  function fileNameFromDisposition(value) {
    const text = String(value || '')
    const encoded = text.match(/filename\*=UTF-8''([^;]+)/i)
    if (encoded) {
      try {
        return decodeURIComponent(encoded[1].replace(/["']/g, ''))
      } catch {
        return encoded[1]
      }
    }
    const plain = text.match(/filename\s*=\s*"?([^";]+)"?/i)
    return plain ? plain[1].trim() : ''
  }

  function normalizeFileName(name, contentType) {
    const text = String(name || '').trim()
    if (/\.(xlsx|xls)$/i.test(text)) return text
    return `${text || '企微智能表格'}.xlsx`
  }

  function emitBlob(blob, fileName, reason) {
    if (!blob || blob.size < 100 || blob.size > 30 * 1024 * 1024) return
    const contentType = blob.type || 'application/octet-stream'
    if (!isArmed() && !isSpreadsheet(contentType, fileName)) return
    const captureKey = `${blob.size}:${contentType}:${fileName}`
    if (captureKey === lastCaptureKey) return
    lastCaptureKey = captureKey
    const reader = new FileReader()
    reader.onload = () => {
      window.postMessage({
        source: MESSAGE_SOURCE,
        type: 'EXPORT_CAPTURED',
        payload: {
          dataUrl: reader.result,
          fileName: normalizeFileName(fileName, contentType),
          contentType,
          size: blob.size,
          reason
        }
      }, '*')
    }
    reader.readAsDataURL(blob)
  }

  window.addEventListener('message', (event) => {
    if (event.source !== window) return
    if (event.data?.source !== 'crm-channel-collector-content') return
    if (event.data?.type === 'ARM_EXPORT_CAPTURE') {
      armedUntil = Date.now() + Number(event.data.durationMs || 120000)
    }
  })

  const originalCreateObjectURL = URL.createObjectURL.bind(URL)
  URL.createObjectURL = function createObjectURL(value) {
    const url = originalCreateObjectURL(value)
    if (value instanceof Blob) {
      emitBlob(value, '', 'blob')
    }
    return url
  }

  const originalFetch = window.fetch.bind(window)
  window.fetch = async function collectorFetch(...args) {
    const response = await originalFetch(...args)
    try {
      const url = requestUrl(args[0]) || response.url
      const contentType = response.headers.get('content-type') || ''
      const disposition = response.headers.get('content-disposition') || ''
      const fileName = fileNameFromDisposition(disposition)
      if (isSpreadsheet(contentType, fileName)
        || (isArmed() && (disposition || looksLikeExportUrl(url)))) {
        response.clone().blob().then((blob) => emitBlob(blob, fileName, 'fetch')).catch(() => {})
      }
    } catch {
      // 捕获失败时不影响企微页面自己的请求
    }
    return response
  }

  const originalOpen = XMLHttpRequest.prototype.open
  const originalSend = XMLHttpRequest.prototype.send
  XMLHttpRequest.prototype.open = function collectorOpen(method, url, ...args) {
    this.__crmCollectorUrl = String(url || '')
    return originalOpen.call(this, method, url, ...args)
  }
  XMLHttpRequest.prototype.send = function collectorSend(...args) {
    this.addEventListener('load', () => {
      try {
        const contentType = this.getResponseHeader('content-type') || ''
        const disposition = this.getResponseHeader('content-disposition') || ''
        const fileName = fileNameFromDisposition(disposition)
        const responseIsBinary = this.responseType === 'blob' || this.responseType === 'arraybuffer'
        if (!isSpreadsheet(contentType, fileName)
          && !(isArmed()
            && (disposition || looksLikeExportUrl(this.__crmCollectorUrl) || responseIsBinary))) return
        if (this.response instanceof Blob) {
          emitBlob(this.response, fileName, 'xhr')
        } else if (this.response instanceof ArrayBuffer) {
          emitBlob(new Blob([this.response], { type: contentType }), fileName, 'xhr')
        }
      } catch {
        // 捕获失败时不影响企微页面自己的请求
      }
    }, { once: true })
    return originalSend.apply(this, args)
  }

  document.addEventListener('click', (event) => {
    if (!isArmed() || !(event.target instanceof Element)) return
    const anchor = event.target.closest('a[href]')
    if (!anchor || !looksLikeExportUrl(anchor.href) || anchor.href.startsWith('blob:')) return
    originalFetch(anchor.href, { credentials: 'include' })
      .then((response) => response.blob())
      .then((blob) => emitBlob(blob, anchor.download || '', '下载链接'))
      .catch(() => {})
  }, true)
})()
