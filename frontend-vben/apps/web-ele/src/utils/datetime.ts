/**
 * 统一时间格式化工具
 * 后端返回的时间字段均为 epoch 毫秒数 (Long)，需要在前端统一格式化。
 */

const formatterCache = new Map<string, Intl.DateTimeFormat>();

function getFormatter(timeZone?: string): Intl.DateTimeFormat {
  const key = timeZone || 'local';
  let formatter = formatterCache.get(key);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat('zh-CN', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });
    formatterCache.set(key, formatter);
  }
  return formatter;
}

function normalizeParts(parts: Intl.DateTimeFormatPart[]): string {
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second}`;
}

/**
 * 将 epoch 毫秒时间戳格式化为 yyyy-MM-dd HH:mm:ss
 * @param value epoch 毫秒数或 Date 或 ISO 字符串
 * @param placeholder 无效时的占位符
 * @param timeZone 可选时区
 */
export function formatDateTime(
  value?: number | string | Date | null,
  placeholder = '-',
  timeZone?: string,
): string {
  if (value == null || value === '') {
    return placeholder;
  }
  // 数字类型直接当 epoch ms
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || value <= 0) {
      return placeholder;
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return placeholder;
    }
    return normalizeParts(getFormatter(timeZone).formatToParts(date));
  }
  // 字符串：尝试解析
  if (typeof value === 'string') {
    // 可能是 ISO 字符串
    const timestamp = Date.parse(value);
    if (Number.isFinite(timestamp)) {
      return normalizeParts(getFormatter(timeZone).formatToParts(new Date(timestamp)));
    }
    return placeholder;
  }
  // Date 对象
  if (value instanceof Date) {
    const ts = value.getTime();
    if (!Number.isFinite(ts)) {
      return placeholder;
    }
    return normalizeParts(getFormatter(timeZone).formatToParts(value));
  }
  return placeholder;
}

/**
 * 格式化为日期部分 yyyy-MM-dd
 */
export function formatDate(
  value?: number | string | Date | null,
  placeholder = '-',
): string {
  const formatted = formatDateTime(value, placeholder);
  return formatted === placeholder ? placeholder : formatted.substring(0, 10);
}

/**
 * 相对时间（如 "3分钟前"）
 */
export function formatRelativeTime(value?: number | string | Date | null): string {
  if (value == null) return '-';
  const timestamp = typeof value === 'number' ? value : Date.parse(String(value));
  if (!Number.isFinite(timestamp)) return '-';
  const diff = Date.now() - timestamp;
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 30) return `${days}天前`;
  return formatDateTime(value);
}