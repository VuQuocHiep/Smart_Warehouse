import { useEffect, useState } from "react";
import { Form, Input, InputNumber, Modal, Select, message } from "antd";
import { createProduct } from "../api/product";

const unitOptions = ["ITEM", "BOX", "BOTTLE", "CAN", "PACK", "BAG", "KG", "G", "LITER", "ML", "PIECE"].map((u) => ({
  label: u,
  value: u,
}));

/**
 * Hộp tạo nhanh sản phẩm khi quét một mã chưa có trong mục Sản phẩm.
 * Props: open, sku (mã vừa quét), onClose(), onCreated(sku)
 */
export default function ProductQuickCreate({ open, sku, onClose, onCreated }) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({ sku, unit: "ITEM" });
    }
  }, [open, sku, form]);

  const onFinish = async (value) => {
    setSaving(true);
    try {
      await createProduct({ ...value, name: (value.name || "").trim(), sku, unit_weight: Number(value.unit_weight || 0) });
      message.success(`Đã tạo sản phẩm ${value.name} (${sku})`);
      onCreated?.(sku);
    } catch (error) {
      message.error(error?.message?.includes("400") ? "Không tạo được: tên sản phẩm đã tồn tại hoặc thông tin chưa hợp lệ" : (error?.message || "Không thể tạo sản phẩm"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="Mã này chưa có trong mục Sản phẩm. Tạo sản phẩm mới?"
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Tạo sản phẩm"
      cancelText="Bỏ qua"
      confirmLoading={saving}
    >
      <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
        <Form.Item label="Mã (SKU)" name="sku">
          <Input disabled />
        </Form.Item>
        <Form.Item label="Tên sản phẩm" name="name" rules={[{ required: true, message: "Vui lòng nhập tên sản phẩm" }]}>
          <Input placeholder="Tên sản phẩm" />
        </Form.Item>
        <Form.Item label="Danh mục" name="category" rules={[{ required: true, message: "Vui lòng nhập danh mục" }]}>
          <Input placeholder="Thực phẩm, linh kiện..." />
        </Form.Item>
        <Form.Item
          label="Trọng lượng đơn vị"
          name="unit_weight"
          rules={[
            { required: true, message: "Vui lòng nhập trọng lượng" },
            { type: "number", min: 0.001, message: "Trọng lượng phải lớn hơn 0" },
          ]}
        >
          <InputNumber step={0.01} placeholder="0.25" style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item label="Đơn vị" name="unit" rules={[{ required: true, message: "Vui lòng chọn đơn vị" }]}>
          <Select options={unitOptions} />
        </Form.Item>
      </Form>
    </Modal>
  );
}