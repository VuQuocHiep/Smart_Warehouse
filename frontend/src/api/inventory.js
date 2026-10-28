import { api } from "./api"

export const inventoryApi = {
    warehouseStock: () => api.get('/inventory/warehouse-stock'),
    warehouses: () => api.get('/inventory/warehouses'),
    stock: (type, data) => {
        return api.post(type === "IN" ? "/inventory/stock-in" : "/inventory/stock-out", data)
    },
    getAll: () => {
        return api.get("/inventory/getAll")
    },
    getHistory: () => {
        return api.get("/inventory/history")
    },
    getUsedSkus: () => {
        return api.get("/inventory/used-skus")
    },
    getPlacements: (data) => {
        return api.post("/inventory/placement-suggestions", data)
    },
}
