import { ArrowLeftOutlined, MailOutlined, PhoneOutlined, SaveOutlined, TeamOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Form, Input, message, Select, Spin, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getUserById, updateUser } from "../../api/user";

const { Text, Title } = Typography;

const roleOptions = [
    { label: "ADMIN", value: "ADMIN" },
    { label: "MANAGER", value: "MANAGER" },
    { label: "STAFF", value: "STAFF" },
];

const getRoleNames = (user)=>{
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

export default function UpdateUser(){
    const [form] = Form.useForm();
    const { id } = useParams();
    const navigate = useNavigate();
    const [loading,setLoading] = useState(false);
    const [saving,setSaving] = useState(false);
    const [user,setUser] = useState(null);
    const displayName = useMemo(()=>{
        return `${user?.lastname || ""} ${user?.firstname || ""}`.trim() || user?.email || "Tài khoản";
    },[user])
    useEffect(()=>{
        const fetchUser = async()=>{
            setLoading(true);
            try {
                const data = await getUserById(id);
                setUser(data);
                form.setFieldsValue({
                    email: data?.email,
                    firstname: data?.firstname,
                    lastname: data?.lastname,
                    phone: data?.phone,
                    role: getRoleNames(data),
                });
            } catch (error) {
                message.error(error.message || "Không thể tải thông tin người dùng");
            } finally {
                setLoading(false);
            }
        }

        if (id) {
            fetchUser();
        }
    },[form,id])

    const onFinish = async(value)=>{
        setSaving(true);
        try {
            const payload = {
                ...value,
                role: Array.isArray(value.role) ? value.role : [value.role],
            };

            if (!payload.password) {
                delete payload.password;
            }

            await updateUser(payload,id);
            message.success("Cập nhật tài khoản thành công");
            navigate("/admin/user");
        } catch (error) {
            message.error(error.message || "Không thể cập nhật tài khoản");
        } finally {
            setSaving(false);
        }
    }
    return(
        <div className="admin-user-form-page">
            <div className="admin-user-form-header">
                <div>
                    <Text className="admin-user-kicker"><UserOutlined /> Người dùng</Text>
                    <Title level={2}>Cập nhật tài khoản</Title>
                    <Text className="admin-user-description">Chỉnh sửa thông tin hiện có của {displayName}.</Text>
                </div>
                <Button icon={<ArrowLeftOutlined />} onClick={()=>navigate("/admin/user")} size="large">
                    Quay lại
                </Button>
            </div>

            <div className="admin-user-form-panel">
                <Spin spinning={loading}>
                    <Form
                        form={form}
                        layout="vertical"
                        onFinish={onFinish}
                        requiredMark={false}
                    >
                        <div className="admin-user-form-grid">
                            <Form.Item label="Email" name="email" rules={[{required:true,message:"Vui lòng nhập email"},{type:"email",message:"Email không hợp lệ"}]}>
                                <Input prefix={<MailOutlined />} placeholder="example@email.com" size="large"/>
                            </Form.Item>
                            <Form.Item label="Mật khẩu mới" name="password" rules={[{min:6,message:"Mật khẩu tối thiểu 6 ký tự"}]}>
                                <Input.Password placeholder="Để trống nếu không đổi mật khẩu" size="large"/>
                            </Form.Item>
                            <Form.Item label="Họ" name="lastname" rules={[{required:true,message:"Vui lòng nhập họ"}]}>
                                <Input prefix={<UserOutlined />} placeholder="Nguyễn" size="large"/>
                            </Form.Item>
                            <Form.Item label="Tên" name="firstname" rules={[{required:true,message:"Vui lòng nhập tên"}]}>
                                <Input prefix={<UserOutlined />} placeholder="An" size="large"/>
                            </Form.Item>
                            <Form.Item label="Số điện thoại" name="phone" rules={[{required:true,message:"Vui lòng nhập số điện thoại"},{pattern:/^0[0-9]{9}$/,message:"Số điện thoại phải gồm 10 số và bắt đầu bằng 0"}]}>
                                <Input prefix={<PhoneOutlined />} placeholder="09xxxxxxxx" size="large"/>
                            </Form.Item>
                            <Form.Item label="Vai trò" name="role" rules={[{required:true,message:"Vui lòng chọn vai trò"}]}>
                                <Select mode="multiple" options={roleOptions} placeholder="Chọn vai trò" size="large" suffixIcon={<TeamOutlined />}/>
                            </Form.Item>
                        </div>

                        <div className="admin-user-form-actions">
                            <Button onClick={()=>navigate("/admin/user")} size="large">
                                Hủy
                            </Button>
                            <Button icon={<SaveOutlined />} loading={saving} type="primary" htmlType="submit" size="large">
                                Lưu thay đổi
                            </Button>
                        </div>
                    </Form>
                </Spin>
            </div>
        </div>
    )
}
