import { BarcodeOutlined, DeleteOutlined, EditOutlined, LinkOutlined, PlusOutlined, ProductOutlined, ReloadOutlined, ScanOutlined, SearchOutlined, TagsOutlined, ThunderboltOutlined } from "@ant-design/icons";
import { Button, Empty, Form, Image, Input, InputNumber, message, Modal, Popconfirm, Select, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { createProduct, deleteProduct, getAllProduct, updateProduct } from "../../api/product";
import QrLabel from "../../components/QrLabel";
import ScannerCamera from "../../components/ScannerCamera";
import { inventoryApi } from "../../api/inventory";

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
    const [scanOpen,setScanOpen] = useState(false);
    const [usedSkus,setUsedSkus] = useState([]);
    const [stockMap,setStockMap] = useState({});
        // Lấy tồn kho từng SKU và danh sách SKU đã có giao dịch (để khóa SKU, chặn xóa)
    const fetchInventoryInfo = async()=>{
        try {
            const stockRes = await inventoryApi.getAll();
            const stockBody = stockRes?.data ?? stockRes;
            const stockList = Array.isArray(stockBody) ? stockBody : stockBody?.data;
            const map = {};
            (Array.isArray(stockList) ? stockList : []).forEach((s)=>{ map[s.sku] = s.stock; });
            setStockMap(map);
        } catch (error) {
            setStockMap({});
        }
        try {
            const usedRes = await inventoryApi.getUsedSkus();
            const usedBody = usedRes?.data ?? usedRes;
            const usedList = Array.isArray(usedBody) ? usedBody : usedBody?.data;
            setUsedSkus(Array.isArray(usedList) ? usedList : []);
        } catch (error) {
            setUsedSkus([]);
        }
    }
    const fetchProducts = async()=>{
        setLoading(true);
        fetchInventoryInfo();
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

    // Mã tự tạo: SP + số thứ tự tiếp theo (SP001, SP002...) dựa trên các mã SPxxx đã có
    const nextSku = ()=>{
        const numbers = data
            .map((item)=>/^SP(\d+)$/i.exec(item?.sku || ""))
            .filter(Boolean)
            .map((match)=>parseInt(match[1],10));
        const next = (numbers.length ? Math.max(...numbers) : 0) + 1;
        return `SP${String(next).padStart(3,"0")}`;
    }
        // Sản phẩm đã có giao dịch nhập/xuất thì không cho đổi SKU
    const skuLocked = !!editing && usedSkus.includes(editing.sku);
    const openCreate = ()=>{
        setEditing(null);
        form.resetFields();
        form.setFieldsValue({ unit:"ITEM" });
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
                sku:(value.sku || "").trim(),
                name:(value.name || "").trim(),
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
                        message.error(error.message?.includes("400") ? "Dữ liệu không hợp lệ hoặc SKU/tên sản phẩm đã tồn tại" : (error.message || "Không thể lưu sản phẩm"));
        } finally {
            setSaving(false);
        }
    }

        const handleDelete = async(item)=>{
        const stock = stockMap[item.sku] ?? 0;
        if (stock > 0) {
            message.warning(`Không thể xóa: sản phẩm ${item.sku} còn ${stock} trong kho. Hãy xuất hết hàng trước.`);
            return;
        }
        try {
            await deleteProduct(item.productId);
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
                                    <span>Bảo quản: {item.minTemperature ?? '…'} – {item.maxTemperature ?? '…'} °C · {item.minHumidity ?? '…'} – {item.maxHumidity ?? '…'} %</span>
                                </div>
                                <div className="product-card-meta">
                                    <span>Trọng lượng</span>
                                    <strong>{item?.unit_weight || 0}</strong>
                                </div>
                                <div className="product-card-actions">
                                    <QrLabel sku={item?.sku} name={item?.name} />
                                    <Button icon={<EditOutlined />} onClick={()=>openUpdate(item)}>
                                        Sửa
                                    </Button>
                                    <Popconfirm
                                        title="Xóa sản phẩm"
                                        description="Bạn có chắc chắn muốn xóa sản phẩm này?"
                                        onConfirm={()=>handleDelete(item)}
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
                                                <Form.Item
                            label="SKU"
                            name="sku"
                            extra={skuLocked ? "Không thể đổi SKU vì sản phẩm đã có giao dịch nhập/xuất" : undefined}
                            rules={[
                                {required:true,message:"Vui lòng nhập SKU"},
                                {
                                    validator:(_,value)=>{
                                        const v = (value || "").trim().toLowerCase();
                                        const duplicated = data.some((p)=>p?.sku?.trim().toLowerCase() === v && p.productId !== editing?.productId);
                                        return duplicated ? Promise.reject(new Error("SKU này đã tồn tại")) : Promise.resolve();
                                    },
                                },
                            ]}
                        >
                            <Input
                                disabled={skuLocked}
                                prefix={<BarcodeOutlined />}
                                placeholder="SP001"
                                size="large"
                                suffix={
                                    <>
                                        <Button disabled={skuLocked} size="small" type="text" icon={<ScanOutlined />} onClick={()=>setScanOpen(true)}>
                                            Quét
                                        </Button>
                                        <Button disabled={skuLocked} size="small" type="text" icon={<ThunderboltOutlined />} onClick={()=>form.setFieldValue("sku",nextSku())}>
                                            Tự tạo
                                        </Button>
                                    </>
                                }
                            />
                        </Form.Item>
                        <Form.Item
    label="Tên sản phẩm"
    name="name"
    rules={[
        {required:true,message:"Vui lòng nhập tên sản phẩm"},
        {
            validator:(_,value)=>{
                const v = (value || "").trim().toLowerCase();
                const duplicated = data.some((p)=>p?.name?.trim().toLowerCase() === v && p.productId !== editing?.productId);
                return duplicated ? Promise.reject(new Error("Tên sản phẩm này đã tồn tại")) : Promise.resolve();
            },
        },
    ]}
>
                            <Input prefix={<ProductOutlined />} placeholder="Tên sản phẩm" size="large"/>
                        </Form.Item>
                        <Form.Item label="Danh mục" name="category" rules={[{required:true,message:"Vui lòng nhập danh mục"}]}>
                            <Input prefix={<TagsOutlined />} placeholder="Thực phẩm, linh kiện..." size="large"/>
                        </Form.Item>
                        <Form.Item label="Ảnh sản phẩm" name="image_url">
                            <Input prefix={<LinkOutlined />} placeholder="https://..." size="large"/>
                        </Form.Item>
                        <Form.Item label="Trọng lượng đơn vị" name="unit_weight" rules={[{required:true,message:"Vui lòng nhập trọng lượng"},{type:"number",min:0.001,message:"Trọng lượng phải lớn hơn 0"}]}>
                        <InputNumber className="catalog-full-input" step={0.01} placeholder="0.25" size="large"/>
                        </Form.Item>
                        <Form.Item label="Đơn vị" name="unit" rules={[{required:true,message:"Vui lòng chọn đơn vị"}]}>
                            <Select options={unitOptions} placeholder="Chọn đơn vị" size="large"/>
                        </Form.Item>
                    </div>
                    <Typography.Paragraph type="secondary">Ngưỡng bảo quản riêng của sản phẩm. Để trống nếu không cần giám sát thông số đó.</Typography.Paragraph>
                    <div className="catalog-form-grid">
                        {[
                            ['minTemperature', 'Nhiệt độ tối thiểu (°C)', -100, 200],
                            ['maxTemperature', 'Nhiệt độ tối đa (°C)', -100, 200],
                            ['minHumidity', 'Độ ẩm tối thiểu (%)', 0, 100],
                            ['maxHumidity', 'Độ ẩm tối đa (%)', 0, 100],
                        ].map(([field, label, min, max]) => <Form.Item key={field} name={field} label={label}
                            dependencies={['minTemperature', 'maxTemperature', 'minHumidity', 'maxHumidity'].filter(other => other !== field)}
                            rules={[({ getFieldValue }) => ({ validator(_, value) {
                                const low = getFieldValue(field.includes('Temperature') ? 'minTemperature' : 'minHumidity');
                                const high = getFieldValue(field.includes('Temperature') ? 'maxTemperature' : 'maxHumidity');
                                if (value != null && (value < min || value > max)) return Promise.reject(new Error(`Giá trị phải từ ${min} đến ${max}`));
                                return low != null && high != null && low > high
                                    ? Promise.reject(new Error('Ngưỡng tối thiểu phải nhỏ hơn hoặc bằng tối đa')) : Promise.resolve();
                            } })]}>
                            <InputNumber min={min} max={max} precision={2} style={{ width: '100%' }} placeholder="Chưa cấu hình" />
                        </Form.Item>)}
                    </div>
                    <Form.Item label="Mô tả" name="description">
                        <Input.TextArea autoSize={{ minRows: 3, maxRows: 5 }} placeholder="Ghi chú thêm về sản phẩm"/>
                    </Form.Item>
                </Form>
            </Modal>

            <Modal
                footer={null}
                onCancel={()=>setScanOpen(false)}
                open={scanOpen}
                title="Quét mã để điền SKU"
                width={560}
            >
                {scanOpen && (
                    <ScannerCamera
                        onScan={(code)=>{
                            form.setFieldValue("sku",code);
                            setScanOpen(false);
                            message.success(`Đã điền mã: ${code}`);
                        }}
                        onError={(text)=>message.error(text)}
                    />
                )}
            </Modal>
        </div>
    )
}