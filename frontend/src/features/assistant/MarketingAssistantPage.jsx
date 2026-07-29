import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Bot, Check, Copy, FileText, Loader2, MessageSquarePlus,
  Paperclip, RefreshCw, Send, Settings2, Sparkles, Square, Trash2, X,
} from 'lucide-react'
import { Badge, Button, ConfirmDialog, EmptyPermission, MarkdownText, useConfirmDialog } from '../../components'
import { api } from '../../api'

function nextId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function agentScene(agent) {
  return agent?.sceneName || agent?.sceneCode || agent?.code || '通用场景'
}

function shortText(value, length = 26) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return '新会话'
  return text.length > length ? `${text.slice(0, length)}…` : text
}

function toMessages(rows) {
  return (rows || []).map((item) => ({
    id: `${item.runId || nextId()}-${item.role}-${item.createdAt || ''}`,
    role: item.role,
    content: item.content || '',
    status: item.status,
    createdAt: item.createdAt,
    attachments: item.attachments || [],
  }))
}

function updateLastAssistant(messages, updater) {
  const next = [...messages]
  for (let index = next.length - 1; index >= 0; index -= 1) {
    if (next[index].role === 'assistant') {
      next[index] = { ...next[index], ...(typeof updater === 'function' ? updater(next[index]) : updater) }
      return next
    }
  }
  return next
}

export function MarketingAssistantPage({
  navigate,
  notify,
  can,
}) {
  const [agents, setAgents] = useState([])
  const [activeAgentId, setActiveAgentId] = useState(null)
  const [conversations, setConversations] = useState([])
  const [activeConversationId, setActiveConversationId] = useState(null)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [attachments, setAttachments] = useState([])
  const [loading, setLoading] = useState(false)
  const [settling, setSettling] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState([])
  const [copiedId, setCopiedId] = useState(null)
  const { confirm, dialogProps } = useConfirmDialog()
  const fileInputRef = useRef(null)
  const scrollRef = useRef(null)
  const abortControllerRef = useRef(null)
  const activeRunRequestIdRef = useRef(null)
  const answerRenderTimerRef = useRef(null)
  const shouldStickToBottomRef = useRef(true)
  const runActive = loading || settling

  const activeAgent = useMemo(
    () => agents.find((item) => String(item.id) === String(activeAgentId)) || null,
    [agents, activeAgentId],
  )
  useEffect(() => {
    if (!can('crm:assistant:use')) return
    loadAgents()
  }, [])

  useEffect(() => {
    if (!activeAgentId) {
      setConversations([])
      return
    }
    loadConversations(activeAgentId)
  }, [activeAgentId])

  useEffect(() => {
    const element = scrollRef.current
    if (!element || !shouldStickToBottomRef.current) return undefined
    const frame = window.requestAnimationFrame(() => {
      if (shouldStickToBottomRef.current) {
        element.scrollTop = element.scrollHeight
      }
    })
    return () => window.cancelAnimationFrame(frame)
  }, [messages, progress])

  useEffect(() => () => {
    abortControllerRef.current?.abort()
    if (answerRenderTimerRef.current) {
      window.clearTimeout(answerRenderTimerRef.current)
    }
  }, [])

  if (!can('crm:assistant:use')) {
    return <EmptyPermission onBack={() => navigate('dashboard')} />
  }

  const loadAgents = async () => {
    try {
      const rows = await api.agent.assistantAgents()
      setAgents(rows || [])
      if (!activeAgentId && rows?.length) {
        setActiveAgentId(rows[0].id)
      }
    } catch (error) {
      notify(error.message || '智能体列表加载失败', 'info')
    }
  }

  const loadConversations = async (agentId = activeAgentId) => {
    if (!agentId) return
    try {
      const rows = await api.agent.assistantConversations({ agentId })
      setConversations(rows || [])
    } catch (error) {
      notify(error.message || '会话列表加载失败', 'info')
    }
  }

  const loadMessages = async (conversation) => {
    if (!conversation?.id) return
    setActiveConversationId(conversation.id)
    setProgress([])
    try {
      const rows = await api.agent.assistantMessages(conversation.id)
      setMessages(toMessages(rows))
    } catch (error) {
      notify(error.message || '会话消息加载失败', 'info')
    }
  }

  const startConversation = () => {
    setActiveConversationId(null)
    setMessages([])
    setProgress([])
    setAttachments([])
    setInput('')
  }

  const selectAgent = (agent) => {
    if (runActive) return
    setActiveAgentId(agent.id)
    setActiveConversationId(null)
    setMessages([])
    setProgress([])
    setAttachments([])
  }

  const deleteConversation = async (conversation) => {
    if (!conversation?.id || runActive) return
    const confirmed = await confirm({
      title: '删除会话',
      description: '删除后该会话不会再出现在历史列表中，审计和用量记录仍会保留。',
      target: conversation.title || '未命名会话',
      confirmText: '确认删除',
      tone: 'danger',
    })
    if (!confirmed) return
    try {
      await api.agent.assistantDeleteConversation(conversation.id)
      setConversations((values) => values.filter((item) => String(item.id) !== String(conversation.id)))
      if (String(activeConversationId) === String(conversation.id)) {
        startConversation()
      }
      notify('会话已删除', 'success')
    } catch (error) {
      notify(error.message || '会话删除失败', 'info')
    }
  }

  const stopGenerating = async () => {
    const requestId = activeRunRequestIdRef.current
    const controller = abortControllerRef.current
    if (!requestId || !controller) return
    try {
      const stopped = await api.agent.assistantStopRun(requestId)
      if (!stopped) {
        notify('本次回答已经结束', 'info')
        return
      }
      controller.abort()
      setProgress((values) => [...new Set([...values, '已终止本次回答'])].slice(-5))
    } catch (error) {
      notify(error.message || '终止回答失败', 'error')
    }
  }

  const uploadFiles = async (fileList) => {
    const files = Array.from(fileList || [])
    if (!files.length) return
    setUploading(true)
    try {
      const uploaded = []
      for (const file of files) {
        const response = await api.attachment.uploadFile(file)
        uploaded.push(response)
      }
      setAttachments((values) => [...values, ...uploaded])
      notify(`已上传 ${uploaded.length} 个附件`, 'success')
    } catch (error) {
      notify(error.message || '附件上传失败', 'info')
    } finally {
      setUploading(false)
    }
  }

  const removeAttachment = (storageKey) => {
    setAttachments((values) => values.filter((item) => item.storageKey !== storageKey))
  }

  const sendMessage = async () => {
    if (!activeAgent || runActive || uploading) return
    const text = input.trim() || (attachments.length ? '请阅读附件内容，并结合我的业务问题给出建议。' : '')
    if (!text) return
    const userMessage = {
      id: nextId(),
      role: 'user',
      content: text,
      attachments: [...attachments],
      createdAt: new Date().toISOString(),
    }
    const assistantMessage = {
      id: nextId(),
      role: 'assistant',
      content: '',
      streaming: true,
      createdAt: new Date().toISOString(),
    }
    setMessages((values) => [...values, userMessage, assistantMessage])
    setInput('')
    setAttachments([])
    setProgress(['智能体开始处理'])
    shouldStickToBottomRef.current = true
    setLoading(true)
    setSettling(false)
    let answer = ''
    let nextConversationId = activeConversationId
    const controller = new AbortController()
    const requestId = nextId()
    abortControllerRef.current = controller
    activeRunRequestIdRef.current = requestId
    const flushAnswer = (streaming) => {
      if (answerRenderTimerRef.current) {
        window.clearTimeout(answerRenderTimerRef.current)
        answerRenderTimerRef.current = null
      }
      setMessages((values) => updateLastAssistant(values, { content: answer, streaming }))
    }
    const scheduleAnswerRender = () => {
      if (answerRenderTimerRef.current) return
      answerRenderTimerRef.current = window.setTimeout(() => {
        answerRenderTimerRef.current = null
        setMessages((values) => updateLastAssistant(values, { content: answer, streaming: true }))
      }, 45)
    }
    try {
      await api.agent.assistantRunStream({
        requestId,
        agentId: activeAgent.id,
        conversationId: activeConversationId,
        message: text,
        attachments: userMessage.attachments,
      }, {
        onRuntimeEvent: (payload) => {
          const type = String(payload?.type || '').toUpperCase()
          const content = String(payload?.content || '')
          if (payload?.conversationId) {
            nextConversationId = payload.conversationId
          }
          if (type === 'ANSWER_DELTA') {
            answer += content
            scheduleAnswerRender()
            return
          }
          if (type === 'ANSWER_FINISHED') {
            if (content && !answer) {
              answer = content
            }
            flushAnswer(false)
            setLoading(false)
            setSettling(true)
            return
          }
          if (type === 'RUN_FINISHED') {
            const response = payload?.metadata?.response || {}
            if (response.conversationId) {
              nextConversationId = response.conversationId
              setActiveConversationId(response.conversationId)
            }
            if (response.reply && !answer) {
              answer = response.reply
            }
            flushAnswer(false)
            setLoading(false)
            setSettling(true)
            setProgress((values) => [...new Set([...values, '智能体回复完成'])].slice(-5))
            return
          }
          if (type === 'RUN_ERROR') {
            if (answerRenderTimerRef.current) {
              window.clearTimeout(answerRenderTimerRef.current)
              answerRenderTimerRef.current = null
            }
            setMessages((values) => updateLastAssistant(values, {
              content: content || '智能体运行失败',
              streaming: false,
              status: 'FAILED',
            }))
            setLoading(false)
            setSettling(true)
            setProgress((values) => [...new Set([...values, content || '智能体运行失败'])].slice(-5))
            return
          }
          if (content && ['RUN_STATUS_CHANGED', 'CONTEXT_LOADED', 'TOOL_CALL_STARTED', 'TOOL_RESULT_FINISHED'].includes(type)) {
            setProgress((values) => [...new Set([...values, content])].slice(-5))
          }
        },
      }, {
        signal: controller.signal,
      })
      if (nextConversationId) {
        setActiveConversationId(nextConversationId)
      }
      await loadConversations(activeAgent.id)
    } catch (error) {
      if (error.name === 'AbortError') {
        if (answerRenderTimerRef.current) {
          window.clearTimeout(answerRenderTimerRef.current)
          answerRenderTimerRef.current = null
        }
        setMessages((values) => updateLastAssistant(values, {
          content: answer || '本次回答已终止。',
          streaming: false,
          status: 'STOPPED',
        }))
        if (nextConversationId) {
          setActiveConversationId(nextConversationId)
          await loadConversations(activeAgent.id)
        }
        notify('本次回答已终止', 'info')
        return
      }
      if (answerRenderTimerRef.current) {
        window.clearTimeout(answerRenderTimerRef.current)
        answerRenderTimerRef.current = null
      }
      setMessages((values) => updateLastAssistant(values, {
        content: error.message || '智能体请求失败',
        streaming: false,
        status: 'FAILED',
      }))
      notify(error.message || '智能体请求失败', 'error')
    } finally {
      if (abortControllerRef.current === controller) {
        abortControllerRef.current = null
      }
      if (activeRunRequestIdRef.current === requestId) {
        activeRunRequestIdRef.current = null
      }
      setLoading(false)
      setSettling(false)
    }
  }

  const copyMessage = async (message) => {
    try {
      await navigator.clipboard.writeText(message.content || '')
      setCopiedId(message.id)
      window.setTimeout(() => setCopiedId(null), 1200)
    } catch (error) {
      notify('复制失败', 'info')
    }
  }

  return (
    <div className="page agent-assistant-page">
      <section className="agent-assistant-shell">
        <aside className="agent-assistant-sidebar">
          <div className="agent-assistant-side-head">
            <div>
              <span className="eyebrow">智能体工作台</span>
              <h1>AI 智能体助手</h1>
            </div>
            <button className="icon-button" onClick={loadAgents} aria-label="刷新智能体">
              <RefreshCw size={16} />
            </button>
          </div>
          <Button icon={MessageSquarePlus} onClick={startConversation} disabled={runActive} className="agent-new-chat">
            新起会话
          </Button>
          <div className="agent-assistant-section-title">可用智能体</div>
          <div className="agent-card-list">
            {agents.map((agent) => (
              <button
                key={agent.id}
                className={`agent-select-card ${String(activeAgentId) === String(agent.id) ? 'active' : ''}`}
                onClick={() => selectAgent(agent)}
              >
                <span><Bot size={17} /></span>
                <div>
                  <strong>{agent.name}</strong>
                  <small>{agentScene(agent)}</small>
                  <p>{agent.description || '暂无功能描述，请在智能体配置管理中补充。'}</p>
                  <em>{agent.modelName || '未配置模型'} · {agent.conversationCount || 0} 个会话</em>
                </div>
              </button>
            ))}
            {!agents.length && (
              <div className="agent-assistant-empty">
                <Bot size={24} />
                <b>暂无可用智能体</b>
                <small>请先到智能体配置管理中创建并启用智能体。</small>
              </div>
            )}
          </div>
          <div className="agent-assistant-section-title">历史会话</div>
          <div className="conversation-list">
            {conversations.map((conversation) => (
              <div
                key={conversation.id}
                className={`conversation-item ${String(activeConversationId) === String(conversation.id) ? 'active' : ''}`}
              >
                <button className="conversation-main" onClick={() => loadMessages(conversation)}>
                  <strong>{conversation.title || '未命名会话'}</strong>
                  <small>{formatTime(conversation.lastMessageAt || conversation.createdAt)}</small>
                </button>
                <span className="conversation-actions">
                  <button
                    className="danger"
                    title="删除会话"
                    disabled={runActive}
                    onClick={() => deleteConversation(conversation)}
                  >
                    <Trash2 size={13} />
                  </button>
                </span>
              </div>
            ))}
            {!conversations.length && (
              <div className="agent-assistant-empty compact">
                <b>还没有历史会话</b>
                <small>选择智能体后直接发送第一条消息。</small>
              </div>
            )}
          </div>
        </aside>

        <main className="agent-chat-main">
          <header className="agent-chat-head">
            <div>
              <span className="agent-avatar"><Sparkles size={18} /></span>
              <div>
                <strong>{activeAgent?.name || '请选择智能体'}</strong>
                <small>{activeAgent ? `${agentScene(activeAgent)} · ${activeAgent.modelName || '未配置模型'}` : '左侧选择一个智能体开始'}</small>
              </div>
            </div>
            <div className="agent-chat-actions">
              <Badge tone={loading ? 'warning' : activeAgent ? 'success' : 'neutral'}>
                {loading ? '正在回答' : activeAgent ? '可继续对话' : '未选择'}
              </Badge>
              {loading && (
                <button onClick={stopGenerating}>
                  <Square size={15} /> 终止回答
                </button>
              )}
              {can('crm:agent:manage') && (
                <button onClick={() => navigate('agent-config')}>
                  <Settings2 size={15} /> 配置管理
                </button>
              )}
            </div>
          </header>

          <div
            className="agent-chat-scroll"
            ref={scrollRef}
            onScroll={(event) => {
              const element = event.currentTarget
              shouldStickToBottomRef.current =
                element.scrollHeight - element.scrollTop - element.clientHeight < 96
            }}
          >
            {!messages.length ? (
              <div className="agent-chat-welcome">
                <div className="agent-welcome-mark">✳</div>
                <h2>{activeAgent ? `继续推进，${activeAgent.name}` : '选择一个智能体'}</h2>
                <p>上传资料、调用工具、结合知识库和业务数据，让智能体直接给出可执行的销售建议。</p>
              </div>
            ) : (
              messages.map((message, index) => {
                const assistant = message.role === 'assistant'
                const activeAssistant = assistant && index === messages.length - 1
                return (
                  <div className={`agent-chat-message ${message.role}`} key={message.id}>
                    {assistant && (
                      <span className="agent-message-avatar">
                        <Sparkles size={15} />
                      </span>
                    )}
                    <article className="agent-chat-bubble">
                      {assistant && (
                        <div className="agent-message-meta">
                          <span>{activeAgent?.name || '智能体'}</span>
                          <small>{message.streaming ? '正在生成' : formatTime(message.createdAt)}</small>
                        </div>
                      )}
                      {activeAssistant && progress.length > 0 && (
                        <details className="assistant-thoughts">
                          <summary>{message.streaming ? '正在处理' : `已完成 · ${progress.length} 步`}</summary>
                          <ol>
                            {progress.map((item) => <li key={item}>{item}</li>)}
                          </ol>
                        </details>
                      )}
                      {message.content
                        ? assistant
                          ? <MarkdownText value={message.content} variant="assistant" />
                          : <p className="agent-user-text">{message.content}</p>
                        : message.streaming ? <div className="agent-typing"><i /><i /><i /></div> : null}
                      {message.attachments?.length > 0 && (
                        <div className="agent-attachment-list">
                          {message.attachments.map((file) => (
                            <a key={file.storageKey || file.url} href={file.url} target="_blank" rel="noreferrer">
                              <FileText size={15} />
                              <span>{file.fileName}</span>
                              <small>{file.contentType || '文件'}</small>
                            </a>
                          ))}
                        </div>
                      )}
                      {assistant && message.content && (
                        <button className="agent-copy-button" onClick={() => copyMessage(message)} title="复制回答">
                          {copiedId === message.id ? <Check size={14} /> : <Copy size={14} />}
                        </button>
                      )}
                    </article>
                  </div>
                )
              })
            )}
          </div>

          <footer className="agent-composer-wrap">
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className="hidden-input"
              accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.csv,.txt,.md,.markdown,.html,.rtf,.odt"
              onChange={(event) => {
                uploadFiles(event.target.files)
                event.target.value = ''
              }}
            />
            {(attachments.length > 0 || uploading) && (
              <div className="agent-composer-files">
                {uploading && <span><Loader2 size={13} className="spin" /> 上传中…</span>}
                {attachments.map((file) => (
                  <span key={file.storageKey || file.url}>
                    <FileText size={13} />
                    {shortText(file.fileName, 18)}
                    <button onClick={() => removeAttachment(file.storageKey)}><X size={12} /></button>
                  </span>
                ))}
              </div>
            )}
            <div className="agent-composer">
              <button onClick={() => fileInputRef.current?.click()} disabled={runActive || uploading || !activeAgent} title="上传附件">
                <Paperclip size={17} />
              </button>
              <textarea
                value={input}
                rows={2}
                disabled={runActive || !activeAgent}
                placeholder={activeAgent ? `给 ${activeAgent.name} 发消息，可上传文件` : '请先选择一个智能体'}
                onChange={(event) => setInput(event.target.value)}
                onPaste={(event) => {
                  const files = Array.from(event.clipboardData.files || [])
                  if (files.length) {
                    event.preventDefault()
                    uploadFiles(files)
                  }
                }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                    event.preventDefault()
                    sendMessage()
                  }
                }}
              />
              <button className="send" onClick={sendMessage} disabled={runActive || uploading || !activeAgent || (!input.trim() && !attachments.length)}>
                {loading ? <Loader2 size={17} className="spin" /> : <Send size={17} />}
              </button>
            </div>
          </footer>
        </main>
      </section>
      <ConfirmDialog {...dialogProps} />
    </div>
  )
}
