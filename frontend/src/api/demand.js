import { api } from './api'

export const demandApi = {
  catalog: () => api.get('/api/demand/catalog'),
  latest: () => api.get('/api/demand/forecasts'),
  startForecast: () => api.post('/api/demand/forecast-jobs'),
  replenishment: () => api.get('/api/demand/replenishment'),
  status: () => api.get('/api/demand/status'),
  job: (id) => api.get(`/api/demand/jobs/${encodeURIComponent(id)}`),
}
