const dateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
})

export function formatDate(value) {
  if (!value) return '—'
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value))
  return match ? `${match[3]}/${match[2]}` : '—'
}

export function formatDateTime(value) {
  if (!value) return '—'
  const date = Array.isArray(value)
    ? new Date(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0)
    : new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const parts = Object.fromEntries(dateTimeFormatter.formatToParts(date).map(({ type, value: part }) => [type, part]))
  return `${parts.day}/${parts.month} ${parts.hour}:${parts.minute}`
}
