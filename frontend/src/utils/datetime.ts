const formatterCache = new Map<string, Intl.DateTimeFormat>()

function getFormatter(timeZone?: string) {
  const key = timeZone || 'local'
  let formatter = formatterCache.get(key)
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
    })
    formatterCache.set(key, formatter)
  }
  return formatter
}

function normalizeParts(parts: Intl.DateTimeFormatPart[]) {
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second}`
}

export function formatDateTime(value?: number | null, placeholder = '-', timeZone?: string) {
  if (value == null || !Number.isFinite(value)) {
    return placeholder
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return placeholder
  }
  return normalizeParts(getFormatter(timeZone).formatToParts(date))
}

export function toDate(value?: number | null) {
  if (value == null || !Number.isFinite(value)) {
    return null
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function toEpochMs(value?: Date | null) {
  if (!(value instanceof Date)) {
    return null
  }
  const epochMs = value.getTime()
  return Number.isFinite(epochMs) ? epochMs : null
}
