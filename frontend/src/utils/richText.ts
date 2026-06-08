const BLOCKED_TAG_SELECTOR = 'script, iframe, object, embed, style, link, meta, form, input, button'
const ALLOWED_TAGS = new Set([
  'a',
  'blockquote',
  'br',
  'code',
  'div',
  'em',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'hr',
  'img',
  'li',
  'ol',
  'p',
  'pre',
  's',
  'span',
  'strong',
  'sub',
  'sup',
  'table',
  'tbody',
  'td',
  'th',
  'thead',
  'tr',
  'u',
  'ul',
])
const GLOBAL_ALLOWED_ATTRIBUTES = new Set(['class', 'style', 'title'])
const TAG_ALLOWED_ATTRIBUTES = new Map([
  ['a', new Set(['href', 'target', 'rel'])],
  ['img', new Set(['src', 'alt', 'title', 'width', 'height', 'loading', 'decoding'])],
  ['th', new Set(['colspan', 'rowspan'])],
  ['td', new Set(['colspan', 'rowspan'])],
])
const ALLOWED_URL_PATTERN = /^(https?:|mailto:|tel:|\/|#)/i
const ALLOWED_IMAGE_URL_PATTERN = /^(https?:|\/)/i
const ALLOWED_STYLE_PROPERTIES = new Set([
  'color',
  'background-color',
  'text-align',
  'font-weight',
  'font-style',
  'text-decoration',
  'text-decoration-line',
  'vertical-align',
])

export function sanitizeRichText(value?: string | null) {
  if (!value) {
    return ''
  }
  if (typeof document === 'undefined') {
    return value
      .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
      .replace(/\son\w+=("[^"]*"|'[^']*'|[^\s>]*)/gi, '')
      .replace(/javascript:/gi, '')
  }

  const container = document.createElement('div')
  container.innerHTML = value
  container.querySelectorAll(BLOCKED_TAG_SELECTOR).forEach((node) => node.remove())
  Array.from(container.querySelectorAll('*')).forEach((node) => {
    const element = node as HTMLElement
    if (!ALLOWED_TAGS.has(element.tagName.toLowerCase())) {
      element.replaceWith(...Array.from(element.childNodes))
      return
    }
    sanitizeElement(element)
  })
  return container.innerHTML
}

export function richTextToPlainText(value?: string | null) {
  if (!value) {
    return ''
  }
  if (typeof document === 'undefined') {
    return sanitizeRichText(value).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
  }

  const container = document.createElement('div')
  container.innerHTML = sanitizeRichText(value)
  return (container.textContent || '').replace(/\s+/g, ' ').trim()
}

export function countRichTextElements(value: string | null | undefined, selector: string) {
  if (!value || typeof document === 'undefined') {
    return 0
  }
  const container = document.createElement('div')
  container.innerHTML = sanitizeRichText(value)
  return container.querySelectorAll(selector).length
}

export function hasMeaningfulRichText(value?: string | null) {
  const safeValue = sanitizeRichText(value)
  return richTextToPlainText(safeValue).length > 0 || countRichTextElements(safeValue, 'img, table, hr') > 0
}

function sanitizeElement(element: HTMLElement) {
  const tagName = element.tagName.toLowerCase()
  const tagAllowedAttributes = TAG_ALLOWED_ATTRIBUTES.get(tagName) || new Set<string>()

  Array.from(element.attributes).forEach((attribute) => {
    const name = attribute.name.toLowerCase()
    const rawValue = attribute.value || ''

    if (!GLOBAL_ALLOWED_ATTRIBUTES.has(name) && !tagAllowedAttributes.has(name)) {
      element.removeAttribute(attribute.name)
      return
    }

    if (name.startsWith('on') || containsUnsafeToken(rawValue)) {
      element.removeAttribute(attribute.name)
      return
    }

    if (name === 'href' && !ALLOWED_URL_PATTERN.test(rawValue.trim())) {
      element.removeAttribute(attribute.name)
      return
    }

    if (name === 'src' && !ALLOWED_IMAGE_URL_PATTERN.test(rawValue.trim())) {
      element.removeAttribute(attribute.name)
      return
    }

    if (name === 'style') {
      const style = sanitizeStyle(rawValue)
      if (style) {
        element.setAttribute('style', style)
      } else {
        element.removeAttribute(attribute.name)
      }
    }
  })

  if (element.tagName.toLowerCase() === 'a' && element.hasAttribute('href')) {
    element.setAttribute('target', '_blank')
    element.setAttribute('rel', 'noopener noreferrer')
  }
}

function sanitizeStyle(style: string) {
  return style
    .split(';')
    .map((declaration) => declaration.trim())
    .filter(Boolean)
    .map((declaration) => {
      const separatorIndex = declaration.indexOf(':')
      if (separatorIndex <= 0) {
        return ''
      }
      const property = declaration.slice(0, separatorIndex).trim().toLowerCase()
      const value = declaration.slice(separatorIndex + 1).trim()
      if (!ALLOWED_STYLE_PROPERTIES.has(property) || containsUnsafeToken(value) || !isAllowedStyleValue(property, value)) {
        return ''
      }
      return `${property}: ${value}`
    })
    .filter(Boolean)
    .join('; ')
}

function containsUnsafeToken(value: string) {
  const normalized = value.replace(/\s+/g, '').toLowerCase()
  return normalized.includes('javascript:') || normalized.includes('expression(') || normalized.includes('url(') || normalized.includes('@import')
}

function isAllowedStyleValue(property: string, value: string) {
  const normalized = value.trim().toLowerCase()
  if (property === 'text-align') {
    return ['left', 'center', 'right', 'justify'].includes(normalized)
  }
  if (property === 'font-weight') {
    return /^(normal|bold|bolder|lighter|[1-9]00)$/.test(normalized)
  }
  if (property === 'font-style') {
    return ['normal', 'italic', 'oblique'].includes(normalized)
  }
  if (property === 'text-decoration' || property === 'text-decoration-line') {
    return /^[a-z\s-]+$/.test(normalized)
  }
  if (property === 'vertical-align') {
    return ['baseline', 'sub', 'super', 'top', 'middle', 'bottom', 'text-top', 'text-bottom'].includes(normalized)
  }
  return /^(#[0-9a-f]{3,8}|rgba?\([0-9,.%\s]+\)|hsla?\([0-9,.%\s]+\)|[a-z-]+)$/i.test(value)
}