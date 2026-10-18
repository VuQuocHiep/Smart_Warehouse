import { api } from './api.js'

export const sensorApi = {
  latest: async () => {
    try {
      const overview = await api.get('/api/warehouses/monitoring')
      const real = overview.find(item => item.warehouse.monitoringSlot === 1)?.reading
      return {
        ...(real || {}),
        warehouseReadings: Object.fromEntries(overview.map(item => [item.warehouse.name, item.reading])),
      }
    } catch {
      return null
    }
  },
  history: async (limit = 60) => {
    try {
      const response = await api.get(`/api/sensor/history?limit=${limit}`)

      return Array.isArray(response) ? response : response?.data || []
    } catch {
      return []
    }
  },
}
