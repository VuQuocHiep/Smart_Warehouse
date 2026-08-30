import { deleteUser, getAll } from "../../api/user";
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined, TeamOutlined } from "@ant-design/icons";
import { Button, Input, message, Popconfirm, Segmented, Space, Table, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

const { Text, Title } = Typography;
const roleOptions = [
    { label: "Admin", value: "ADMIN", color: "blue" },
    { label: "Manager", value: "MANAGER", color: "green" },
    { label: "Staff", value: "STAFF", color: "orange" },
]
export default function User(){
    const [type,setType]=useState("ADMIN");
    const [keyword,setKeyword]=useState("");
    const navigate = useNavigate();
    const [data,setData]=useState([]);
    const [loading,setLoading]=useState(false);

    const getUserRoles = (user)=>{
        if (Array.isArray(user?.role)) {
            return user.role.map((role)=>role?.name || role).filter(Boolean);
        }

        if (user?.role?.name) {
            return [user.role.name];
        }

        if (typeof user?.role === "string") {
            return [user.role];
        }

        return [];
    }

    const fetchUsers = async()=>{
        setLoading(true);
        try {
            const response = await getAll();
            setData(Array.isArray(response) ? response : response?.data || []);
        } catch (error) {
            message.error(error.message || "Không thể tải danh sách người dùng");
        } finally {
            setLoading(false);
        }
    }

    useEffect(()=>{
        fetchUsers()
    },[])

    const roleStats = useMemo(()=>{
        return roleOptions.map((role)=>({
            ...role,
            count: data.filter((item)=>getUserRoles(item).includes(role.value)).length,
        }))
    },[data])

    const filteredData = useMemo(()=>{
        const searchValue = keyword.trim().toLowerCase();
        return data.filter((item)=>{
            const fullName = `${item?.lastname || ""} ${item?.firstname || ""}`.trim().toLowerCase();
            const matchesKeyword = !searchValue
                || item?.email?.toLowerCase().includes(searchValue)
                || item?.phone?.toLowerCase().includes(searchValue)
                || fullName.includes(searchValue);

            return getUserRoles(item).includes(type) && matchesKeyword;
        })
    },[data,type,keyword])
    const confirm=async(id)=>{
        try {
            await deleteUser(id);
            message.success("Xóa tài khoản thành công");
            fetchUsers();
        } catch (error) {
            message.error(error.message || "Không thể xóa tài khoản");
        }
    }
    const columns = [
        {
            title: "STT",
            width: 72,
            align: "center",
            render: (_, __, index) => index + 1,
        },
        {
            title:"Email",
            dataIndex:"email",
            render:(text)=><Text strong>{text}</Text>
        },
        {
            title:"Họ tên",
            render:(_,record)=>{
                return(
                    <Text strong>{record?.lastname} {record?.firstname}</Text>
                )
            }
        },
        {
            title:"Số điện thoại",
            dataIndex:"phone",
            render:(text)=><Text>{text || "Chưa cập nhật"}</Text>
        },
        {
            title:"Vai trò",
            dataIndex:"role",
            render:(_,record)=>(
                <Space size={6} wrap>
                    {getUserRoles(record).map((role)=>(
                        <Tag color={roleOptions.find((item)=>item.value === role)?.color || "default"} key={role}>
                            {role}
                        </Tag>
                    ))}
                </Space>
            )
        },
        {
            title:"Hoạt động",
            width: 180,
            align: "center",
            render:(_,record)=>{
                return(
                    <Space>
                        <Button icon={<EditOutlined />} onClick={()=>navigate(`/admin/updateUser/${record.userId}`)}>Cập nhật</Button>
                        <Popconfirm
                            title="Xóa tài khoản"
                            description="Bạn có chắc chắn muốn xóa tài khoản?"
                            onConfirm={()=>confirm(record.userId)}
                            okText="Xóa"
                            cancelText="Hủy"
                        >
                            <Button danger icon={<DeleteOutlined />} />
                        </Popconfirm>
                    </Space>
                );
            }
        }
    ]
    return(
        <div className="admin-user-page">
            <div className="admin-user-header">
                <div>
                    <Text className="admin-user-kicker"><TeamOutlined /> Người dùng</Text>
                    <Title level={2}>Quản lý tài khoản</Title>
                    <Text className="admin-user-description">Quản lý tài khoản theo vai trò trong hệ thống kho.</Text>
                </div>
                <Button icon={<PlusOutlined />} onClick={()=>navigate("/admin/addUser")} size="large" type="primary">
                    Tạo tài khoản
                </Button>
            </div>

            <div className="admin-user-stats">
                {roleStats.map((role)=>(
                    <button
                        className={type === role.value ? "admin-user-stat is-active" : "admin-user-stat"}
                        key={role.value}
                        onClick={()=>setType(role.value)}
                        type="button"
                    >
                        <span>{role.label}</span>
                        <strong>{role.count}</strong>
                    </button>
                ))}
            </div>

            <div className="admin-user-panel">
                <div className="admin-user-toolbar">
                    <Segmented
                        onChange={setType}
                        options={roleOptions.map((role)=>({ label: role.label, value: role.value }))}
                        value={type}
                    />
                    <Input
                        allowClear
                        className="admin-user-search"
                        onChange={(event)=>setKeyword(event.target.value)}
                        placeholder="Tìm email, họ tên, SĐT..."
                        prefix={<SearchOutlined />}
                        value={keyword}
                    />
                </div>

                <Table
                    className="admin-user-table"
                    columns={columns}
                    dataSource={filteredData}
                    rowKey="userId"
                    loading={loading}
                    pagination={{ pageSize: 8, showSizeChanger: false }}
                />
            </div>
        </div>
    )
}
