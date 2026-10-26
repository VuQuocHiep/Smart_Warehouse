import { api } from './api'

export const demandApi = {
  catalog: () => api.get('/api/demand/catalog'),
  latest: () => api.get('/api/demand/forecasts'),
  startDemo: () => api.post('/api/demand/demo-jobs'),
  job: (id) => api.get(`/api/demand/jobs/${encodeURIComponent(id)}`),
}
