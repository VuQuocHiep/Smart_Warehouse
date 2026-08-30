import { ArrowLeftOutlined, MailOutlined, PhoneOutlined, SaveOutlined, TeamOutlined, UserAddOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Form, Input, message, Select, Typography } from "antd";
import { useNavigate } from "react-router-dom";
import { createUser } from "../../api/user";

const { Text, Title } = Typography;

const roleOptions = [
    { label: "ADMIN", value: "ADMIN" },
    { label: "MANAGER", value: "MANAGER" },
    { label: "STAFF", value: "STAFF" },
];

export default function AddUser(){
    const navigate = useNavigate();
    const onFinish = async(value)=>{
        try {
            await createUser({
                ...value,
                role: Array.isArray(value.role) ? value.role : [value.role],
            });
            message.success("Tạo tài khoản thành công");
            form.resetFields();
        } catch (error) {
            message.error(error.message || "Không thể tạo tài khoản");
        }
    }
    const [form] = Form.useForm();
    return(
        <div className="admin-user-form-page">
            <div className="admin-user-form-header">
                <div>
                    <Text className="admin-user-kicker"><UserAddOutlined /> Người dùng</Text>
                    <Title level={2}>Tạo tài khoản mới</Title>
                    <Text className="admin-user-description">Nhập thông tin nhân sự và phân quyền truy cập hệ thống.</Text>
                </div>
                <Button icon={<ArrowLeftOutlined />} onClick={()=>navigate("/admin/user")} size="large">
                    Quay lại
                </Button>
            </div>

            <div className="admin-user-form-panel">
                <Form
                    form={form}
                    initialValues={{ role: ["STAFF"] }}
                    layout="vertical"
                    onFinish={onFinish}
                    requiredMark={false}
                >
                    <div className="admin-user-form-grid">
                        <Form.Item label="Email" name="email" rules={[{required:true,message:"Vui lòng nhập email"},{type:"email",message:"Email không hợp lệ"}]}>
                            <Input prefix={<MailOutlined />} placeholder="example@email.com" size="large"/>
                        </Form.Item>
                        <Form.Item label="Mật khẩu" name="password" rules={[{required:true,message:"Vui lòng nhập mật khẩu"},{min:6,message:"Mật khẩu tối thiểu 6 ký tự"}]}>
                            <Input.Password placeholder="Nhập mật khẩu" size="large"/>
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
                        <Button onClick={()=>form.resetFields()} size="large">
                            Làm mới
                        </Button>
                        <Button icon={<SaveOutlined />} type="primary" htmlType="submit" size="large">
                            Tạo tài khoản
                        </Button>
                    </div>
                </Form>
            </div>
        </div>
    );
}
