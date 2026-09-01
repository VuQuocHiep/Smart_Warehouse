import { api } from "../api/api"

export const productApi = {
    createProduct:(data)=>{
        return api.post("/product/create",data)
    },
    updateProduct:(data,id)=>{
        return api.patch(`/product/update/${encodeURIComponent(id)}`,data)
    },
    deleteProduct:(id)=>{
        return api.patch(`/product/delete/${encodeURIComponent(id)}`)
    },
    getAllProduct:()=>{
        return api.get("/product/getAll")
    },
    getProductById:(id)=>{
        return api.get(`/product/getProductById/${encodeURIComponent(id)}`)
    },
    getProductByName:(name)=>{
        return api.get(`/product/getProductByName/${encodeURIComponent(name)}`)
    },
    getProductBySku:(sku)=>{
        return api.get(`/product/getProductBySku/${encodeURIComponent(sku)}`)
    },
}

export const createProduct=productApi.createProduct;
export const updateProduct=productApi.updateProduct;
export const deleteProduct=productApi.deleteProduct;
export const getAllProduct=productApi.getAllProduct;
export const getProductById=productApi.getProductById;
export const getProductByName=productApi.getProductByName;
export const getProductBySku=productApi.getProductBySku;
