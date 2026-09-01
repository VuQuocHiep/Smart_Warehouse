import { DeleteOutlined, EditOutlined, EnvironmentOutlined, HomeOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Empty, Form, Input, message, Modal, Popconfirm, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { createWarehouse, deleteWarehouse, getAllWarehouse, updateWarehouse } from "../../api/warehouse";

const { Text, Title } = Typography;

export default function Warehouse(){
    const [form] = Form.useForm();
    const [data,setData] = useState([]);
    const [keyword,setKeyword] = useState("");
    const [loading,setLoading] = useState(false);
    const [saving,setSaving] = useState(false);
    const [modalOpen,setModalOpen] = useState(false);
    const [editing,setEditing] = useState(null);

    const fetchWarehouses = async()=>{
        setLoading(true);
        try {
            const response = await getAllWarehouse();
            setData(Array.isArray(response) ? response : response?.data || []);
        } catch (error) {
            message.error(error.message || "Không thể tải danh sách kho hàng");
        } finally {
            setLoading(false);
        }
    }

    useEffect(()=>{
        fetchWarehouses();
    },[])

    const filteredData = useMemo(()=>{
        const searchValue = keyword.trim().toLowerCase();
        if (!searchValue) {
            return data;
        }
        return data.filter((item)=>{
            return `${item?.name || ""} ${item?.address || ""} ${item?.description || ""}`.toLowerCase().includes(searchValue);
        })
    },[data,keyword])

    const openCreate = ()=>{
        setEditing(null);
        form.resetFields();
        setModalOpen(true);
    }

    const openUpdate = (record)=>{
        setEditing(record);
        form.setFieldsValue(record);
        setModalOpen(true);
    }

    const closeModal = ()=>{
        setModalOpen(false);
        setEditing(null);
        form.resetFields();
    }

    const onFinish = async(value)=>{
        setSaving(true);
        try {
            if (editing) {
                await updateWarehouse(value,editing.warehouseId);
                message.success("Cập nhật kho hàng thành công");
            } else {
                await createWarehouse(value);
                message.success("Tạo kho hàng thành công");
            }
            closeModal();
            fetchWarehouses();
        } catch (error) {
            message.error(error.message || "Không thể lưu kho hàng");
        } finally {
            setSaving(false);
        }
    }

    const handleDelete = async(id)=>{
        try {
            await deleteWarehouse(id);
            message.success("Xóa kho hàng thành công");
            fetchWarehouses();
        } catch (error) {
            message.error(error.message || "Không thể xóa kho hàng");
        }
    }

    return(
        <div className="warehouse-map-page">
            <div className="warehouse-topbar">
                <div>
                    <Text className="catalog-kicker"><HomeOutlined /> Kho hàng</Text>
                    <Title level={2}>Không gian kho</Title>
                    <Text className="catalog-description">Theo dõi địa điểm, mô tả và trạng thái các kho đang vận hành.</Text>
                </div>
                <Button icon={<PlusOutlined />} onClick={openCreate} size="large" type="primary">
                    Tạo kho hàng
                </Button>
            </div>

            <div className="warehouse-metrics">
                <div>
                    <span>Tổng kho</span>
                    <strong>{data.length}</strong>
                </div>
                <div>
                    <span>Đang hiển thị</span>
                    <strong>{filteredData.length}</strong>
                </div>
                <div>
                    <span>Có mô tả</span>
                    <strong>{data.filter((item)=>item?.description).length}</strong>
                </div>
            </div>

            <div className="warehouse-command">
                <Input
                    allowClear
                    onChange={(event)=>setKeyword(event.target.value)}
                    placeholder="Tìm tên kho, địa chỉ, mô tả..."
                    prefix={<SearchOutlined />}
                    size="large"
                    value={keyword}
                />
                <Button icon={<ReloadOutlined />} loading={loading} onClick={fetchWarehouses} size="large">
                    Tải lại
                </Button>
            </div>

            {filteredData.length ? (
                <div className="warehouse-grid">
                    {filteredData.map((item,index)=>(
                        <article className="warehouse-card" key={item.warehouseId}>
                            <div className="warehouse-card-index">{String(index + 1).padStart(2,"0")}</div>
                            <div className="warehouse-card-icon"><HomeOutlined /></div>
                            <div className="warehouse-card-content">
                                <Text className="warehouse-card-label">Warehouse</Text>
                                <h3>{item?.name}</h3>
                                <p><EnvironmentOutlined /> {item?.address}</p>
                                <span>{item?.description || "Chưa có mô tả cho kho hàng này."}</span>
                            </div>
                            <div className="warehouse-card-actions">
                                <Button icon={<EditOutlined />} onClick={()=>openUpdate(item)}>
                                    Sửa
                                </Button>
                                <Popconfirm
                                    title="Xóa kho hàng"
                                    description="Bạn có chắc chắn muốn xóa kho hàng này?"
                                    onConfirm={()=>handleDelete(item.warehouseId)}
                                    okText="Xóa"
                                    cancelText="Hủy"
                                >
                                    <Button danger icon={<DeleteOutlined />} />
                                </Popconfirm>
                            </div>
                        </article>
                    ))}
                </div>
            ) : (
                <div className="catalog-empty">
                    <Empty description="Chưa có kho hàng phù hợp" />
                </div>
            )}

            <Modal
                confirmLoading={saving}
                okText={editing ? "Lưu thay đổi" : "Tạo mới"}
                onCancel={closeModal}
                onOk={()=>form.submit()}
                open={modalOpen}
                title={editing ? "Cập nhật kho hàng" : "Tạo kho hàng"}
            >
                <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
                    <Form.Item label="Tên kho" name="name" rules={[{required:true,message:"Vui lòng nhập tên kho"}]}>
                        <Input prefix={<HomeOutlined />} placeholder="Kho trung tâm" size="large"/>
                    </Form.Item>
                    <Form.Item label="Địa chỉ" name="address" rules={[{required:true,message:"Vui lòng nhập địa chỉ"}]}>
                        <Input prefix={<EnvironmentOutlined />} placeholder="Số nhà, phường/xã, quận/huyện..." size="large"/>
                    </Form.Item>
                    <Form.Item label="Mô tả" name="description">
                        <Input.TextArea autoSize={{ minRows: 3, maxRows: 5 }} placeholder="Ghi chú thêm về kho hàng"/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}
