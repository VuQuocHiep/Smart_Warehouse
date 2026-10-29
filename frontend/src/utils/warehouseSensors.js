const SAFE_WAREHOUSE_VALUES = {
  'Kho 1': { temperature: 28.6, humidity: 60.4, gas: 320 },
  'Kho 2': { temperature: 27.4, humidity: 58.5, gas: 260 },
  'Kho 3': { temperature: 28.1, humidity: 61.2, gas: 315 },
  'Kho 4': { temperature: 26.8, humidity: 55.9, gas: 230 },
}

export const WAREHOUSE_NAMES = ['Kho 1', 'Kho 2', 'Kho 3', 'Kho 4']

const getRecordedAt = (fallback) => fallback || new Date().toISOString()

const hasSensorReading = (reading) => {
  if (!reading) return false

  return ['temperature', 'humidity', 'gas'].some(key => reading[key] != null && Number.isFinite(Number(reading[key])))
}

const buildSafeReading = (name, recordedAt) => ({
  ...SAFE_WAREHOUSE_VALUES[name],
  recordedAt: getRecordedAt(recordedAt),
  status: 'NORMAL',
  warehouse: name,
  simulated: true,
})

const buildGeneratedHistory = (name, latest) => {
  const base = SAFE_WAREHOUSE_VALUES[name]
  const now = latest?.recordedAt ? new Date(latest.recordedAt) : new Date()

  return Array.from({ length: 30 }, (_, index) => {
    const distance = 29 - index
    const wave = Math.sin(index / 3)

    return {
      temperature: Number((base.temperature + wave * 0.35).toFixed(1)),
      humidity: Number((base.humidity + Math.cos(index / 4) * 1.2).toFixed(1)),
      gas: Math.round(base.gas + wave * 18),
      recordedAt: new Date(now.getTime() - distance * 60000).toISOString(),
      status: 'NORMAL',
      warehouse: name,
    }
  })
}

export function getWarehouseReading(name, latest) {
  const markFreshness = (reading) => {
    // Backend monitoring timestamps use Vietnam local time; explicit offsets also work.
    const recordedAt = reading.recordedAt && (/([zZ]|[+-]\d\d:\d\d)$/.test(reading.recordedAt) ? reading.recordedAt : `${reading.recordedAt}+07:00`)
    return { ...reading, recordedAt, stale: reading.simulated !== true && (!recordedAt || !Number.isFinite(Date.parse(recordedAt)) || Date.now() - Date.parse(recordedAt) > 120000) }
  }
  if (latest?.warehouseReadings?.[name]) return markFreshness({ ...latest.warehouseReadings[name], warehouse: name })
  if (name === 'Kho 1' && hasSensorReading(latest)) {
    return markFreshness({
      ...latest,
      warehouse: name,
      simulated: false,
    })
  }

  if (name === 'Kho 1') return { temperature: null, humidity: null, gas: null, recordedAt: null, warehouse: name, simulated: false }
  return buildSafeReading(name)
}

export function getWarehouseHistory(name, latest, history) {
  if (name === 'Kho 1' && Array.isArray(history) && history.length) {
    return history
  }

  if (name === 'Kho 1') return []
  return buildGeneratedHistory(name, null)
}
