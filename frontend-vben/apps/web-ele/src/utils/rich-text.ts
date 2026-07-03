/**
 * 富文本安全处理工具
 * - 清理危险标签/属性，防止 XSS
 * - 提供纯文本/摘要提取能力
 */
const BLOCKED_TAG_SELECTOR =
  'script, iframe, object, embed, style, link, meta, form, input, button';

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
]);

const GLOBAL_ALLOWED_ATTRIBUTES = new Set(['class', 'style', 'title']);

const TAG_ALLOWED_ATTRIBUTES: Record<string, Set<string>> = {
  a: new Set(['href', 'rel', 'target']),
  img: new Set([
    'alt',
    'decoding',
    'height',
    'loading',
    'src',
    'title',
    'width',
  ]),
  th: new Set(['colspan', 'rowspan']),
  td: new Set(['colspan', 'rowspan']),
};

const ALLOWED_URL_PATTERN = /^(?:https?:|mailto:|tel:|\/|#)/i;
const ALLOWED_IMAGE_URL_PATTERN = /^(?:https?:|\/)/i;

const ALLOWED_STYLE_PROPERTIES = new Set([
  'background-color',
  'color',
  'font-style',
  'font-weight',
  'text-align',
  'text-decoration',
  'text-decoration-line',
  'vertical-align',
]);

/**
 * 清理富文本，过滤危险内容
 * @param value 原始 HTML
 * @returns 清理后的安全 HTML
 */
export function sanitizeRichText(value?: null | string): string {
  if (!value) {
    return '';
  }

  // SSR / 非浏览器环境：做最基本的清理
  if (typeof document === 'undefined') {
    return stripScriptTags(value)
      .replaceAll(/\son\w+=(?:"[^"]*"|'[^']*'|[^\s>]*)/gi, '')
      .replaceAll(/javascript:/gi, '');
  }

  const container = document.createElement('div');
  container.innerHTML = value;
  container
    .querySelectorAll(BLOCKED_TAG_SELECTOR)
    .forEach((node) => node.remove());

  [...container.querySelectorAll('*')].forEach((node) => {
    const element = node as HTMLElement;
    if (!ALLOWED_TAGS.has(element.tagName.toLowerCase())) {
      element.replaceWith(...element.childNodes);
      return;
    }
    sanitizeElement(element);
  });

  return container.innerHTML;
}

/**
 * 富文本转纯文本
 */
export function richTextToPlainText(value?: null | string): string {
  if (!value) {
    return '';
  }

  const safe = sanitizeRichText(value);

  if (typeof document === 'undefined') {
    return safe
      .replaceAll(/<[^>]+>/g, ' ')
      .replaceAll(/\s+/g, ' ')
      .trim();
  }

  const container = document.createElement('div');
  container.innerHTML = safe;
  return (container.textContent || '').replaceAll(/\s+/g, ' ').trim();
}

/**
 * 统计富文本中指定选择器的元素数量
 */
export function countRichTextElements(
  value: null | string | undefined,
  selector: string,
): number {
  if (!value || typeof document === 'undefined') {
    return 0;
  }

  const container = document.createElement('div');
  container.innerHTML = sanitizeRichText(value);
  return container.querySelectorAll(selector).length;
}

/**
 * 判断富文本是否有实际内容
 */
export function hasMeaningfulRichText(value?: null | string): boolean {
  const safeValue = sanitizeRichText(value);
  return (
    richTextToPlainText(safeValue).length > 0 ||
    countRichTextElements(safeValue, 'img, table, hr') > 0
  );
}

function sanitizeElement(element: HTMLElement): void {
  const tagName = element.tagName.toLowerCase();
  const tagAllowedAttributes =
    TAG_ALLOWED_ATTRIBUTES[tagName] || new Set<string>();

  [...element.attributes].forEach((attribute) => {
    const name = attribute.name.toLowerCase();
    const rawValue = attribute.value || '';

    // 全局允许属性 + 标签允许属性
    if (
      !GLOBAL_ALLOWED_ATTRIBUTES.has(name) &&
      !tagAllowedAttributes.has(name)
    ) {
      element.removeAttribute(attribute.name);
      return;
    }

    // 事件属性
    if (name.startsWith('on') || containsUnsafeToken(rawValue)) {
      element.removeAttribute(attribute.name);
      return;
    }

    // URL 校验
    if (name === 'href' && !ALLOWED_URL_PATTERN.test(rawValue.trim())) {
      element.removeAttribute(attribute.name);
      return;
    }

    if (name === 'src' && !ALLOWED_IMAGE_URL_PATTERN.test(rawValue.trim())) {
      element.removeAttribute(attribute.name);
      return;
    }

    // Style 清理
    if (name === 'style') {
      const style = sanitizeStyle(rawValue);
      if (style) {
        element.setAttribute('style', style);
      } else {
        element.removeAttribute(attribute.name);
      }
    }
  });

  if (tagName === 'a' && element.hasAttribute('href')) {
    element.setAttribute('target', '_blank');
    element.setAttribute('rel', 'noopener noreferrer');
  }
}

function sanitizeStyle(style: string): string {
  return style
    .split(';')
    .map((declaration) => declaration.trim())
    .filter(Boolean)
    .map((declaration) => {
      const separatorIndex = declaration.indexOf(':');
      if (separatorIndex <= 0) {
        return '';
      }
      const property = declaration
        .slice(0, separatorIndex)
        .trim()
        .toLowerCase();
      const value = declaration.slice(separatorIndex + 1).trim();
      if (
        !ALLOWED_STYLE_PROPERTIES.has(property) ||
        containsUnsafeToken(value) ||
        !isAllowedStyleValue(property, value)
      ) {
        return '';
      }
      return `${property}: ${value}`;
    })
    .filter(Boolean)
    .join('; ');
}

function containsUnsafeToken(value: string): boolean {
  const normalized = value.replaceAll(/\s+/g, '').toLowerCase();
  return (
    normalized.includes('javascript:') ||
    normalized.includes('expression(') ||
    normalized.includes('url(') ||
    normalized.includes('@import')
  );
}

function stripScriptTags(value: string): string {
  let output = value;
  let lowerOutput = output.toLowerCase();
  let startIndex = lowerOutput.indexOf('<script');

  while (startIndex >= 0) {
    const openEndIndex = lowerOutput.indexOf('>', startIndex);
    if (openEndIndex === -1) {
      return output.slice(0, startIndex);
    }

    const closeStartIndex = lowerOutput.indexOf('</script>', openEndIndex + 1);
    const endIndex =
      closeStartIndex === -1 ? openEndIndex + 1 : closeStartIndex + 9;

    output = `${output.slice(0, startIndex)}${output.slice(endIndex)}`;
    lowerOutput = output.toLowerCase();
    startIndex = lowerOutput.indexOf('<script', startIndex);
  }

  return output;
}

function isAllowedStyleValue(property: string, value: string): boolean {
  const normalized = value.trim().toLowerCase();
  switch (property) {
    case 'font-style': {
      return ['italic', 'normal', 'oblique'].includes(normalized);
    }
    case 'font-weight': {
      return /^(?:normal|bold|bolder|lighter|[1-9]00)$/.test(normalized);
    }
    case 'text-align': {
      return ['center', 'justify', 'left', 'right'].includes(normalized);
    }
    case 'text-decoration':
    case 'text-decoration-line': {
      return /^[a-z\s-]+$/.test(normalized);
    }
    case 'vertical-align': {
      return [
        'baseline',
        'bottom',
        'middle',
        'sub',
        'super',
        'text-bottom',
        'text-top',
        'top',
      ].includes(normalized);
    }
    default: {
      return /^(?:#[0-9a-f]{3,8}|rgba?\([0-9,.%\s]+\)|hsla?\([0-9,.%\s]+\)|[a-z-]+)$/i.test(
        value,
      );
    }
  }
}
