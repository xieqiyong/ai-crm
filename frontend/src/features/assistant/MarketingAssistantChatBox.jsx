import { useMemo, useState } from 'react'
import { Bot, Send, ShieldCheck } from 'lucide-react'
import { Badge, Card, MarkdownText } from '../../components'
import { api } from '../../api'
import { backendAddressLabel } from '../../config/env'

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

export function MarketingAssistantChatBox({
  routeKey,
  currentRole,
  onNavigate,
  onNotify,
}) {
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [messages, setMessages] = useState([])
  const defaultSuggestions = useMemo(() => buildDefaultSuggestions(routeKey), [routeKey])
  const lastAssistant = [...messages].reverse().find((item) => item.role === 'assistant')
  const suggestions = lastAssistant?.suggestions?.length ? lastAssistant.suggestions : defaultSuggestions

  const sendMessage = async (preset) => {
    const text = String((preset ?? input) || '').trim()
    if (!text || loading) return
    const userMessage = { id: nextMessageId(), role: 'user', content: text }
    setMessages((values) => [...values, userMessage])
    setInput('')
    setLoading(true)
    try {
      const response = await api.assistant.chat({
        message: text,
        routeKey,
      })
      setMessages((values) => [
        ...values,
        {
          id: nextMessageId(),
          role: 'assistant',
          title: response?.title || '营销建议',
          content: response?.reply || '暂未生成建议。',
          suggestions: response?.suggestions || [],
          quickActions: response?.quickActions || [],
        },
      ])
    } catch (error) {
      onNotify(error.message || 'AI 营销助手请求失败', 'error')
      setMessages((values) => [
        ...values,
        {
          id: nextMessageId(),
          role: 'assistant',
          title: '请求失败',
          content: '当前助手接口暂时不可用，请稍后重试。',
          suggestions: defaultSuggestions,
          quickActions: [],
        },
      ])
    } finally {
      setLoading(false)
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
          <p>你好，{currentRole?.name || '用户'}。我会基于你当前能访问的数据给出销售建议，不展示未确认信息。</p>
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
            <div className={`assistant-message ${message.role}`} key={message.id}>
              {message.title && <strong>{message.title}</strong>}
              {message.role === 'assistant'
                ? <MarkdownText value={message.content} />
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
          {loading && <div className="assistant-message assistant-loading">正在读取真实数据并整理建议…</div>}
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
