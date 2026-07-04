/**
 * 统一时间工具。
 * 后端公开 API 使用 ISO-8601 Instant 字符串，业务时区通过 X-Time-Zone 传递。
 */

export type InstantValue = Date | null | string | undefined;

const DEFAULT_TIME_ZONE = 'Asia/Shanghai';
const formatterCache = new Map<string, Intl.DateTimeFormat>();
const zonedIsoPattern = /(z|[+-]\d{2}:?\d{2})$/i;
const localDateTimePattern =
  /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/;

export function getClientTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || DEFAULT_TIME_ZONE;
}

function getFormatter(timeZone = getClientTimeZone()): Intl.DateTimeFormat {
  const key = timeZone || DEFAULT_TIME_ZONE;
  let formatter = formatterCache.get(key);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat('zh-CN', {
      timeZone: key,
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
  const values = Object.fromEntries(
    parts.map((part) => [part.type, part.value]),
  );
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second}`;
}

function validDate(date: Date): boolean {
  return Number.isFinite(date.getTime());
}

function parseInstant(value: InstantValue): Date | null {
  if (!value) return null;
  if (value instanceof Date) {
    return validDate(value) ? value : null;
  }
  const trimmed = value.trim();
  if (!trimmed || !zonedIsoPattern.test(trimmed)) {
    return null;
  }
  const date = new Date(trimmed);
  return validDate(date) ? date : null;
}

function parseLocalCalendarDateTime(value: string): Date | null {
  const match = localDateTimePattern.exec(value.trim());
  if (!match) return null;
  const [, year, month, day, hour, minute, second = '0', millis = '0'] = match;
  const date = new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second),
    Number(millis.padEnd(3, '0')),
  );
  return validDate(date) ? date : null;
}

/**
 * Element Plus 日期选择器等本地日历输入转 UTC Instant ISO 字符串。
 */
export function toInstantIso(value: InstantValue): string | undefined {
  if (!value) return undefined;
  if (value instanceof Date) {
    return validDate(value) ? value.toISOString() : undefined;
  }
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const zoned = parseInstant(trimmed);
  if (zoned) return zoned.toISOString();
  const local = parseLocalCalendarDateTime(trimmed);
  return local ? local.toISOString() : undefined;
}

/**
 * 将 ISO Instant 格式化为 yyyy-MM-dd HH:mm:ss。
 */
export function formatDateTime(
  value?: InstantValue,
  placeholder = '-',
  timeZone = getClientTimeZone(),
): string {
  const date = parseInstant(value);
  return date
    ? normalizeParts(getFormatter(timeZone).formatToParts(date))
    : placeholder;
}

/**
 * 格式化为日期部分 yyyy-MM-dd。
 */
export function formatDate(value?: InstantValue, placeholder = '-'): string {
  const formatted = formatDateTime(value, placeholder);
  return formatted === placeholder ? placeholder : formatted.slice(0, 10);
}

/**
 * 相对时间（如 "3分钟前"）。
 */
export function formatRelativeTime(value?: InstantValue): string {
  const date = parseInstant(value);
  if (!date) return '-';
  const diff = Date.now() - date.getTime();
  const minutes = Math.floor(diff / 60_000);
  const hours = Math.floor(diff / 3_600_000);
  const days = Math.floor(diff / 86_400_000);
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 30) return `${days}天前`;
  return formatDateTime(value);
}
