import { api } from './api'

export const shelfApi = {
  list: () => api.get('/api/shelves'),
  save: (data, id) => id
    ? api.put(`/api/shelves/${encodeURIComponent(id)}`, data)
    : api.post('/api/shelves', data),
  alerts: () => api.get('/api/product-alerts'),
  markRead: (id) => api.put(`/api/product-alerts/${encodeURIComponent(id)}/read`),
}
