const sourceList = document.getElementById('sourceList')
const messageBox = document.getElementById('message')
let sources = []

document.getElementById('saveButton').addEventListener('click', save)
document.getElementById('testButton').addEventListener('click', testCrm)
document.getElementById('addSourceButton').addEventListener('click', () => {
  sources.push({ id: crypto.randomUUID(), name: '', url: '', enabled: true })
  renderSources()
})
document.getElementById('toggleToken').addEventListener('click', toggleToken)

load()

async function load() {
  const dashboard = await chrome.runtime.sendMessage({ type: 'GET_DASHBOARD' })
  if (!dashboard?.success) {
    showMessage(dashboard?.message || '配置读取失败', true)
    return
  }
  const config = dashboard.config
  document.getElementById('crmBaseUrl').value = config.crmBaseUrl || ''
  document.getElementById('uploadPath').value = config.uploadPath || ''
  document.getElementById('token').value = config.token || ''
  document.getElementById('enabled').checked = Boolean(config.enabled)
  document.getElementById('scheduleTime1').value = config.scheduleTimes?.[0] || '10:00'
  document.getElementById('scheduleTime2').value = config.scheduleTimes?.[1] || '18:00'
  sources = (config.sources || []).map((item) => ({ ...item }))
  renderSources()
}

function renderSources() {
  sourceList.innerHTML = ''
  sources.forEach((source, index) => {
    const row = document.createElement('div')
    row.className = 'source-setting-item'
    row.innerHTML = `
      <label class="switch-row compact">
        <input type="checkbox" data-field="enabled" ${source.enabled ? 'checked' : ''}>
        <span>启用</span>
      </label>
      <label class="field">
        <span>名称</span>
        <input data-field="name" value="${escapeAttribute(source.name)}" placeholder="表格名称">
      </label>
      <label class="field source-url-field">
        <span>智能表格链接</span>
        <input data-field="url" value="${escapeAttribute(source.url)}" placeholder="https://doc.weixin.qq.com/smartsheet/...">
      </label>
      <button type="button" class="danger-text" data-action="remove">删除</button>
    `
    row.querySelectorAll('[data-field]').forEach((input) => {
      input.addEventListener('input', () => {
        const field = input.dataset.field
        sources[index][field] = input.type === 'checkbox' ? input.checked : input.value
      })
      input.addEventListener('change', () => {
        const field = input.dataset.field
        sources[index][field] = input.type === 'checkbox' ? input.checked : input.value
      })
    })
    row.querySelector('[data-action="remove"]').addEventListener('click', () => {
      sources.splice(index, 1)
      renderSources()
    })
    sourceList.appendChild(row)
  })
}

async function save() {
  setBusy(true)
  try {
    const config = collectConfig()
    await ensureOriginPermission(config.crmBaseUrl)
    const result = await chrome.runtime.sendMessage({ type: 'SAVE_CONFIG', config })
    if (!result?.success) throw new Error(result?.message || '保存失败')
    showMessage(result.message)
  } catch (error) {
    showMessage(error.message || String(error), true)
  } finally {
    setBusy(false)
  }
}

async function testCrm() {
  setBusy(true)
  try {
    const config = collectConfig()
    await ensureOriginPermission(config.crmBaseUrl)
    await chrome.runtime.sendMessage({ type: 'SAVE_CONFIG', config })
    const result = await chrome.runtime.sendMessage({ type: 'TEST_CRM' })
    if (!result?.success) throw new Error(result?.message || '连接失败')
    showMessage(result.message)
  } catch (error) {
    showMessage(error.message || String(error), true)
  } finally {
    setBusy(false)
  }
}

function collectConfig() {
  const crmBaseUrl = document.getElementById('crmBaseUrl').value.trim().replace(/\/$/, '')
  if (!/^https?:\/\//i.test(crmBaseUrl)) throw new Error('CRM地址必须以http://或https://开头')
  const enabledSources = sources.filter((item) => item.enabled)
  enabledSources.forEach((item) => {
    if (!/^https:\/\/doc\.weixin\.qq\.com\/smartsheet\//i.test(item.url || '')) {
      throw new Error(`表格“${item.name || '未命名'}”链接格式不正确`)
    }
  })
  return {
    crmBaseUrl,
    uploadPath: document.getElementById('uploadPath').value.trim() || '/api/channel/source/import-excel',
    token: document.getElementById('token').value.trim(),
    enabled: document.getElementById('enabled').checked,
    scheduleTimes: [
      document.getElementById('scheduleTime1').value || '10:00',
      document.getElementById('scheduleTime2').value || '18:00'
    ],
    sources: sources.map((item) => ({
      id: item.id || crypto.randomUUID(),
      name: String(item.name || '').trim() || '企微智能表格',
      url: String(item.url || '').trim(),
      enabled: Boolean(item.enabled)
    }))
  }
}

async function ensureOriginPermission(origin) {
  if (!origin) return
  const url = new URL(origin)
  const pattern = `${url.protocol}//${url.hostname}/*`
  const hasPermission = await chrome.permissions.contains({ origins: [pattern] })
  if (hasPermission) return
  const granted = await chrome.permissions.request({ origins: [pattern] })
  if (!granted) throw new Error('未授予访问CRM地址的权限')
}

function toggleToken() {
  const input = document.getElementById('token')
  const button = document.getElementById('toggleToken')
  const visible = input.type === 'text'
  input.type = visible ? 'password' : 'text'
  button.textContent = visible ? '显示' : '隐藏'
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

function escapeAttribute(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
