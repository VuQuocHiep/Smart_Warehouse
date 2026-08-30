import {api} from "../api/api"
export const userApi = {
    createUser:(data)=>{
        return api.post("/user/create",data)
    },
    updateUser:(data,id)=>{
        return api.patch(`/user/update/${encodeURIComponent(id)}`,data)
    },
    deleteUser:(id)=>{
        return api.patch(`/user/delete/${encodeURIComponent(id)}`)
    },
    getAll:()=>{
        return api.get("/user/getAll")
    },
    getUserById:(id)=>{
        return api.get(`/user/getUserById/${encodeURIComponent(id)}`)
    }
}
export const createUser=userApi.createUser;
export const updateUser=userApi.updateUser;
export const deleteUser=userApi.deleteUser;
export const getAll=userApi.getAll;
export const getUserById=userApi.getUserById;
