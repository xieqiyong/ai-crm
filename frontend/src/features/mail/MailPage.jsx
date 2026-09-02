import { useEffect, useMemo, useRef, useState } from 'react'
import {
  FileText,
  Mail,
  Paperclip,
  RefreshCw,
  Send,
  Settings2,
  Trash2,
} from 'lucide-react'
import { api } from '../../api'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Drawer,
  Field,
  PageHeader,
  RichTextEditor,
  SecretInput,
  Select,
  useConfirmDialog,
} from '../../components'

const emptyPage = {
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: [],
}

const emptyAccount = {
  host: '',
  port: 465,
  username: '',
  password: '',
  fromAddress: '',
  fromName: '',
  sslEnabled: true,
  starttlsEnabled: false,
  enabled: true,
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

export function MailPage({ can, notify }) {
  const canSend = can('crm:mail:send')
  const canView = can('crm:mail:view')
  const canConfig = can('crm:mail:config')
  const canDelete = can('crm:mail:delete')
  const { confirm, dialogProps } = useConfirmDialog()
  const fileRef = useRef(null)
  const [customers, setCustomers] = useState([])
  const [form, setForm] = useState({
    customerId: '',
    recipientEmail: '',
    subject: '',
    bodyHtml: '',
  })
  const [files, setFiles] = useState([])
  const [logs, setLogs] = useState(emptyPage)
  const [logQuery, setLogQuery] = useState({ pageNo: 1, pageSize: 5 })
  const [loading, setLoading] = useState(false)
  const [sending, setSending] = useState(false)
  const [configOpen, setConfigOpen] = useState(false)

  const customerOptions = useMemo(() => customers.map((item) => ({
    value: item.id,
    label: `${item.name}${item.contactName ? ` · ${item.contactName}` : ''}`,
    description: item.contactEmail,
  })), [customers])

  const recipientOptions = useMemo(() => ([
    {
      value: '',
      label: '手动填写邮箱',
      description: '不关联客户，直接填写收件邮箱',
    },
    ...customerOptions,
  ]), [customerOptions])

  const selectedCustomer = customers.find((item) => String(item.id) === String(form.customerId))

  const loadLogs = async (nextQuery = logQuery) => {
    if (!canView) {
      setLogs(emptyPage)
      return
    }
    setLoading(true)
    try {
      const data = await api.mail.pageLogs(nextQuery)
      setLogs(data || emptyPage)
      setLogQuery(nextQuery)
    } catch (err) {
      notify(err.message || '邮件记录加载失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadLogs(logQuery)
    if (canSend) {
      api.mail.customerOptions()
        .then((data) => setCustomers(data || []))
        .catch((err) => notify(err.message || '客户邮箱列表加载失败', 'info'))
    }
  }, [canSend, canView])

  const chooseFiles = (event) => {
    const nextFiles = Array.from(event.target.files || [])
    event.target.value = ''
    if (files.length + nextFiles.length > 5) {
      notify('单封邮件最多上传5个附件', 'info')
      return
    }
    const totalBytes = [...files, ...nextFiles].reduce((sum, file) => sum + file.size, 0)
    if (totalBytes > 10 * 1024 * 1024) {
      notify('附件总大小不能超过10MB', 'info')
      return
    }
    setFiles([...files, ...nextFiles])
  }

  const send = async () => {
    if (!form.recipientEmail.trim()) {
      notify('请选择客户或填写收件邮箱', 'info')
      return
    }
    if (!form.subject.trim() || !form.bodyHtml.replace(/<[^>]*>/g, '').trim()) {
      notify('请填写邮件主题和正文', 'info')
      return
    }
    setSending(true)
    try {
      await api.mail.send(form, files)
      notify('邮件发送成功')
      setForm({
        customerId: '',
        recipientEmail: '',
        subject: '',
        bodyHtml: '',
      })
      setFiles([])
      await loadLogs({ ...logQuery, pageNo: 1 })
    } catch (err) {
      notify(err.message || '邮件发送失败', 'info')
    } finally {
      setSending(false)
    }
  }

  const deleteLog = async (row) => {
    const confirmed = await confirm({
      title: '删除邮件发送记录？',
      description: '删除后该记录不会继续显示，但已经发出的邮件不会被撤回。',
      target: row.subject,
      confirmText: '确认删除',
    })
    if (!confirmed) return
    try {
      await api.mail.deleteLog(row.id)
      notify('邮件发送记录已删除')
      const nextPageNo = (logs.records || []).length === 1 && logQuery.pageNo > 1
        ? logQuery.pageNo - 1
        : logQuery.pageNo
      await loadLogs({ ...logQuery, pageNo: nextPageNo })
    } catch (err) {
      notify(err.message || '邮件发送记录删除失败', 'info')
    }
  }

  const currentPage = logs.pageNo || logQuery.pageNo
  const totalPages = Math.max(1, Math.ceil((logs.total || 0) / logQuery.pageSize))

  return (
    <div className="page mail-page">
      <PageHeader
        title="客户邮件"
        description="从真实客户资料选择收件人，发送邮件和附件，并保留发送结果"
        actions={(
          <>
            <Button variant="secondary" icon={RefreshCw} onClick={() => loadLogs(logQuery)}>刷新记录</Button>
            {canConfig && <Button variant="secondary" icon={Settings2} onClick={() => setConfigOpen(true)}>发件配置</Button>}
          </>
        )}
      />

      <div className="mail-layout">
        <Card className="mail-compose-card">
          <div className="mail-section-title">
            <span><Mail size={20} /></span>
            <div><h2>写邮件</h2><p>可以选择系统客户自动带出邮箱，也可以直接填写收件邮箱。</p></div>
          </div>
          <Field label="收件客户" hint="可以选择系统客户，也可以选择手动填写邮箱">
            <Select
              searchable
              value={form.customerId}
              options={recipientOptions}
              placeholder="选择客户或手动填写"
              onChange={(customerId) => {
                const customer = customers.find((item) => String(item.id) === String(customerId))
                setForm({
                  ...form,
                  customerId,
                  recipientEmail: customer?.contactEmail || '',
                })
              }}
            />
          </Field>
          <Field
            label="收件邮箱"
            required
            hint={selectedCustomer ? `已关联客户：${selectedCustomer.name}，邮箱仍可修改` : '直接填写邮箱时不会强制关联客户'}
          >
            <input
              type="email"
              value={form.recipientEmail}
              placeholder="name@example.com"
              onChange={(event) => setForm({ ...form, recipientEmail: event.target.value })}
            />
          </Field>
          <Field label="邮件主题" required hint="最多256个字符">
            <input
              maxLength={256}
              value={form.subject}
              placeholder="请输入清晰的邮件主题"
              onChange={(event) => setForm({ ...form, subject: event.target.value })}
            />
          </Field>
          <Field label="邮件正文" required as="div">
            <RichTextEditor
              value={form.bodyHtml}
              notify={notify}
              placeholder="输入邮件正文，可使用标题、列表、链接和图片"
              onChange={(bodyHtml) => setForm({ ...form, bodyHtml })}
            />
          </Field>
          <div className="mail-attachments">
            <div>
              <Button variant="secondary" icon={Paperclip} onClick={() => fileRef.current?.click()}>
                添加附件
              </Button>
              <small>最多5个，合计不超过10MB</small>
              <input ref={fileRef} hidden type="file" multiple onChange={chooseFiles} />
            </div>
            {files.map((file, index) => (
              <div className="mail-attachment-item" key={`${file.name}-${file.size}-${index}`}>
                <FileText size={16} />
                <span>{file.name}</span>
                <small>{Math.max(1, Math.ceil(file.size / 1024))} KB</small>
                <button type="button" onClick={() => setFiles(files.filter((_, itemIndex) => itemIndex !== index))}>
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
          </div>
          <div className="mail-compose-actions">
            <Button icon={Send} disabled={!canSend || sending} onClick={send}>
              {sending ? '正在发送…' : '发送邮件'}
            </Button>
          </div>
        </Card>

        <Card className="mail-log-card">
          <div className="mail-section-title">
            <span><FileText size={20} /></span>
            <div><h2>最近发送</h2><p>共 {logs.total || 0} 条，失败原因可用于排查SMTP配置。</p></div>
          </div>
          <div className="mail-log-list">
            {(logs.records || []).map((row) => (
              <article key={row.id}>
                <div>
                  <b>{row.subject}</b>
                  <span>{row.customerName || '未关联客户'} · {row.recipientEmail}</span>
                  <small>{formatDateTime(row.sentAt || row.createdAt)}</small>
                </div>
                <Badge tone={row.status === 'SENT' ? 'success' : row.status === 'FAILED' ? 'danger' : 'neutral'}>
                  {row.status === 'SENT' ? '已发送' : row.status === 'FAILED' ? '发送失败' : '发送中'}
                </Badge>
                {canDelete && (
                  <button type="button" className="mail-log-delete" onClick={() => deleteLog(row)}>
                    <Trash2 size={14} />
                    删除
                  </button>
                )}
                {row.errorMessage && <p>{row.errorMessage}</p>}
              </article>
            ))}
            {!loading && !(logs.records || []).length && (
              <div className="mail-empty"><Mail size={24} /><b>暂无发送记录</b><span>发送第一封客户邮件后会展示在这里</span></div>
            )}
            {loading && <div className="mail-empty"><b>正在加载邮件记录…</b></div>}
          </div>
          {(logs.total || 0) > 0 && (
            <div className="mail-log-pagination">
              <span>第 {currentPage} / {totalPages} 页</span>
              <div>
                <button
                  type="button"
                  disabled={currentPage <= 1 || loading}
                  onClick={() => loadLogs({ ...logQuery, pageNo: currentPage - 1 })}
                >
                  上一页
                </button>
                <button
                  type="button"
                  disabled={currentPage >= totalPages || loading}
                  onClick={() => loadLogs({ ...logQuery, pageNo: currentPage + 1 })}
                >
                  下一页
                </button>
              </div>
            </div>
          )}
        </Card>
      </div>

      <MailAccountDrawer
        open={configOpen}
        notify={notify}
        onClose={() => setConfigOpen(false)}
      />
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}

function MailAccountDrawer({ open, notify, onClose }) {
  const [form, setForm] = useState(emptyAccount)
  const [passwordConfigured, setPasswordConfigured] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    api.mail.account()
      .then((data) => {
        setPasswordConfigured(Boolean(data?.passwordConfigured))
        setForm({ ...emptyAccount, ...(data || {}), password: '' })
      })
      .catch((err) => notify(err.message || '发件配置加载失败', 'info'))
  }, [open])

  const update = (key, value) => setForm({ ...form, [key]: value })

  const save = async () => {
    setSaving(true)
    try {
      const data = await api.mail.saveAccount({
        ...form,
        port: Number(form.port),
        password: form.password || undefined,
      })
      setPasswordConfigured(Boolean(data?.passwordConfigured))
      setForm({ ...form, password: '' })
      notify('发件配置已保存')
      onClose()
    } catch (err) {
      notify(err.message || '发件配置保存失败', 'info')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Drawer open={open} title="SMTP 发件配置" onClose={onClose}>
      <div className="mail-account-form">
        <div className="settings-security-note">
          配置按当前租户隔离。SMTP密码或授权码不会返回前端，留空表示保留原值。
        </div>
        <Field label="SMTP服务器" required><input value={form.host} placeholder="smtp.example.com" onChange={(event) => update('host', event.target.value)} /></Field>
        <Field label="SMTP端口" required><input type="number" min="1" max="65535" value={form.port} onChange={(event) => update('port', event.target.value)} /></Field>
        <Field label="SMTP账号" required><input value={form.username} onChange={(event) => update('username', event.target.value)} /></Field>
        <Field label="SMTP密码 / 授权码" required={!passwordConfigured} hint={passwordConfigured ? '已配置，留空表示不修改' : '首次配置必须填写'}>
          <SecretInput value={form.password} onChange={(event) => update('password', event.target.value)} />
        </Field>
        <Field label="发件邮箱" required><input type="email" value={form.fromAddress} onChange={(event) => update('fromAddress', event.target.value)} /></Field>
        <Field label="发件人名称"><input value={form.fromName || ''} onChange={(event) => update('fromName', event.target.value)} /></Field>
        <label className="settings-check"><input type="checkbox" checked={Boolean(form.sslEnabled)} onChange={(event) => update('sslEnabled', event.target.checked)} />启用 SSL</label>
        <label className="settings-check"><input type="checkbox" checked={Boolean(form.starttlsEnabled)} onChange={(event) => update('starttlsEnabled', event.target.checked)} />启用 STARTTLS</label>
        <label className="settings-check"><input type="checkbox" checked={Boolean(form.enabled)} onChange={(event) => update('enabled', event.target.checked)} />启用当前发件账号</label>
        <div className="drawer-actions">
          <Button variant="secondary" onClick={onClose}>取消</Button>
          <Button onClick={save} disabled={saving}>{saving ? '保存中…' : '保存配置'}</Button>
        </div>
      </div>
    </Drawer>
  )
}
