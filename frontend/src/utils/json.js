const maxSafeInteger = typeof BigInt === 'function' ? BigInt(Number.MAX_SAFE_INTEGER) : null

function isDigit(char) {
  return char >= '0' && char <= '9'
}

function shouldPreserveInteger(token) {
  const text = token.startsWith('-') ? token.slice(1) : token
  if (text.length < 16) {
    return false
  }
  if (!maxSafeInteger) {
    return true
  }
  try {
    const value = BigInt(token)
    return value > maxSafeInteger || value < -maxSafeInteger
  } catch {
    return false
  }
}

export function preserveLargeIntegerJson(text) {
  let output = ''
  let index = 0
  let inString = false
  let escaped = false

  while (index < text.length) {
    const char = text[index]

    if (inString) {
      output += char
      if (escaped) {
        escaped = false
      } else if (char === '\\') {
        escaped = true
      } else if (char === '"') {
        inString = false
      }
      index += 1
      continue
    }

    if (char === '"') {
      inString = true
      output += char
      index += 1
      continue
    }

    const startsNumber = char === '-' ? isDigit(text[index + 1]) : isDigit(char)
    if (startsNumber) {
      const start = index
      let hasFraction = false
      let hasExponent = false
      if (text[index] === '-') {
        index += 1
      }
      if (text[index] === '0') {
        index += 1
      } else {
        while (isDigit(text[index])) {
          index += 1
        }
      }
      if (text[index] === '.') {
        hasFraction = true
        index += 1
        while (isDigit(text[index])) {
          index += 1
        }
      }
      if (text[index] === 'e' || text[index] === 'E') {
        hasExponent = true
        index += 1
        if (text[index] === '+' || text[index] === '-') {
          index += 1
        }
        while (isDigit(text[index])) {
          index += 1
        }
      }

      const token = text.slice(start, index)
      if (!hasFraction && !hasExponent && shouldPreserveInteger(token)) {
        output += `"${token}"`
      } else {
        output += token
      }
      continue
    }

    output += char
    index += 1
  }

  return output
}

export function parseJsonPreservingLargeIntegers(text) {
  if (!text) {
    return null
  }
  return JSON.parse(preserveLargeIntegerJson(text))
}
