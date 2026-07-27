import { request } from '../httpClient'

function parseJsonOutput(output) {
  if (!output) return null
  const text = String(output).trim()
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end <= start) return null
  try {
    return JSON.parse(text.slice(start, end + 1))
  } catch (err) {
    return null
  }
}

function normalizeStringList(value) {
  if (!Array.isArray(value)) return []
  return value.filter(Boolean).map((item) => String(item))
}

function adaptLeadAnalyzeResponse(payload, response) {
  const parsed = parseJsonOutput(response?.output) || {}
  const events = Array.isArray(response?.events) ? response.events : []
  const finalEvent = events.find((item) => String(item.type || '').toLowerCase().includes('final'))
  const usage = finalEvent?.metadata || {}
  return {
    ...parsed,
    available: true,
    success: Boolean(response?.success),
    leadId: payload?.leadId,
    leadName: parsed.leadName || '',
    message: response?.success ? '线索 AI 分析完成' : '线索 AI 分析失败',
    runId: parsed.runId || response?.runId || usage.runId || '',
    conversationId: parsed.conversationId || response?.conversationId || usage.conversationId || '',
    rawOutput: response?.output || '',
    runtimeEvents: events,
    keyFindings: normalizeStringList(parsed.keyFindings),
    riskWarnings: normalizeStringList(parsed.riskWarnings),
    nextActions: normalizeStringList(parsed.nextActions),
    convertDraft: parsed.convertDraft || {},
    customerProfile: parsed.customerProfile || {},
    confidence: parsed.confidence ?? 0,
    score: parsed.score ?? 0,
    recommendConvert: Boolean(parsed.recommendConvert),
  }
}

export const assistantApi = {
  chat: (payload) => request('/api/assistant/chat', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  analyzeLead: (payload) => request('/api/assistant/lead/analyze', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  analyzeLeadLegacy: (payload) => request('/api/assistant/lead/analyze', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  analyzeLeadRuntime: async (payload) => {
    const response = await request('/api/assistant/langgraph/lead/analyze', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    return adaptLeadAnalyzeResponse(payload, response)
  },
}
