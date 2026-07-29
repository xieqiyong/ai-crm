import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

const assistantMarkdownComponents = {
  a({ children, node: _node, ...props }) {
    return <a {...props} target="_blank" rel="noreferrer">{children}</a>
  },
  table({ children, node: _node }) {
    return (
      <div className="agent-markdown-table">
        <table>{children}</table>
      </div>
    )
  },
}

function renderInline(text, keyPrefix) {
  const value = String(text || '')
  const pattern = /(\*\*([^*]+)\*\*|~~([^~]+)~~|`([^`]+)`|\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s)]+)|\*([^*\n]+)\*)/g
  const nodes = []
  let lastIndex = 0
  let index = 0
  let match = pattern.exec(value)
  while (match) {
    if (match.index > lastIndex) {
      nodes.push(value.slice(lastIndex, match.index))
    }
    if (match[2]) {
      nodes.push(<strong key={`${keyPrefix}-b-${index}`}>{match[2]}</strong>)
    } else if (match[3]) {
      nodes.push(<del key={`${keyPrefix}-d-${index}`}>{match[3]}</del>)
    } else if (match[4]) {
      nodes.push(<code key={`${keyPrefix}-c-${index}`}>{match[4]}</code>)
    } else if (match[5] && match[6]) {
      nodes.push(
        <a key={`${keyPrefix}-a-${index}`} href={match[6]} target="_blank" rel="noreferrer">
          {match[5]}
        </a>,
      )
    } else if (match[7]) {
      nodes.push(
        <a key={`${keyPrefix}-u-${index}`} href={match[7]} target="_blank" rel="noreferrer">
          {match[7]}
        </a>,
      )
    } else if (match[8]) {
      nodes.push(<em key={`${keyPrefix}-e-${index}`}>{match[8]}</em>)
    }
    lastIndex = pattern.lastIndex
    index += 1
    match = pattern.exec(value)
  }
  if (lastIndex < value.length) {
    nodes.push(value.slice(lastIndex))
  }
  return nodes
}

function flushParagraph(blocks, paragraph) {
  if (!paragraph.length) {
    return
  }
  blocks.push({
    type: 'paragraph',
    lines: paragraph.splice(0, paragraph.length),
  })
}

function isTableLine(line) {
  const value = String(line || '').trim()
  return value.startsWith('|') && value.endsWith('|') && value.includes('|')
}

function isTableSeparator(line) {
  const cells = splitTableLine(line)
  if (cells.length === 0) {
    return false
  }
  return cells.every((cell) => /^:?-{3,}:?$/.test(cell.trim()))
}

function splitTableLine(line) {
  return String(line || '')
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim())
}

function parseBlocks(value, variant) {
  const lines = normalizeMarkdownValue(value, variant).replace(/\r\n/g, '\n').split('\n')
  const blocks = []
  const paragraph = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    const trimmed = line.trim()

    if (!trimmed) {
      flushParagraph(blocks, paragraph)
      index += 1
      continue
    }

    if (/^(-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      flushParagraph(blocks, paragraph)
      blocks.push({ type: 'hr' })
      index += 1
      continue
    }

    if (trimmed.startsWith('```')) {
      flushParagraph(blocks, paragraph)
      const codeLines = []
      const language = trimmed.replace(/^```/, '').trim()
      index += 1
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        codeLines.push(lines[index])
        index += 1
      }
      if (index < lines.length) {
        index += 1
      }
      blocks.push({ type: 'code', text: codeLines.join('\n'), language })
      continue
    }

    if (isTableLine(trimmed) && index + 1 < lines.length && isTableSeparator(lines[index + 1])) {
      flushParagraph(blocks, paragraph)
      const headers = splitTableLine(trimmed)
      const rows = []
      index += 2
      while (index < lines.length && isTableLine(lines[index])) {
        rows.push(splitTableLine(lines[index]))
        index += 1
      }
      blocks.push({ type: 'table', headers, rows })
      continue
    }

    const heading = (variant === 'assistant' ? /^(#{1,6})\s*(\S.*)$/ : /^(#{1,6})\s+(.+)$/).exec(trimmed)
    if (heading) {
      flushParagraph(blocks, paragraph)
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] })
      index += 1
      continue
    }

    if (looksLikeAssistantHeading(trimmed, variant)) {
      flushParagraph(blocks, paragraph)
      blocks.push({ type: 'heading', level: 3, text: trimmed })
      index += 1
      continue
    }

    if (trimmed.startsWith('>')) {
      flushParagraph(blocks, paragraph)
      const quoteLines = []
      while (index < lines.length && lines[index].trim().startsWith('>')) {
        quoteLines.push(lines[index].trim().replace(/^>\s?/, ''))
        index += 1
      }
      blocks.push({ type: 'quote', lines: quoteLines })
      continue
    }

    if (/^[-*]\s+\[[ xX]\]\s+/.test(trimmed)) {
      flushParagraph(blocks, paragraph)
      const items = []
      while (index < lines.length && /^[-*]\s+\[[ xX]\]\s+/.test(lines[index].trim())) {
        const task = /^[-*]\s+\[([ xX])\]\s+(.+)$/.exec(lines[index].trim())
        items.push({
          checked: task ? /x/i.test(task[1]) : false,
          text: task ? task[2] : lines[index].trim(),
        })
        index += 1
      }
      blocks.push({ type: 'task', items })
      continue
    }

    if (looksLikeAssistantListItem(trimmed, variant)) {
      flushParagraph(blocks, paragraph)
      const items = []
      while (index < lines.length) {
        const itemText = lines[index].trim()
        if (/^[-*]\s+/.test(itemText)) {
          items.push(itemText.replace(/^[-*]\s+/, ''))
          index += 1
          continue
        }
        if (looksLikeAssistantListItem(itemText, variant)) {
          items.push(itemText)
          index += 1
          continue
        }
        break
      }
      blocks.push({ type: 'ul', items })
      continue
    }

    if (/^[-*]\s+/.test(trimmed)) {
      flushParagraph(blocks, paragraph)
      const items = []
      while (index < lines.length && /^[-*]\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^[-*]\s+/, ''))
        index += 1
      }
      blocks.push({ type: 'ul', items })
      continue
    }

    if (/^\d+\.\s+/.test(trimmed)) {
      flushParagraph(blocks, paragraph)
      const items = []
      while (index < lines.length && /^\d+\.\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^\d+\.\s+/, ''))
        index += 1
      }
      blocks.push({ type: 'ol', items })
      continue
    }

    paragraph.push(line)
    index += 1
  }

  flushParagraph(blocks, paragraph)
  return blocks
}

function looksLikeAssistantHeading(value, variant) {
  if (variant !== 'assistant') {
    return false
  }
  const text = String(value || '').trim()
  if (!text || text.length > 32) {
    return false
  }
  if (/[。；，,.、]$/.test(text)) {
    return false
  }
  if (/^[-*]\s+/.test(text) || /^\d+\.\s+/.test(text) || text.includes('|')) {
    return false
  }
  if (/^[a-z][a-z0-9_-]{2,}$/i.test(text) && text.includes('-')) {
    return true
  }
  return /[？?]$/.test(text)
    || /^(核心|产品|技术|能力|优势|特点|架构|方案|客户|知识库|部署|安全|总结|结论|建议|下一步|风险|说明|其他|已配置|可用|使用方式|适用场景|输出要求|注意事项)/.test(text)
}

function looksLikeAssistantListItem(value, variant) {
  if (variant !== 'assistant') {
    return false
  }
  const text = String(value || '').trim()
  if (!text || text.length > 180) {
    return false
  }
  if (/^[-*]\s+/.test(text)) {
    return true
  }
  if (/^\d+\.\s+/.test(text) || text.includes('|')) {
    return false
  }
  return /^[\u4e00-\u9fa5A-Za-z0-9][^。！？；\n]{1,42}\s*[—-]\s*\S+/.test(text)
}

function normalizeMarkdownValue(value, variant) {
  if (value === undefined || value === null) {
    return ''
  }
  let text = String(value)
  const trimmed = text.trim()
  if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
    try {
      const parsed = JSON.parse(trimmed)
      if (typeof parsed === 'string') {
        text = parsed
      }
    } catch {
      text = String(value)
    }
  }
  const escapedLineCount = (text.match(/\\n/g) || []).length
  const realLineCount = (text.match(/\n/g) || []).length
  if (escapedLineCount > realLineCount) {
    text = text.replace(/\\r\\n/g, '\n').replace(/\\n/g, '\n')
  }
  if (variant === 'assistant') {
    text = normalizeAssistantMarkdown(text)
  }
  text = normalizeCompactTables(text)
  text = text
    .replace(/([^\n|])(\|[^|\n]+\|[^|\n]+\|)/g, (match, before, tableText) => {
      if (/特性|说明|字段|值|项目|内容|能力|特点/.test(tableText)) {
        return `${before}\n\n${tableText}`
      }
      return match
    })
    .replace(/\n{3,}/g, '\n\n')
  return text
}

function normalizeAssistantMarkdown(text) {
  return String(text || '')
    .replace(/(^|\n)(#{1,6})([^\s#\n])/g, '$1$2 $3')
    .replace(/([^\n])\s*-\s+(?=[\u4e00-\u9fa5A-Za-z0-9][^。\n]{0,48}\s*[—:：-])/g, '$1\n- ')
    .replace(/(^|\n)(说明|其他能力(?:（[^）]*）)?|已配置的\s*Skill|可用能力|使用方式|适用场景|输出要求|注意事项)([:：]?)(?=\n|$)/g, '$1### $2$3')
}

function normalizeCompactTables(text) {
  return String(text || '').replace(/((?:\|[^|\n]*\|[^|\n]*\|\s*){3,})/g, (match) => {
    const rows = match.match(/\|[^|\n]*\|[^|\n]*\|/g) || []
    if (rows.length < 3) {
      return match
    }
    const hasSeparator = rows.some((row) => splitTableLine(row).every((cell) => /^:?-{2,}:?$/.test(cell)))
    if (!hasSeparator) {
      return match
    }
    return `\n\n${rows.map((row) => normalizeTableRow(row)).join('\n')}\n\n`
  })
}

function normalizeTableRow(row) {
  const cells = splitTableLine(row)
  return `| ${cells.join(' | ')} |`
}

function renderTable(block, index, variant) {
  if (variant === 'assistant') {
    return renderAssistantTable(block, index)
  }
  return (
    <div className="markdown-table-wrap" key={index}>
      <table>
        <thead>
          <tr>
            {block.headers.map((header, cellIndex) => (
              <th key={cellIndex}>{renderInline(header, `th-${index}-${cellIndex}`)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {block.rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {block.headers.map((header, cellIndex) => (
                <td key={cellIndex}>
                  {renderInline(row[cellIndex] || '', `td-${index}-${rowIndex}-${cellIndex}`)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function renderAssistantTable(block, index) {
  const headers = block.headers || []
  return (
    <div className="markdown-table-cards" key={index}>
      {block.rows.map((row, rowIndex) => {
        const title = row[0] || headers[0] || `第 ${rowIndex + 1} 项`
        return (
          <div className="markdown-table-card" key={rowIndex}>
            <strong>{renderInline(title, `tc-title-${index}-${rowIndex}`)}</strong>
            <dl>
              {headers.slice(1).map((header, cellIndex) => {
                const value = row[cellIndex + 1] || ''
                if (!value) {
                  return null
                }
                return (
                  <div key={cellIndex}>
                    <dt>{renderInline(header, `tc-dt-${index}-${rowIndex}-${cellIndex}`)}</dt>
                    <dd>{renderInline(value, `tc-dd-${index}-${rowIndex}-${cellIndex}`)}</dd>
                  </div>
                )
              })}
            </dl>
          </div>
        )
      })}
    </div>
  )
}

export function MarkdownText({ value, empty = '暂无内容', variant = 'default' }) {
  if (!value || !String(value).trim()) {
    return <p>{empty}</p>
  }
  if (variant === 'assistant') {
    return (
      <div className="agent-markdown">
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          components={assistantMarkdownComponents}
        >
          {normalizeAssistantMarkdownSource(value)}
        </ReactMarkdown>
      </div>
    )
  }
  const blocks = parseBlocks(value, variant)
  const className = ['markdown-text', variant !== 'default' ? `markdown-text-${variant}` : '']
    .filter(Boolean)
    .join(' ')
  return (
    <div className={className}>
      {blocks.map((block, index) => {
        if (block.type === 'heading') {
          const HeadingTag = `h${Math.min(block.level + (variant === 'assistant' ? 1 : 2), 6)}`
          return <HeadingTag key={index}>{renderInline(block.text, `h-${index}`)}</HeadingTag>
        }
        if (block.type === 'hr') {
          return <hr key={index} />
        }
        if (block.type === 'code') {
          return (
            <pre key={index}>
              {block.language && <span>{block.language}</span>}
              <code>{block.text}</code>
            </pre>
          )
        }
        if (block.type === 'quote') {
          return (
            <blockquote key={index}>
              {block.lines.map((line, lineIndex) => (
                <p key={lineIndex}>{renderInline(line, `q-${index}-${lineIndex}`)}</p>
              ))}
            </blockquote>
          )
        }
        if (block.type === 'table') {
          return renderTable(block, index, variant)
        }
        if (block.type === 'ul') {
          return (
            <ul key={index}>
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>{renderInline(item, `ul-${index}-${itemIndex}`)}</li>
              ))}
            </ul>
          )
        }
        if (block.type === 'task') {
          return (
            <ul key={index} className="markdown-task-list">
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>
                  <input type="checkbox" checked={item.checked} readOnly />
                  <span>{renderInline(item.text, `task-${index}-${itemIndex}`)}</span>
                </li>
              ))}
            </ul>
          )
        }
        if (block.type === 'ol') {
          return (
            <ol key={index}>
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>{renderInline(item, `ol-${index}-${itemIndex}`)}</li>
              ))}
            </ol>
          )
        }
        return (
          <p key={index}>
            {renderInline(joinParagraphLines(block.lines), `p-${index}`)}
          </p>
        )
      })}
    </div>
  )
}

function normalizeAssistantMarkdownSource(value) {
  let text = String(value || '')
  const trimmed = text.trim()
  if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
    try {
      const parsed = JSON.parse(trimmed)
      if (typeof parsed === 'string') {
        text = parsed
      }
    } catch {
      text = String(value || '')
    }
  }
  const escapedLineCount = (text.match(/\\n/g) || []).length
  const realLineCount = (text.match(/\n/g) || []).length
  if (escapedLineCount > realLineCount) {
    text = text.replace(/\\r\\n/g, '\n').replace(/\\n/g, '\n')
  }
  return text
    .replace(/\r\n/g, '\n')
    .replace(/([^#\n])(?=#{2,6}(?:\s|[^\s#]))/g, '$1\n\n')
    .replace(/([^\n])-\s*(?=(?:\*\*|[\u4e00-\u9fa5]))/g, '$1\n- ')
    .replace(/(^|\n)-\s*(?=(?:\*\*|[\u4e00-\u9fa5]))/g, '$1- ')
    .replace(/([^\n])---(?=\s*(?:\n|$))/g, '$1\n\n---')
    .replace(
      /(^|\n)(#{2,6})\s*(结论|依据|下一步动作|风险提醒|建议)(?=\S)/g,
      '$1$2 $3\n\n',
    )
    .replace(/(^|\n)(#{1,6})([^\s#\n])/g, '$1$2 $3')
    .replace(/\n{3,}/g, '\n\n')
}

function joinParagraphLines(lines) {
  return (lines || [])
    .map((line) => String(line || '').trim())
    .filter(Boolean)
    .join(' ')
}
