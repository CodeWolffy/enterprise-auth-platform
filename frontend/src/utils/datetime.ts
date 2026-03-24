function pad(value: number) {
  return String(value).padStart(2, '0')
}

function parseDateTime(raw: string) {
  const normalized = raw.trim().replace(' ', 'T')
  const parsed = new Date(normalized)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed
  }
  return null
}

export function formatDateTime(value?: string | null, placeholder = '-') {
  if (!value) {
    return placeholder
  }
  const date = parseDateTime(value)
  if (!date) {
    return value
  }
  const year = date.getFullYear()
  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  const hour = pad(date.getHours())
  const minute = pad(date.getMinutes())
  const second = pad(date.getSeconds())
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}
