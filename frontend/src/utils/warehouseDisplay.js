export const sensorValue = (value, unit) => value == null ? '—' : `${Number(value).toFixed(['ppm', 'ADC', 'ppb'].includes(unit) ? 0 : 1)} ${unit}`

export const SENSOR_STALE_MS = 120000

export function readingStatus(reading, now) {
  if (!reading.recordedAt) return 'Chờ dữ liệu môi trường'
  return now - new Date(reading.recordedAt).getTime() > SENSOR_STALE_MS ? 'Chưa cập nhật hơn 2 phút' : 'Đang cập nhật dữ liệu'
}
