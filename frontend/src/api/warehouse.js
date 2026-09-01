import { api } from "../api/api"

export const warehouseApi = {
    createWarehouse:(data)=>{
        return api.post("/warehouse/create",data)
    },
    updateWarehouse:(data,id)=>{
        return api.patch(`/warehouse/update/${encodeURIComponent(id)}`,data)
    },
    deleteWarehouse:(id)=>{
        return api.patch(`/warehouse/delete/${encodeURIComponent(id)}`)
    },
    getAllWarehouse:()=>{
        return api.get("/warehouse/getAll")
    },
    getWarehouseById:(id)=>{
        return api.get(`/warehouse/getWarehouseById/${encodeURIComponent(id)}`)
    },
    getWarehouseByName:(name)=>{
        return api.get(`/warehouse/getWarehouseByName/${encodeURIComponent(name)}`)
    },
}

export const createWarehouse=warehouseApi.createWarehouse;
export const updateWarehouse=warehouseApi.updateWarehouse;
export const deleteWarehouse=warehouseApi.deleteWarehouse;
export const getAllWarehouse=warehouseApi.getAllWarehouse;
export const getWarehouseById=warehouseApi.getWarehouseById;
export const getWarehouseByName=warehouseApi.getWarehouseByName;
