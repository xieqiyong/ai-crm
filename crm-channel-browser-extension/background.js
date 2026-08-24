const DEFAULT_CONFIG = {
  enabled: false,
  crmBaseUrl: 'http://localhost:5173',
  uploadPath: '/api/channel/source/import-excel',
  token: '',
  scheduleTimes: ['10:00', '18:00'],
  sources: [
    {
      id: 'wecom-sheet-tl1irP',
      name: '企微智能表格-tl1irP',
      enabled: true,
      url: 'https://doc.weixin.qq.com/smartsheet/s3_Ac8AUgacAJUCNWE4TzABESf67UOKI?tab=tl1irP&viewId=fv1'
    },
    {
      id: 'wecom-sheet-tl3VP4',
      name: '企微智能表格-tl3VP4',
      enabled: true,
      url: 'https://doc.weixin.qq.com/smartsheet/s3_AZYAywavAPICNOjOR4IcSTdOgr8S2?tab=tl3VP4&viewId=fv1'
    }
  ]
}

const runtime = {
  running: false,
  queue: [],
  current: null,
  currentJobId: null,
  currentTabId: null,
  triggerStarted: false,
  temporaryTabIds: new Set(),
  downloadSources: new Map(),
  uploadingHashes: new Set()
}

let runtimeRestored = false

chrome.runtime.onInstalled.addListener(() => initialize())
chrome.runtime.onStartup.addListener(() => initialize())

initialize()

async function initialize() {
  const stored = await chrome.storage.local.get(['config'])
  if (!stored.config) {
    await chrome.storage.local.set({
      config: DEFAULT_CONFIG,
      lastRuns: [],
      lastHashes: {},
      runtimeStatus: idleStatus()
    })
  }
  await restoreRuntime()
  await chrome.alarms.clear('crm-channel-current-timeout')
  await rebuildSchedule()
}

function idleStatus() {
  return {
    running: false,
    stage: 'IDLE',
    message: '等待同步',
    updatedAt: new Date().toISOString()
  }
}

chrome.alarms.onAlarm.addListener((alarm) => {
  handleAlarm(alarm).catch((error) => recordError('定时任务处理失败', error))
})

chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  handleTabUpdated(tabId, changeInfo).catch((error) => recordError('企微页面状态处理失败', error))
})

chrome.tabs.onRemoved.addListener((tabId) => {
  handleTabRemoved(tabId).catch((error) => recordError('企微页面关闭处理失败', error))
})

chrome.downloads.onCreated.addListener((item) => {
  handleDownloadCreated(item).catch((error) => recordError('下载任务识别失败', error))
})

chrome.downloads.onChanged.addListener(async (delta) => {
  if (delta.state?.current !== 'complete') return
  await restoreRuntime()
  const context = runtime.downloadSources.get(delta.id)
    || (runtime.current ? {
      source: runtime.current,
      jobId: runtime.currentJobId,
      createdAt: Date.now()
    } : null)
  if (!context) return
  runtime.downloadSources.delete(delta.id)
  const items = await chrome.downloads.search({ id: delta.id })
  const item = items[0]
  if (!item) return
  try {
    const response = await fetch(item.finalUrl || item.url, { credentials: 'include' })
    if (!response.ok) throw new Error(`下载响应异常：${response.status}`)
    const blob = await response.blob()
    const fileName = basename(item.filename) || context.source.name + '.xlsx'
    await uploadBlob(context.source, blob, fileName, '浏览器下载', context.jobId)
  } catch (error) {
    await recordRun({
      sourceName: context.source.name,
      sourceUrl: context.source.url,
      status: 'WARNING',
      message: `浏览器已完成下载，但插件无法再次读取文件：${error.message}`
    })
  }
})

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message, sender)
    .then((result) => sendResponse(result))
    .catch((error) => sendResponse({ success: false, message: error.message || String(error) }))
  return true
})

async function handleMessage(message, sender) {
  await restoreRuntime()
  switch (message?.type) {
    case 'GET_DASHBOARD':
      return getDashboard()
    case 'SAVE_CONFIG':
      return saveConfig(message.config)
    case 'SAVE_CRM_SESSION':
      return saveCrmSession(message.payload)
    case 'TEST_CRM':
      return testCrm()
    case 'RUN_ALL':
      await runAllSources('MANUAL')
      return { success: true, message: '已开始同步全部表格' }
    case 'RUN_CURRENT':
      return runCurrentTab(message.tabId, message.pageUrl)
    case 'ARM_CURRENT':
      return armCurrentTab(message.tabId, message.pageUrl)
    case 'PROBE_CURRENT':
      return sendToTab(message.tabId, { type: 'PAGE_PROBE' })
    case 'EXPORT_CAPTURED':
      return handleCapturedExport(
        message.payload,
        message.pageUrl || sender.tab?.url,
        message.jobId)
    case 'EXPORT_TRIGGER_RESULT':
      return handleExportTriggerResult(message.result, sender.tab?.id, message.jobId)
    case 'WECOM_PAGE_READY':
      return { success: true }
    default:
      return { success: false, message: '不支持的插件消息' }
  }
}

async function handleAlarm(alarm) {
  await restoreRuntime()
  if (alarm.name.startsWith('crm-channel-schedule-')) {
    await runAllSources('SCHEDULED')
  }
  if (alarm.name.startsWith('crm-channel-timeout-') && runtime.current) {
    const jobId = alarm.name.substring('crm-channel-timeout-'.length)
    if (runtime.currentJobId === jobId) {
      await failCurrent('等待企微导出文件超时，请检查登录状态或页面导出入口。', jobId)
    }
  }
}

async function handleTabUpdated(tabId, changeInfo) {
  await restoreRuntime()
  if (!runtime.running || runtime.currentTabId !== tabId || changeInfo.status !== 'complete') return
  const jobId = runtime.currentJobId
  setTimeout(() => triggerExport(tabId, jobId), 1800)
}

async function handleTabRemoved(tabId) {
  await restoreRuntime()
  runtime.temporaryTabIds.delete(tabId)
  await saveRuntime()
  if (runtime.running && runtime.currentTabId === tabId) {
    await failCurrent('企微智能表格页面已关闭。', runtime.currentJobId)
  }
}

async function handleDownloadCreated(item) {
  await restoreRuntime()
  if (!runtime.current) return
  const fileName = String(item.filename || item.url || '').toLowerCase()
  if (!/\.xlsx($|\?)|\.xls($|\?)|export|download/.test(fileName)) return
  runtime.downloadSources.set(item.id, {
    source: runtime.current,
    jobId: runtime.currentJobId,
    createdAt: Date.now()
  })
}

async function getDashboard() {
  const stored = await chrome.storage.local.get(['config', 'lastRuns', 'runtimeStatus'])
  return {
    success: true,
    config: mergeConfig(stored.config),
    lastRuns: stored.lastRuns || [],
    runtimeStatus: stored.runtimeStatus || idleStatus()
  }
}

async function saveConfig(input) {
  const current = (await chrome.storage.local.get(['config'])).config
  const config = mergeConfig({ ...current, ...input })
  await chrome.storage.local.set({ config })
  await rebuildSchedule()
  return { success: true, message: '配置已保存', config }
}

async function saveCrmSession(payload) {
  if (!payload?.token || !payload?.origin) {
    throw new Error('当前页面没有检测到CRM登录信息')
  }
  const stored = await chrome.storage.local.get(['config'])
  const config = mergeConfig({
    ...stored.config,
    crmBaseUrl: payload.origin,
    token: payload.token
  })
  await chrome.storage.local.set({ config })
  return { success: true, message: 'CRM登录信息绑定成功', crmBaseUrl: config.crmBaseUrl }
}

async function testCrm() {
  const config = await getConfig()
  validateCrmConfig(config)
  const response = await fetch(joinUrl(config.crmBaseUrl, '/api/channel/source/list'), {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${config.token}`,
      'Content-Type': 'application/json'
    },
    body: '{}'
  })
  const result = await parseApiResponse(response)
  return {
    success: true,
    message: `CRM连接成功，读取到${Array.isArray(result) ? result.length : 0}个渠道来源`
  }
}

async function runCurrentTab(tabId, pageUrl) {
  if (!isWecomSheetUrl(pageUrl)) {
    throw new Error('当前页面不是企微智能表格')
  }
  if (runtime.running) await resetRuntime()
  const config = await getConfig()
  const source = findSource(config.sources, pageUrl) || {
    id: sourceKey(pageUrl),
    name: documentName(pageUrl),
    enabled: true,
    url: pageUrl
  }
  runtime.running = true
  runtime.queue = []
  runtime.current = source
  runtime.currentJobId = crypto.randomUUID()
  runtime.currentTabId = tabId
  runtime.triggerStarted = true
  await saveRuntime()
  await setRuntimeStatus('EXPORTING', `正在导出：${source.name}`, true)
  createCurrentTimeout(runtime.currentJobId)
  const result = await sendToTab(tabId, {
    type: 'START_EXPORT',
    jobId: runtime.currentJobId
  })
  if (!result?.success) {
    await setRuntimeStatus('WAITING_MANUAL', result?.message || '自动导出失败，请手动导出', true)
  }
  return { success: true, message: result?.message || '已开启导出捕获' }
}

async function armCurrentTab(tabId, pageUrl) {
  if (!isWecomSheetUrl(pageUrl)) {
    throw new Error('当前页面不是企微智能表格')
  }
  if (runtime.running) await resetRuntime()
  const config = await getConfig()
  const source = findSource(config.sources, pageUrl) || {
    id: sourceKey(pageUrl),
    name: documentName(pageUrl),
    enabled: true,
    url: pageUrl
  }
  runtime.running = true
  runtime.queue = []
  runtime.current = source
  runtime.currentJobId = crypto.randomUUID()
  runtime.currentTabId = tabId
  runtime.triggerStarted = true
  await saveRuntime()
  await setRuntimeStatus('WAITING_MANUAL', `等待手动导出：${source.name}`, true)
  createCurrentTimeout(runtime.currentJobId)
  return sendToTab(tabId, {
    type: 'ARM_CAPTURE',
    durationMs: 120000,
    jobId: runtime.currentJobId
  })
}

async function runAllSources(trigger) {
  if (runtime.running) {
    if (trigger === 'SCHEDULED') return
    await resetRuntime()
  }
  const config = await getConfig()
  if (!config.enabled && trigger === 'SCHEDULED') return
  runtime.queue = config.sources.filter((item) => item.enabled && isWecomSheetUrl(item.url))
  if (!runtime.queue.length) throw new Error('没有启用的企微智能表格')
  runtime.running = true
  await saveRuntime()
  await setRuntimeStatus('STARTING', `准备同步${runtime.queue.length}张表格`, true)
  await startNextSource()
}

async function startNextSource() {
  const source = runtime.queue.shift()
  if (!source) {
    runtime.running = false
    runtime.current = null
    runtime.currentJobId = null
    runtime.currentTabId = null
    runtime.triggerStarted = false
    await setRuntimeStatus('FINISHED', '本轮同步已完成', false)
    await saveRuntime()
    await setBadge('')
    return
  }
  runtime.current = source
  runtime.currentJobId = crypto.randomUUID()
  runtime.triggerStarted = false
  await setRuntimeStatus('OPENING', `正在打开：${source.name}`, true)
  const tab = await chrome.tabs.create({ url: source.url, active: false })
  runtime.currentTabId = tab.id
  runtime.temporaryTabIds.add(tab.id)
  await saveRuntime()
  createCurrentTimeout(runtime.currentJobId)
  if (tab.status === 'complete') {
    const jobId = runtime.currentJobId
    setTimeout(() => triggerExport(tab.id, jobId), 1800)
  }
}

async function triggerExport(tabId, jobId) {
  if (!runtime.current
    || runtime.currentTabId !== tabId
    || runtime.currentJobId !== jobId
    || runtime.triggerStarted) return
  runtime.triggerStarted = true
  await saveRuntime()
  await setRuntimeStatus('EXPORTING', `正在导出：${runtime.current.name}`, true)
  try {
    const result = await sendToTab(tabId, { type: 'START_EXPORT', jobId })
    if (!result?.success) {
      await failCurrent(result?.message || '没有找到企微导出入口', jobId)
    }
  } catch (error) {
    await failCurrent(error.message, jobId)
  }
}

async function handleExportTriggerResult(result, tabId, jobId) {
  if (!runtime.current
    || runtime.currentTabId !== tabId
    || runtime.currentJobId !== jobId) {
    return { success: true }
  }
  if (result?.success) {
    await setRuntimeStatus('WAITING_FILE', `等待导出文件：${runtime.current.name}`, true)
    return { success: true }
  }
  const message = result?.message || '没有找到企微导出入口'
  if (runtime.temporaryTabIds.has(tabId)) {
    await failCurrent(message, jobId)
  } else {
    await setRuntimeStatus('WAITING_MANUAL', message, true)
  }
  return { success: true }
}

async function handleCapturedExport(payload, pageUrl, jobId) {
  if (!payload?.dataUrl) throw new Error('没有捕获到导出文件内容')
  if (jobId && runtime.currentJobId && jobId !== runtime.currentJobId) {
    return { success: true, ignored: true, message: '已忽略过期导出结果' }
  }
  const config = await getConfig()
  const source = findSource(config.sources, pageUrl) || runtime.current || {
    id: sourceKey(pageUrl),
    name: documentName(pageUrl),
    enabled: true,
    url: pageUrl
  }
  const response = await fetch(payload.dataUrl)
  const blob = await response.blob()
  return uploadBlob(
    source,
    blob,
    payload.fileName || `${source.name}.xlsx`,
    payload.reason || '页面捕获',
    jobId)
}

async function uploadBlob(source, blob, fileName, captureMode, jobId) {
  const hash = await sha256(blob)
  if (runtime.uploadingHashes.has(hash)) {
    return { success: true, skipped: true, message: '相同文件正在同步' }
  }
  runtime.uploadingHashes.add(hash)
  try {
    const stored = await chrome.storage.local.get(['lastHashes'])
    const lastHashes = stored.lastHashes || {}
    if (lastHashes[source.id] === hash) {
      await recordRun({
        sourceName: source.name,
        sourceUrl: source.url,
        status: 'SKIPPED',
        message: '导出文件未发生变化，已跳过',
        fileName,
        fileSize: blob.size,
        captureMode
      })
      await finishCurrentIfMatched(source, jobId)
      return { success: true, skipped: true, message: '文件未变化' }
    }

    const config = await getConfig()
    if (!config.token || !config.crmBaseUrl) {
      await recordRun({
        sourceName: source.name,
        sourceUrl: source.url,
        status: 'CAPTURED',
        message: '文件捕获成功，尚未绑定CRM登录',
        fileName,
        fileSize: blob.size,
        captureMode
      })
      await setBadge('1', '#d48a00')
      await finishCurrentIfMatched(source, jobId)
      return { success: true, captured: true, message: '文件捕获成功，请绑定CRM后重试' }
    }

    await setRuntimeStatus('UPLOADING', `正在上传CRM：${source.name}`, true)
    const form = new FormData()
    form.append('sourceUrl', source.url)
    form.append('file', new File([blob], normalizeExcelFileName(fileName), {
      type: blob.type || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    }))
    const response = await fetch(joinUrl(config.crmBaseUrl, config.uploadPath), {
      method: 'POST',
      headers: { Authorization: `Bearer ${config.token}` },
      body: form
    })
    const result = await parseApiResponse(response)
    lastHashes[source.id] = hash
    await chrome.storage.local.set({ lastHashes })
    const summary = `读取${result.fetchedCount || 0}条，新增${result.createdCount || 0}条，更新${result.updatedCount || 0}条，跳过${result.skippedCount || 0}条`
    await recordRun({
      sourceName: source.name,
      sourceUrl: source.url,
      status: 'SUCCESS',
      message: summary,
      fileName,
      fileSize: blob.size,
      captureMode,
      result
    })
    await notify('企微渠道同步完成', `${source.name}：${summary}`)
    await finishCurrentIfMatched(source, jobId)
    return { success: true, message: summary, result }
  } catch (error) {
    await recordRun({
      sourceName: source.name,
      sourceUrl: source.url,
      status: 'FAILED',
      message: error.message || String(error),
      fileName,
      fileSize: blob.size,
      captureMode
    })
    await setBadge('!', '#c63f3f')
    await notify('企微渠道同步失败', `${source.name}：${error.message || error}`)
    if (runtime.current && sameSource(runtime.current, source)) {
      await finishCurrentIfMatched(source, jobId)
    }
    throw error
  } finally {
    runtime.uploadingHashes.delete(hash)
  }
}

async function finishCurrentIfMatched(source, jobId) {
  if (!runtime.current
    || !sameSource(runtime.current, source)
    || (jobId && runtime.currentJobId !== jobId)) return
  const completedJobId = runtime.currentJobId
  await clearCurrentTimeout(completedJobId)
  const tabId = runtime.currentTabId
  runtime.current = null
  runtime.currentJobId = null
  runtime.currentTabId = null
  runtime.triggerStarted = false
  const shouldCloseTab = tabId && runtime.temporaryTabIds.has(tabId)
  if (shouldCloseTab) runtime.temporaryTabIds.delete(tabId)
  await saveRuntime()
  if (runtime.queue.length) {
    await setRuntimeStatus('TRANSITIONING', '当前表格已完成，正在准备下一张表格', true)
    setTimeout(async () => {
      if (shouldCloseTab) await chrome.tabs.remove(tabId).catch(() => {})
      if (runtime.running && !runtime.current) await startNextSource()
    }, 800)
  } else {
    runtime.running = false
    await saveRuntime()
    await setRuntimeStatus('FINISHED', '本轮同步已完成', false)
    await setBadge('')
    if (shouldCloseTab) {
      setTimeout(() => chrome.tabs.remove(tabId).catch(() => {}), 800)
    }
  }
}

async function failCurrent(message, jobId) {
  if (jobId && runtime.currentJobId !== jobId) return
  const source = runtime.current
  if (source) {
    await recordRun({
      sourceName: source.name,
      sourceUrl: source.url,
      status: 'FAILED',
      message
    })
    await notify('企微渠道同步失败', `${source.name}：${message}`)
    await finishCurrentIfMatched(source, jobId)
  } else {
    runtime.running = false
    await saveRuntime()
    await setRuntimeStatus('FAILED', message, false)
  }
}

function createCurrentTimeout(jobId) {
  if (!jobId) return
  chrome.alarms.create(`crm-channel-timeout-${jobId}`, { delayInMinutes: 2 })
}

async function clearCurrentTimeout(jobId) {
  if (!jobId) return
  await chrome.alarms.clear(`crm-channel-timeout-${jobId}`)
}

async function resetRuntime() {
  const tabIds = Array.from(runtime.temporaryTabIds)
  const alarms = await chrome.alarms.getAll()
  await Promise.all(alarms
    .filter((alarm) => alarm.name === 'crm-channel-current-timeout'
      || alarm.name.startsWith('crm-channel-timeout-'))
    .map((alarm) => chrome.alarms.clear(alarm.name)))
  runtime.running = false
  runtime.queue = []
  runtime.current = null
  runtime.currentJobId = null
  runtime.currentTabId = null
  runtime.triggerStarted = false
  runtime.temporaryTabIds = new Set()
  runtime.downloadSources = new Map()
  await saveRuntime()
  await Promise.all(tabIds.map((tabId) => chrome.tabs.remove(tabId).catch(() => {})))
}

async function restoreRuntime() {
  if (runtimeRestored) return
  const stored = await chrome.storage.session.get(['jobState'])
  const state = stored.jobState
  if (state) {
    runtime.running = Boolean(state.running)
    runtime.queue = Array.isArray(state.queue) ? state.queue : []
    runtime.current = state.current || null
    runtime.currentJobId = state.currentJobId || null
    runtime.currentTabId = state.currentTabId || null
    runtime.triggerStarted = Boolean(state.triggerStarted)
    runtime.temporaryTabIds = new Set(state.temporaryTabIds || [])
  }
  runtimeRestored = true
}

async function saveRuntime() {
  await chrome.storage.session.set({
    jobState: {
      running: runtime.running,
      queue: runtime.queue,
      current: runtime.current,
      currentJobId: runtime.currentJobId,
      currentTabId: runtime.currentTabId,
      triggerStarted: runtime.triggerStarted,
      temporaryTabIds: Array.from(runtime.temporaryTabIds)
    }
  })
}

async function rebuildSchedule() {
  const alarms = await chrome.alarms.getAll()
  await Promise.all(alarms
    .filter((item) => item.name.startsWith('crm-channel-schedule-'))
    .map((item) => chrome.alarms.clear(item.name)))
  const config = await getConfig()
  if (!config.enabled) return
  config.scheduleTimes.forEach((time, index) => {
    chrome.alarms.create(`crm-channel-schedule-${index}`, {
      when: nextTime(time),
      periodInMinutes: 1440
    })
  })
}

function nextTime(value) {
  const parts = String(value || '').split(':')
  const hour = Number(parts[0] || 0)
  const minute = Number(parts[1] || 0)
  const date = new Date()
  date.setHours(hour, minute, 0, 0)
  if (date.getTime() <= Date.now()) date.setDate(date.getDate() + 1)
  return date.getTime()
}

async function getConfig() {
  const stored = await chrome.storage.local.get(['config'])
  return mergeConfig(stored.config)
}

function mergeConfig(input = {}) {
  return {
    ...DEFAULT_CONFIG,
    ...input,
    scheduleTimes: Array.isArray(input.scheduleTimes) ? input.scheduleTimes : DEFAULT_CONFIG.scheduleTimes,
    sources: Array.isArray(input.sources) ? input.sources : DEFAULT_CONFIG.sources
  }
}

async function setRuntimeStatus(stage, message, running) {
  const runtimeStatus = {
    running,
    stage,
    message,
    updatedAt: new Date().toISOString()
  }
  await chrome.storage.local.set({ runtimeStatus })
  return runtimeStatus
}

async function recordRun(item) {
  const stored = await chrome.storage.local.get(['lastRuns'])
  const lastRuns = [
    { id: crypto.randomUUID(), createdAt: new Date().toISOString(), ...item },
    ...(stored.lastRuns || [])
  ].slice(0, 30)
  await chrome.storage.local.set({ lastRuns })
}

async function recordError(message, error) {
  await recordRun({ status: 'FAILED', message: `${message}：${error.message || error}` })
  await setBadge('!', '#c63f3f')
}

async function parseApiResponse(response) {
  const text = await response.text()
  let result
  try {
    result = JSON.parse(text)
  } catch {
    throw new Error(`CRM响应格式不正确：${response.status}`)
  }
  if (response.status === 401) throw new Error('CRM登录已失效，请重新绑定当前CRM页面')
  if (!response.ok || !result?.success) throw new Error(result?.message || `CRM请求失败：${response.status}`)
  return result.data
}

function validateCrmConfig(config) {
  if (!config.crmBaseUrl || !config.token) {
    throw new Error('请先在已登录的CRM页面中绑定登录信息')
  }
}

function joinUrl(baseUrl, path) {
  return `${String(baseUrl || '').replace(/\/$/, '')}/${String(path || '').replace(/^\//, '')}`
}

function sendToTab(tabId, message) {
  if (!tabId) return Promise.reject(new Error('没有可操作的企微页面'))
  return chrome.tabs.sendMessage(tabId, message)
}

function findSource(sources, pageUrl) {
  return (sources || []).find((item) => sameSource(item, { url: pageUrl }))
}

function sameSource(first, second) {
  return sourceKey(first?.url) === sourceKey(second?.url)
}

function sourceKey(value) {
  try {
    const url = new URL(value)
    return `${url.pathname}?tab=${url.searchParams.get('tab') || ''}`
  } catch {
    return String(value || '')
  }
}

function documentName(value) {
  try {
    const url = new URL(value)
    return `企微智能表格-${url.searchParams.get('tab') || url.pathname.split('/').pop()}`
  } catch {
    return '企微智能表格'
  }
}

function isWecomSheetUrl(value) {
  try {
    const url = new URL(value)
    return url.hostname === 'doc.weixin.qq.com' && url.pathname.startsWith('/smartsheet/')
  } catch {
    return false
  }
}

function basename(value) {
  return String(value || '').split(/[\\/]/).pop()
}

function normalizeExcelFileName(value) {
  const name = basename(value) || '企微智能表格.xlsx'
  return /\.(xlsx|xls)$/i.test(name) ? name : `${name.replace(/\.[^.]+$/, '')}.xlsx`
}

async function sha256(blob) {
  const bytes = await blob.arrayBuffer()
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest)).map((item) => item.toString(16).padStart(2, '0')).join('')
}

async function notify(title, message) {
  try {
    await chrome.notifications.create({
      type: 'basic',
      iconUrl: 'icon.svg',
      title,
      message
    })
  } catch {
    // 通知失败不影响同步结果
  }
}

async function setBadge(text, color) {
  await chrome.action.setBadgeText({ text })
  if (color) await chrome.action.setBadgeBackgroundColor({ color })
}
