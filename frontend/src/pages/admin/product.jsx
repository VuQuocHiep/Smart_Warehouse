import { BarcodeOutlined, DeleteOutlined, EditOutlined, LinkOutlined, PlusOutlined, ProductOutlined, ReloadOutlined, SearchOutlined, TagsOutlined } from "@ant-design/icons";
import { Button, Empty, Form, Image, Input, InputNumber, message, Modal, Popconfirm, Select, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { createProduct, deleteProduct, getAllProduct, updateProduct } from "../../api/product";

const { Text, Title } = Typography;

const unitOptions = [
    { label:"ITEM", value:"ITEM" },
    { label:"BOX", value:"BOX" },
    { label:"BOTTLE", value:"BOTTLE" },
    { label:"CAN", value:"CAN" },
    { label:"PACK", value:"PACK" },
    { label:"BAG", value:"BAG" },
    { label:"KG", value:"KG" },
    { label:"G", value:"G" },
    { label:"LITER", value:"LITER" },
    { label:"ML", value:"ML" },
    { label:"PIECE", value:"PIECE" },
];

export default function Product(){
    const [form] = Form.useForm();
    const [data,setData] = useState([]);
    const [keyword,setKeyword] = useState("");
    const [category,setCategory] = useState("ALL");
    const [loading,setLoading] = useState(false);
    const [saving,setSaving] = useState(false);
    const [modalOpen,setModalOpen] = useState(false);
    const [editing,setEditing] = useState(null);

    const fetchProducts = async()=>{
        setLoading(true);
        try {
            const response = await getAllProduct();
            setData(Array.isArray(response) ? response : response?.data || []);
        } catch (error) {
            message.error(error.message || "Không thể tải danh sách sản phẩm");
        } finally {
            setLoading(false);
        }
    }

    useEffect(()=>{
        fetchProducts();
    },[])

    const categoryOptions = useMemo(()=>{
        const categories = [...new Set(data.map((item)=>item?.category).filter(Boolean))];
        return [
            { label:"Tất cả danh mục", value:"ALL" },
            ...categories.map((item)=>({ label:item, value:item })),
        ];
    },[data])

    const filteredData = useMemo(()=>{
        const searchValue = keyword.trim().toLowerCase();
        return data.filter((item)=>{
            const matchesCategory = category === "ALL" || item?.category === category;
            const matchesKeyword = !searchValue
                || `${item?.sku || ""} ${item?.name || ""} ${item?.category || ""} ${item?.description || ""}`.toLowerCase().includes(searchValue);
            return matchesCategory && matchesKeyword;
        })
    },[category,data,keyword])

    const openCreate = ()=>{
        setEditing(null);
        form.resetFields();
        form.setFieldsValue({ unit:"ITEM", unit_weight:0 });
        setModalOpen(true);
    }

    const openUpdate = (record)=>{
        setEditing(record);
        form.setFieldsValue({
            unit:"ITEM",
            unit_weight:0,
            ...record,
        });
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
            const payload = {
                ...value,
                unit_weight:Number(value.unit_weight || 0),
            };
            if (editing) {
                await updateProduct(payload,editing.productId);
                message.success("Cập nhật sản phẩm thành công");
            } else {
                await createProduct(payload);
                message.success("Tạo sản phẩm thành công");
            }
            closeModal();
            fetchProducts();
        } catch (error) {
            message.error(error.message || "Không thể lưu sản phẩm");
        } finally {
            setSaving(false);
        }
    }

    const handleDelete = async(id)=>{
        try {
            await deleteProduct(id);
            message.success("Xóa sản phẩm thành công");
            fetchProducts();
        } catch (error) {
            message.error(error.message || "Không thể xóa sản phẩm");
        }
    }

    return(
        <div className="product-catalog-page">
            <div className="catalog-header">
                <div>
                    <Text className="catalog-kicker"><ProductOutlined /> Sản phẩm</Text>
                    <Title level={2}>Danh mục sản phẩm</Title>
                    <Text className="catalog-description">Quản lý SKU, hình ảnh, danh mục và quy cách hàng hóa.</Text>
                </div>
                <Button icon={<PlusOutlined />} onClick={openCreate} size="large" type="primary">
                    Tạo sản phẩm
                </Button>
            </div>

            <div className="catalog-toolbar">
                <Input
                    allowClear
                    className="catalog-search"
                    onChange={(event)=>setKeyword(event.target.value)}
                    placeholder="Tìm SKU, tên sản phẩm, danh mục..."
                    prefix={<SearchOutlined />}
                    size="large"
                    value={keyword}
                />
                <Select
                    className="catalog-filter"
                    onChange={setCategory}
                    options={categoryOptions}
                    size="large"
                    value={category}
                />
                <Button icon={<ReloadOutlined />} loading={loading} onClick={fetchProducts} size="large">
                    Tải lại
                </Button>
            </div>

            {filteredData.length ? (
                <div className="product-grid">
                    {filteredData.map((item)=>(
                        <article className="product-card" key={item.productId}>
                            <div className="product-card-media">
                                <Image
                                    fallback="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='420' height='280'%3E%3Crect width='420' height='280' fill='%23eef2f7'/%3E%3Cpath d='M171 169h78v-58h-78v58Zm12-46h54v34h-54v-34Z' fill='%2394a3b8'/%3E%3Ctext x='210' y='205' text-anchor='middle' fill='%2364758b' font-size='18' font-family='Arial'%3ENo image%3C/text%3E%3C/svg%3E"
                                    preview={false}
                                    src={item?.image_url}
                                />
                                <Tag className="product-card-sku" color="blue">{item?.sku}</Tag>
                            </div>
                            <div className="product-card-body">
                                <div className="product-card-title-row">
                                    <div>
                                        <Text className="product-card-category">{item?.category || "Chưa phân loại"}</Text>
                                        <h3>{item?.name}</h3>
                                    </div>
                                    <Tag>{item?.unit || "ITEM"}</Tag>
                                </div>
                                <p>{item?.description || "Chưa có mô tả cho sản phẩm này."}</p>
                                <div className="product-card-meta">
                                    <span>Trọng lượng</span>
                                    <strong>{item?.unit_weight || 0}</strong>
                                </div>
                                <div className="product-card-actions">
                                    <Button icon={<EditOutlined />} onClick={()=>openUpdate(item)}>
                                        Sửa
                                    </Button>
                                    <Popconfirm
                                        title="Xóa sản phẩm"
                                        description="Bạn có chắc chắn muốn xóa sản phẩm này?"
                                        onConfirm={()=>handleDelete(item.productId)}
                                        okText="Xóa"
                                        cancelText="Hủy"
                                    >
                                        <Button danger icon={<DeleteOutlined />} />
                                    </Popconfirm>
                                </div>
                            </div>
                        </article>
                    ))}
                </div>
            ) : (
                <div className="catalog-empty">
                    <Empty description="Chưa có sản phẩm phù hợp" />
                </div>
            )}

            <Modal
                confirmLoading={saving}
                okText={editing ? "Lưu thay đổi" : "Tạo mới"}
                onCancel={closeModal}
                onOk={()=>form.submit()}
                open={modalOpen}
                title={editing ? "Cập nhật sản phẩm" : "Tạo sản phẩm"}
                width={720}
            >
                <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
                    <div className="catalog-form-grid">
                        <Form.Item label="SKU" name="sku" rules={[{required:true,message:"Vui lòng nhập SKU"}]}>
                            <Input prefix={<BarcodeOutlined />} placeholder="SKU-001" size="large"/>
                        </Form.Item>
                        <Form.Item label="Tên sản phẩm" name="name" rules={[{required:true,message:"Vui lòng nhập tên sản phẩm"}]}>
                            <Input prefix={<ProductOutlined />} placeholder="Tên sản phẩm" size="large"/>
                        </Form.Item>
                        <Form.Item label="Danh mục" name="category" rules={[{required:true,message:"Vui lòng nhập danh mục"}]}>
                            <Input prefix={<TagsOutlined />} placeholder="Thực phẩm, linh kiện..." size="large"/>
                        </Form.Item>
                        <Form.Item label="Ảnh sản phẩm" name="image_url">
                            <Input prefix={<LinkOutlined />} placeholder="https://..." size="large"/>
                        </Form.Item>
                        <Form.Item label="Trọng lượng đơn vị" name="unit_weight" rules={[{required:true,message:"Vui lòng nhập trọng lượng"},{type:"number",min:0,message:"Trọng lượng không được âm"}]}>
                            <InputNumber className="catalog-full-input" min={0} placeholder="0" size="large"/>
                        </Form.Item>
                        <Form.Item label="Đơn vị" name="unit" rules={[{required:true,message:"Vui lòng chọn đơn vị"}]}>
                            <Select options={unitOptions} placeholder="Chọn đơn vị" size="large"/>
                        </Form.Item>
                    </div>
                    <Form.Item label="Mô tả" name="description">
                        <Input.TextArea autoSize={{ minRows: 3, maxRows: 5 }} placeholder="Ghi chú thêm về sản phẩm"/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}
