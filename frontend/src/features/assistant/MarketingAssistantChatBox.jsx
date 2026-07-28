import { useEffect, useMemo, useRef, useState } from 'react'
import { Bot, Send, ShieldCheck } from 'lucide-react'
import { Badge, Card, MarkdownText } from '../../components'
import { api } from '../../api'
import { backendAddressLabel, runtimeConfig } from '../../config/env'

function typewriterIntervalMs() {
  const value = Number(runtimeConfig.assistantTypewriterInterval || 12)
  if (!Number.isFinite(value)) return 12
  return Math.max(4, Math.min(value, 80))
}

function typewriterStep() {
  const value = Number(runtimeConfig.assistantTypewriterStep || 4)
  if (!Number.isFinite(value)) return 4
  return Math.max(1, Math.min(value, 20))
}

export function findAssistantRouteLabel(routeGroups, routeKey) {
  const activeKey = String(routeKey || '').startsWith('customers/detail/') ? 'customers' : routeKey
  for (const group of routeGroups || []) {
    const item = (group.items || []).find((route) => route.key === activeKey)
    if (item) return item.label
  }
  return '当前页面'
}

function buildDefaultSuggestions(routeKey) {
  const key = String(routeKey || '')
  if (key.startsWith('channels')) {
    return ['生成获客表单字段', '生成短信投放文案', '分析渠道获客情况']
  }
  if (key.startsWith('leads')) {
    return ['今天优先跟哪些线索？', '线索转客户标准是什么？', '生成首次电话话术']
  }
  if (key.startsWith('customers')) {
    return ['哪些客户应该优先跟进？', '帮我做客户分层建议', '沉睡客户怎么召回？']
  }
  if (key.startsWith('opportunities')) {
    return ['哪些商机今天要推进？', '生成谈判跟进话术', '商机风险怎么看？']
  }
  return ['今天优先跟进什么？', '哪些渠道值得优化？', '线索怎么提高转化？']
}

function nextMessageId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function buildHistory(messages) {
  return (messages || []).slice(-8).map((item) => ({
    role: item.role,
    content: item.content,
  }))
}

function assistantSourceLabel(message) {
  if (message.typing) return '输出中'
  if (message.streaming) return '生成中'
  if (message.available && message.success !== false) return 'AI模型回复'
  if (message.success === false) return '规则降级'
  return '规则建议'
}

function assistantSourceTone(message) {
  if (message.typing || message.streaming) return 'info'
  if (message.available && message.success !== false) return 'success'
  if (message.success === false) return 'warning'
  return 'info'
}

function usefulThoughts(thoughts) {
  const blocked = ['调用辅助分析能力', '读取当前页面和可访问业务数据']
  const values = []
  ;(thoughts || []).forEach((item) => {
    const text = String(item || '').trim()
    if (!text || blocked.includes(text) || values.includes(text)) {
      return
    }
    values.push(text)
  })
  return values.slice(0, 4)
}

export function MarketingAssistantChatBox({
  routeKey,
  currentRole,
  onNavigate,
  onNotify,
}) {
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [messages, setMessages] = useState([])
  const typingTimersRef = useRef({})
  const defaultSuggestions = useMemo(() => buildDefaultSuggestions(routeKey), [routeKey])
  const lastAssistant = [...messages].reverse().find((item) => item.role === 'assistant')
  const suggestions = lastAssistant?.suggestions?.length ? lastAssistant.suggestions : defaultSuggestions

  useEffect(() => () => {
    Object.values(typingTimersRef.current).forEach((timer) => window.clearTimeout(timer))
    typingTimersRef.current = {}
  }, [])

  const patchAssistantMessage = (messageId, updater) => {
    setMessages((values) => values.map((item) => {
      if (item.id !== messageId) return item
      return {
        ...item,
        ...(typeof updater === 'function' ? updater(item) : updater),
      }
    }))
  }

  const clearTypewriter = (messageId) => {
    const timer = typingTimersRef.current[messageId]
    if (timer) {
      window.clearTimeout(timer)
      delete typingTimersRef.current[messageId]
    }
  }

  const startTypewriter = (messageId, fullText, finishPatch) => {
    clearTypewriter(messageId)
    const text = String(fullText || '')
    const interval = typewriterIntervalMs()
    const step = typewriterStep()
    let cursor = 0
    patchAssistantMessage(messageId, {
      content: '',
      typing: true,
      streaming: true,
      message: '正在输出结果',
    })
    const tick = () => {
      cursor = Math.min(cursor + step, text.length)
      patchAssistantMessage(messageId, {
        content: text.slice(0, cursor),
      })
      if (cursor >= text.length) {
        delete typingTimersRef.current[messageId]
        patchAssistantMessage(messageId, {
          ...finishPatch,
          typing: false,
          streaming: false,
        })
        setLoading(false)
        return
      }
      typingTimersRef.current[messageId] = window.setTimeout(tick, interval)
    }
    typingTimersRef.current[messageId] = window.setTimeout(tick, interval)
  }

  const sendMessage = async (preset) => {
    const text = String((preset ?? input) || '').trim()
    if (!text || loading) return
    const userMessage = { id: nextMessageId(), role: 'user', content: text }
    const assistantId = nextMessageId()
    const history = buildHistory(messages)
    setMessages((values) => [
      ...values,
      userMessage,
      {
        id: assistantId,
        role: 'assistant',
        title: '营销建议',
        content: '',
        available: true,
        success: true,
        message: '正在处理请求',
        suggestions: [],
        quickActions: [],
        thoughts: [],
        streaming: true,
      },
    ])
    setInput('')
    setLoading(true)
    let doneReceived = false
    let typewriterStarted = false
    try {
      await api.assistant.chatStream({
        message: text,
        routeKey,
        context: {
          history,
        },
      }, {
        onThought: (payload) => {
          const content = String(payload?.content || '').trim()
          if (!content) return
          patchAssistantMessage(assistantId, (message) => ({
            thoughts: usefulThoughts([...(message.thoughts || []), content]),
            message: content,
          }))
        },
        onDone: (payload) => {
          doneReceived = true
          const response = payload?.response || {}
          typewriterStarted = true
          startTypewriter(assistantId, response?.reply || '暂未生成建议。', {
            title: response?.title || '营销建议',
            available: Boolean(response?.available),
            success: response?.success !== false,
            message: response?.message || '',
            suggestions: response?.suggestions || [],
            quickActions: response?.quickActions || [],
          })
        },
      })
      if (!doneReceived) {
        clearTypewriter(assistantId)
        patchAssistantMessage(assistantId, {
          content: '当前助手未返回完整结果，请稍后重试。',
          success: false,
          message: '流式响应异常结束',
          suggestions: defaultSuggestions,
          quickActions: [],
          streaming: false,
          typing: false,
        })
      }
    } catch (error) {
      clearTypewriter(assistantId)
      onNotify(error.message || 'AI 营销助手请求失败', 'error')
      patchAssistantMessage(assistantId, {
        title: '请求失败',
        content: '当前助手接口暂时不可用，请稍后重试。',
        success: false,
        message: error.message || '接口请求失败',
        suggestions: defaultSuggestions,
        quickActions: [],
        streaming: false,
        typing: false,
      })
    } finally {
      if (!typewriterStarted) {
        setLoading(false)
      }
    }
  }

  const handleAction = (action) => {
    if (action?.targetRoute) {
      onNavigate(action.targetRoute)
      onNotify(action.description || `已进入${action.title}`, 'info')
      return
    }
    onNotify('该动作还需要接入确认流程', 'info')
  }

  return (
    <div className="assistant-chat-box">
      <div className="assistant-body">
        <div className="assistant-intro">
          <Bot size={24} />
          <p>你好，{currentRole?.name || '用户'}。我会结合你能访问的业务数据和知识库回答，不展示未确认信息。</p>
        </div>
        {messages.length === 0 && (
          <Card className="insight-card">
            <Badge tone="info">可用场景</Badge>
            <h3>先从当前页面问起</h3>
            <p>可以问线索优先级、渠道获客、客户分层、商机推进。当前接入后端真实统计，不构造演示数据。</p>
          </Card>
        )}
        <div className="assistant-suggestions">
          {suggestions.map((item) => (
            <button key={item} onClick={() => sendMessage(item)} disabled={loading}>{item}</button>
          ))}
        </div>
        <div className="assistant-chat-list">
          {messages.map((message) => (
            <div
              className={`assistant-message ${message.role}${message.streaming ? ' streaming' : ''}`}
              key={message.id}
            >
              {message.title && <strong>{message.title}</strong>}
              {message.role === 'assistant' && (
                <div className="assistant-message-meta">
                  <Badge tone={assistantSourceTone(message)}>{assistantSourceLabel(message)}</Badge>
                  {message.message && <small>{message.message}</small>}
                </div>
              )}
              {message.role === 'assistant' && usefulThoughts(message.thoughts).length > 0 && (
                <details className="assistant-thoughts">
                  <summary>处理进度</summary>
                  <ol>
                    {usefulThoughts(message.thoughts).map((item, index) => (
                      <li key={`${message.id}-thought-${index}`}>{item}</li>
                    ))}
                  </ol>
                </details>
              )}
              {message.role === 'assistant'
                ? (
                  <MarkdownText
                    value={message.content || (message.streaming ? '正在生成回复…' : '暂未生成建议。')}
                    variant="assistant"
                  />
                )
                : <p>{message.content}</p>}
              {message.quickActions?.length > 0 && (
                <div className="assistant-action-list">
                  {message.quickActions.map((action) => (
                    <button key={`${message.id}-${action.code}`} onClick={() => handleAction(action)}>
                      <span>{action.title}</span>
                      <small>{action.description}</small>
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
        <div className="ai-disclaimer">
          <ShieldCheck size={15} />
          后台地址：{backendAddressLabel || '默认接口'}，AI 建议仅作为销售辅助
        </div>
      </div>
      <div className="assistant-input">
        <input
          value={input}
          placeholder="向 AI 营销助手提问…"
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
              event.preventDefault()
              sendMessage()
            }
          }}
        />
        <button onClick={() => sendMessage()} disabled={loading || !input.trim()} aria-label="发送">
          <Send size={17} />
        </button>
      </div>
    </div>
  )
}
