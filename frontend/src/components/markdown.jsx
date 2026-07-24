function renderInline(text, keyPrefix) {
  const value = String(text || '')
  const pattern = /(\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\((https?:\/\/[^\s)]+)\))/g
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
      nodes.push(<code key={`${keyPrefix}-c-${index}`}>{match[3]}</code>)
    } else if (match[4] && match[5]) {
      nodes.push(
        <a key={`${keyPrefix}-a-${index}`} href={match[5]} target="_blank" rel="noreferrer">
          {match[4]}
        </a>,
      )
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

function parseBlocks(value) {
  const lines = String(value || '').replace(/\r\n/g, '\n').split('\n')
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

    if (trimmed.startsWith('```')) {
      flushParagraph(blocks, paragraph)
      const codeLines = []
      index += 1
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        codeLines.push(lines[index])
        index += 1
      }
      if (index < lines.length) {
        index += 1
      }
      blocks.push({ type: 'code', text: codeLines.join('\n') })
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

    const heading = /^(#{1,4})\s+(.+)$/.exec(trimmed)
    if (heading) {
      flushParagraph(blocks, paragraph)
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] })
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

export function MarkdownText({ value, empty = '暂无内容' }) {
  if (!value || !String(value).trim()) {
    return <p>{empty}</p>
  }
  const blocks = parseBlocks(value)
  return (
    <div className="markdown-text">
      {blocks.map((block, index) => {
        if (block.type === 'heading') {
          const HeadingTag = `h${Math.min(block.level + 2, 6)}`
          return <HeadingTag key={index}>{renderInline(block.text, `h-${index}`)}</HeadingTag>
        }
        if (block.type === 'code') {
          return <pre key={index}><code>{block.text}</code></pre>
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
        if (block.type === 'ul') {
          return (
            <ul key={index}>
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>{renderInline(item, `ul-${index}-${itemIndex}`)}</li>
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
            {block.lines.map((line, lineIndex) => (
              <span key={lineIndex}>
                {lineIndex > 0 && <br />}
                {renderInline(line, `p-${index}-${lineIndex}`)}
              </span>
            ))}
          </p>
        )
      })}
    </div>
  )
}
