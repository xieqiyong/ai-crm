const runtimeStatus = document.getElementById('runtimeStatus')
const historyList = document.getElementById('historyList')
const messageBox = document.getElementById('message')

document.getElementById('bindCrmButton').addEventListener('click', bindCurrentCrm)
document.getElementById('runAllButton').addEventListener('click', () => runAction({ type: 'RUN_ALL' }))
document.getElementById('probeButton').addEventListener('click', () => withCurrentTab('PROBE_CURRENT'))
document.getElementById('armButton').addEventListener('click', () => withCurrentTab('ARM_CURRENT'))
document.getElementById('runCurrentButton').addEventListener('click', () => withCurrentTab('RUN_CURRENT'))
document.getElementById('optionsButton').addEventListener('click', () => chrome.runtime.openOptionsPage())

loadDashboard()

async function loadDashboard() {
  const dashboard = await chrome.runtime.sendMessage({ type: 'GET_DASHBOARD' })
  if (!dashboard?.success) {
    showMessage(dashboard?.message || '插件状态读取失败', true)
    return
  }
  renderStatus(dashboard.runtimeStatus)
  renderHistory(dashboard.lastRuns)
}

function renderStatus(status = {}) {
  runtimeStatus.classList.toggle('running', Boolean(status.running))
  runtimeStatus.classList.toggle('failed', status.stage === 'FAILED')
  runtimeStatus.querySelector('strong').textContent = status.message || '等待同步'
  runtimeStatus.querySelector('small').textContent = status.updatedAt
    ? formatTime(status.updatedAt)
    : '尚未运行'
}

function renderHistory(items = []) {
  historyList.innerHTML = ''
  if (!items.length) {
    historyList.innerHTML = '<div class="empty">暂无同步记录</div>'
    return
  }
  items.slice(0, 5).forEach((item) => {
    const row = document.createElement('div')
    row.className = `history-item ${String(item.status || '').toLowerCase()}`
    row.innerHTML = `
      <div class="history-main">
        <strong>${escapeHtml(item.sourceName || '同步任务')}</strong>
        <span>${escapeHtml(item.message || '')}</span>
      </div>
      <time>${formatTime(item.createdAt)}</time>
    `
    historyList.appendChild(row)
  })
}

async function bindCurrentCrm() {
  setBusy(true)
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
    if (!tab?.id) throw new Error('没有找到当前页面')
    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      world: 'MAIN',
      func: () => ({
        token: localStorage.getItem('crm.token') || '',
        origin: location.origin,
        title: document.title
      })
    })
    const payload = results?.[0]?.result
    if (!payload?.token) throw new Error('当前页面没有CRM登录凭证，请先打开并登录CRM')
    await ensureOriginPermission(payload.origin)
    const result = await chrome.runtime.sendMessage({ type: 'SAVE_CRM_SESSION', payload })
    if (!result?.success) throw new Error(result?.message || '绑定失败')
    showMessage(`${result.message}：${result.crmBaseUrl}`)
  } catch (error) {
    showMessage(error.message || String(error), true)
  } finally {
    setBusy(false)
  }
}

async function withCurrentTab(type) {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  return runAction({ type, tabId: tab?.id, pageUrl: tab?.url })
}

async function runAction(payload) {
  setBusy(true)
  try {
    const result = await chrome.runtime.sendMessage(payload)
    if (!result?.success) throw new Error(result?.message || '操作失败')
    showMessage(result.message || '操作成功')
    await loadDashboard()
  } catch (error) {
    showMessage(error.message || String(error), true)
  } finally {
    setBusy(false)
  }
}

async function ensureOriginPermission(origin) {
  const url = new URL(origin)
  const pattern = `${url.protocol}//${url.hostname}/*`
  const hasPermission = await chrome.permissions.contains({ origins: [pattern] })
  if (hasPermission) return
  const granted = await chrome.permissions.request({ origins: [pattern] })
  if (!granted) throw new Error('未授予访问CRM地址的权限')
}

function setBusy(busy) {
  document.querySelectorAll('button').forEach((button) => {
    button.disabled = busy
  })
}

function showMessage(message, error = false) {
  messageBox.textContent = message
  messageBox.className = `message visible${error ? ' error' : ''}`
}

function formatTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function escapeHtml(value) {
  const div = document.createElement('div')
  div.textContent = String(value || '')
  return div.innerHTML
}
