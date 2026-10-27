import { api } from './api';

export const scaleApi = {
  status: () => api.get('/api/scale'),
  capture: (sku, quantity, type) => api.post('/api/scale/capture', { sku, quantity, type }),
  command: (type, referenceGrams = 0) => api.post('/api/scale/commands', { type, referenceGrams }),
  settings: (absoluteGrams, percent) => api.put('/api/scale/settings', { absoluteGrams, percent }),
};
