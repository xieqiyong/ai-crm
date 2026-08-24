(() => {
  const PAGE_MESSAGE_SOURCE = 'crm-channel-collector-page'
  const CONTENT_MESSAGE_SOURCE = 'crm-channel-collector-content'
  const CLICK_RECORD_LIMIT = 12
  let learningMode = false
  let clickTrace = []
  let traceSaveTimer = null
  let activeJobId = null

  function armCapture(durationMs = 120000) {
    window.postMessage({
      source: CONTENT_MESSAGE_SOURCE,
      type: 'ARM_EXPORT_CAPTURE',
      durationMs
    }, '*')
  }

  function sourceKey(value) {
    try {
      const url = new URL(value)
      return `${url.pathname}?tab=${url.searchParams.get('tab') || ''}`
    } catch {
      return String(value || '')
    }
  }

  function rootDocuments() {
    const roots = []
    const visited = new Set()

    function appendRoot(root) {
      if (!root || visited.has(root)) return
      visited.add(root)
      roots.push(root)
      Array.from(root.querySelectorAll('*')).forEach((element) => {
        if (element.shadowRoot) appendRoot(element.shadowRoot)
        if (element.tagName === 'IFRAME') {
          try {
            if (element.contentDocument) appendRoot(element.contentDocument)
          } catch {
            // 跨域内嵌页面由外层下载捕获逻辑继续处理
          }
        }
      })
    }

    appendRoot(document)
    return roots
  }

  function isVisible(element) {
    if (!element || element.nodeType !== Node.ELEMENT_NODE) return false
    const view = element.ownerDocument?.defaultView || window
    const style = view.getComputedStyle(element)
    if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) return false
    const rect = element.getBoundingClientRect()
    return rect.width > 0 && rect.height > 0
  }

  function elementText(element) {
    return [
      element.innerText,
      element.textContent,
      element.getAttribute('aria-label'),
      element.getAttribute('title'),
      element.getAttribute('data-tooltip'),
      element.getAttribute('data-tippy-content')
    ].filter(Boolean).join(' ').replace(/\s+/g, ' ').trim()
  }

  function elementSignal(element) {
    return [
      elementText(element),
      element.id,
      element.className,
      element.getAttribute('data-testid'),
      element.getAttribute('data-test-id'),
      element.getAttribute('data-action'),
      element.getAttribute('data-menu-id'),
      element.getAttribute('data-icon'),
      element.getAttribute('name')
    ].filter((value) => typeof value === 'string' && value).join(' ').replace(/\s+/g, ' ').trim()
  }

  function actionableElement(element) {
    let current = element
    for (let depth = 0; current && depth < 6; depth++) {
      const tagName = String(current.tagName || '').toLowerCase()
      const role = current.getAttribute?.('role')
      if (tagName === 'button'
        || tagName === 'a'
        || role === 'button'
        || role === 'menuitem'
        || current.hasAttribute?.('tabindex')) {
        return current
      }
      current = current.parentElement
    }
    return element
  }

  function clickableElements() {
    const selector = 'button,[role="button"],[role="menuitem"],a,[tabindex],div,span,svg,use'
    const candidates = []
    const unique = new Set()
    rootDocuments().forEach((root) => {
      Array.from(root.querySelectorAll(selector)).forEach((element) => {
        const action = actionableElement(element)
        if (!isVisible(action) || unique.has(action)) return
        const signal = elementSignal(action)
        if (!signal) return
        unique.add(action)
        candidates.push(action)
      })
    })
    return candidates
  }

  function findClickables(labels, exact = false) {
    const normalizedLabels = labels.map((label) => String(label).toLowerCase().replace(/\s+/g, ''))
    return clickableElements()
      .filter((element) => {
        const signal = elementSignal(element).toLowerCase().replace(/\s+/g, '')
        return normalizedLabels.some((label) => exact ? signal === label : signal.includes(label))
      })
      .sort((first, second) => {
        const firstRect = first.getBoundingClientRect()
        const secondRect = second.getBoundingClientRect()
        return firstRect.width * firstRect.height - secondRect.width * secondRect.height
      })
  }

  function findClickable(labels, exact = false) {
    return findClickables(labels, exact)[0] || null
  }

  function delay(milliseconds) {
    return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
  }

  function clickElement(element) {
    if (!element) return false
    element.scrollIntoView({ block: 'center', inline: 'center' })
    const view = element.ownerDocument?.defaultView || window
    const eventOptions = { bubbles: true, cancelable: true, view }
    element.dispatchEvent(new view.MouseEvent('pointerdown', eventOptions))
    element.dispatchEvent(new view.MouseEvent('mousedown', eventOptions))
    element.dispatchEvent(new view.MouseEvent('pointerup', eventOptions))
    element.dispatchEvent(new view.MouseEvent('mouseup', eventOptions))
    if (typeof element.click === 'function') {
      element.click()
    } else {
      element.dispatchEvent(new view.MouseEvent('click', eventOptions))
    }
    return true
  }

  function cssPath(element) {
    const parts = []
    let current = element
    while (current && current.nodeType === Node.ELEMENT_NODE && parts.length < 7) {
      let part = String(current.tagName || '').toLowerCase()
      if (!part) break
      if (current.id && !/\d{5,}/.test(current.id)) {
        part += `#${CSS.escape(current.id)}`
        parts.unshift(part)
        break
      }
      const parent = current.parentElement
      if (parent) {
        const siblings = Array.from(parent.children).filter((item) => item.tagName === current.tagName)
        if (siblings.length > 1) part += `:nth-of-type(${siblings.indexOf(current) + 1})`
      }
      parts.unshift(part)
      current = parent
    }
    return parts.join(' > ')
  }

  function describeElement(element) {
    const action = actionableElement(element)
    const text = elementText(action).slice(0, 80)
    return {
      text,
      ariaLabel: action.getAttribute('aria-label') || '',
      title: action.getAttribute('title') || '',
      testId: action.getAttribute('data-testid') || action.getAttribute('data-test-id') || '',
      action: action.getAttribute('data-action') || action.getAttribute('data-menu-id') || '',
      path: cssPath(action)
    }
  }

  function findLearnedElement(step) {
    const candidates = clickableElements()
    const attributeMatch = candidates.find((element) => {
      if (step.ariaLabel && element.getAttribute('aria-label') === step.ariaLabel) return true
      if (step.title && element.getAttribute('title') === step.title) return true
      if (step.testId
        && (element.getAttribute('data-testid') === step.testId
          || element.getAttribute('data-test-id') === step.testId)) return true
      return step.action
        && (element.getAttribute('data-action') === step.action
          || element.getAttribute('data-menu-id') === step.action)
    })
    if (attributeMatch) return attributeMatch
    if (step.text) {
      const textMatch = candidates.find((element) => elementText(element) === step.text)
      if (textMatch) return textMatch
    }
    if (!step.path) return null
    for (const root of rootDocuments()) {
      try {
        const pathMatch = root.querySelector(step.path)
        if (isVisible(pathMatch)) return actionableElement(pathMatch)
      } catch {
        // 路径失效后继续使用通用识别
      }
    }
    return null
  }

  async function loadLearnedSteps() {
    const stored = await chrome.storage.local.get(['learnedExportSteps'])
    const learnedExportSteps = stored.learnedExportSteps || {}
    const sharedSteps = Object.values(learnedExportSteps)
      .find((steps) => Array.isArray(steps) && steps.length > 0)
    return learnedExportSteps[sourceKey(location.href)]
      || learnedExportSteps.__default__
      || sharedSteps
      || []
  }

  async function persistLearnedSteps() {
    if (!learningMode || !clickTrace.length) return false
    const stored = await chrome.storage.local.get(['learnedExportSteps'])
    const learnedExportSteps = stored.learnedExportSteps || {}
    const steps = clickTrace.slice(-CLICK_RECORD_LIMIT)
    learnedExportSteps[sourceKey(location.href)] = steps
    learnedExportSteps.__default__ = steps
    await chrome.storage.local.set({ learnedExportSteps })
    return true
  }

  async function saveLearnedSteps() {
    const saved = await persistLearnedSteps()
    learningMode = false
    return saved
  }

  async function replayLearnedSteps(steps) {
    if (!Array.isArray(steps) || !steps.length) return false
    for (const step of steps) {
      const element = findLearnedElement(step)
      if (!element) return false
      clickElement(element)
      await delay(650)
    }
    return true
  }

  async function clickExportFromOpenedMenu() {
    const exportButton = findClickable([
      '导出为Excel', '导出Excel', '导出本地表格', '导出表格', '导出', '下载', '另存为'
    ])
    if (!exportButton) return false
    clickElement(exportButton)
    await delay(700)
    const excelButton = findClickable([
      'Microsoft Excel', 'Excel工作簿', '导出为Excel', 'Excel', 'XLSX', '本地表格', '导出文件'
    ])
    if (excelButton && excelButton !== exportButton) clickElement(excelButton)
    return true
  }

  async function tryKnownMenus() {
    const directExport = findClickable([
      '导出为Excel', '导出Excel', '导出本地表格', '导出表格', '另存为'
    ])
    if (directExport) {
      clickElement(directExport)
      await delay(700)
      const excelButton = findClickable(['Microsoft Excel', 'Excel工作簿', 'Excel', 'XLSX', '本地表格'])
      if (excelButton && excelButton !== directExport) clickElement(excelButton)
      return true
    }

    const menuButtons = findClickables([
      '更多', '文件', '菜单', '主菜单', 'more', 'menu', 'overflow', 'ellipsis', 'hamburger', 'toolbar-more'
    ])
    for (const menuButton of menuButtons.slice(0, 8)) {
      clickElement(menuButton)
      await delay(600)
      if (await clickExportFromOpenedMenu()) return true
    }
    return false
  }

  async function tryAutomaticExport() {
    learningMode = false
    armCapture()
    await delay(1200)

    const learnedSteps = await loadLearnedSteps()
    if (await replayLearnedSteps(learnedSteps)) {
      showToast('已按学习路径触发导出，正在等待文件。', 'success')
      return { success: true, message: '已按学习路径触发导出' }
    }

    if (await tryKnownMenus()) {
      showToast('已触发导出，正在等待文件。', 'success')
      return { success: true, message: '已触发导出' }
    }

    learningMode = true
    clickTrace = []
    showToast('未识别到导出按钮，请现在手动导出一次，插件会学习点击路径。', 'error')
    return {
      success: false,
      message: learnedSteps.length
        ? '已学习的导出路径失效，请重新手动导出一次'
        : '没有找到导出入口，请手动导出一次完成路径学习'
    }
  }

  function showToast(message, type = 'info') {
    const old = document.getElementById('crm-channel-collector-toast')
    if (old) old.remove()
    const toast = document.createElement('div')
    toast.id = 'crm-channel-collector-toast'
    toast.textContent = message
    toast.style.cssText = [
      'position:fixed',
      'top:24px',
      'left:50%',
      'transform:translateX(-50%)',
      'z-index:2147483647',
      'max-width:520px',
      'padding:12px 18px',
      'border-radius:10px',
      'font-size:14px',
      'line-height:1.5',
      'color:#fff',
      `background:${type === 'error' ? '#d14343' : type === 'success' ? '#21875b' : '#252a34'}`,
      'box-shadow:0 10px 30px rgba(0,0,0,.22)'
    ].join(';')
    document.documentElement.appendChild(toast)
    window.setTimeout(() => toast.remove(), 8000)
  }

  document.addEventListener('click', (event) => {
    if (!learningMode || !event.isTrusted || !(event.target instanceof Element)) return
    clickTrace.push(describeElement(event.target))
    if (clickTrace.length > CLICK_RECORD_LIMIT) clickTrace.shift()
    if (traceSaveTimer) window.clearTimeout(traceSaveTimer)
    traceSaveTimer = window.setTimeout(() => persistLearnedSteps().catch(() => {}), 100)
  }, true)

  window.addEventListener('message', (event) => {
    if (event.source !== window || event.data?.source !== PAGE_MESSAGE_SOURCE) return
    if (event.data?.type !== 'EXPORT_CAPTURED') return
    saveLearnedSteps().then((learned) => {
      chrome.runtime.sendMessage({
        type: 'EXPORT_CAPTURED',
        payload: event.data.payload,
        pageUrl: location.href,
        jobId: activeJobId
      }).catch(() => {})
      showToast(learned
        ? '导出文件已捕获，并已记住本次导出路径。'
        : '已捕获企微导出文件，正在同步到CRM。', 'success')
    })
  })

  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message?.type === 'ARM_CAPTURE') {
      activeJobId = message.jobId || null
      learningMode = true
      clickTrace = []
      armCapture(message.durationMs)
      showToast('学习模式已开启，请在2分钟内手动导出Excel。')
      sendResponse({ success: true, message: '请手动导出一次，插件会记住点击路径' })
      return
    }
    if (message?.type === 'START_EXPORT') {
      activeJobId = message.jobId || null
      sendResponse({ success: true, message: '正在识别并触发导出' })
      tryAutomaticExport()
        .then((result) => chrome.runtime.sendMessage({
          type: 'EXPORT_TRIGGER_RESULT',
          result,
          pageUrl: location.href,
          jobId: activeJobId
        }))
        .catch((error) => chrome.runtime.sendMessage({
          type: 'EXPORT_TRIGGER_RESULT',
          result: { success: false, message: error.message || String(error) },
          pageUrl: location.href,
          jobId: activeJobId
        }))
        .catch(() => {})
      return
    }
    if (message?.type === 'PAGE_PROBE') {
      Promise.all([loadLearnedSteps(), Promise.resolve(clickableElements())])
        .then(([steps, elements]) => {
          const labels = elements.map(elementSignal).filter(Boolean).slice(0, 300)
          sendResponse({
            success: true,
            url: location.href,
            title: document.title,
            hasExport: labels.some((text) => text.includes('导出')),
            hasMore: labels.some((text) => /更多|more|menu|ellipsis/i.test(text)),
            learned: steps.length > 0,
            matchedLabels: labels.filter((text) => /导出|更多|文件|Excel|more|menu|ellipsis/i.test(text)).slice(0, 30),
            message: steps.length
              ? `检测完成：已学习${steps.length}步导出路径`
              : `检测完成：${labels.some((text) => text.includes('导出')) ? '已找到导出入口' : '尚未学习导出路径'}`
          })
        })
        .catch((error) => sendResponse({ success: false, message: error.message || String(error) }))
      return true
    }
  })

  chrome.runtime.sendMessage({ type: 'WECOM_PAGE_READY', pageUrl: location.href }).catch(() => {})
})()
