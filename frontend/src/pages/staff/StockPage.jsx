import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card, Input, InputNumber, Table, Tag, Typography, message } from "antd";
import { DeleteOutlined, ReloadOutlined } from "@ant-design/icons";
import ScannerCamera from "../../components/ScannerCamera";
import { productApi } from "../../api/product";
import { inventoryApi } from "../../api/inventory";

const { Text } = Typography;

// Lấy dữ liệu thật từ response (hỗ trợ cả kiểu bọc trong "data" lẫn trả thẳng)
const unwrap = (res) => {
  const body = res?.data ?? res;
  return body?.data ?? body;
};

/**
 * Trang nhập/xuất kho dùng chung.
 * type = "IN": nhập kho (+), type = "OUT": xuất kho (-)
 * Quy trình: quét SKU -> thêm vào phiếu (quét lại thì +1) -> chỉnh số lượng -> Xác nhận.
 * Bên dưới có bảng tồn kho hiện tại; khi xuất, nếu số lượng vượt tồn sẽ báo đỏ và không cho xác nhận.
 */
export default function StockPage({ type }) {
  const isIn = type === "IN";
  const title = isIn ? "Nhập kho" : "Xuất kho";

  const [items, setItems] = useState([]);
  const [status, setStatus] = useState({ type: "info", message: "Quét mã sản phẩm để thêm vào phiếu" });
  const [saving, setSaving] = useState(false);
  const [manual, setManual] = useState("");
  const [stockList, setStockList] = useState([]);
  const [loadingStock, setLoadingStock] = useState(false);

  const loadStock = useCallback(async () => {
    setLoadingStock(true);
    try {
      const list = unwrap(await inventoryApi.getAll());
      setStockList(Array.isArray(list) ? list : []);
    } catch (err) {
      setStockList([]);
    } finally {
      setLoadingStock(false);
    }
  }, []);

  useEffect(() => {
    loadStock();
  }, [loadStock]);

  // Tra nhanh tồn kho theo SKU (sản phẩm chưa từng nhập thì tồn = 0)
  const stockOf = (sku) => stockList.find((s) => s.sku === sku)?.stock ?? 0;

  const addSku = async (sku) => {
    try {
      const res = await productApi.getProductBySku(sku);
      const product = unwrap(res);
      if (!product || (!product.name && !product.sku && !product.id)) throw new Error("Không tìm thấy");

      const key = product.sku || sku;
      setItems((prev) => {
        const i = prev.findIndex((x) => x.sku === key);
        if (i >= 0) {
          const next = [...prev];
          next[i] = { ...next[i], quantity: next[i].quantity + 1 };
          return next;
        }
        return [...prev, { sku: key, name: product.name || key, quantity: 1 }];
      });
      setStatus({ type: "success", message: `Đã thêm: ${product.name || key} (${key})` });
    } catch (err) {
      setStatus({ type: "error", message: `SKU không tồn tại hoặc lỗi: ${sku}` });
    }
  };

  const setQuantity = (sku, quantity) =>
    setItems((prev) => prev.map((x) => (x.sku === sku ? { ...x, quantity: quantity || 1 } : x)));

  const removeItem = (sku) => setItems((prev) => prev.filter((x) => x.sku !== sku));

  const sendManual = () => {
    if (manual.trim()) {
      addSku(manual.trim());
      setManual("");
    }
  };

  // Khi xuất: có dòng nào vượt tồn kho không
  const overStock = !isIn && items.some((x) => x.quantity > stockOf(x.sku));

  const confirm = async () => {
    if (!items.length || overStock) return;
    setSaving(true);
    try {
      await inventoryApi.stock(type, { items: items.map(({ sku, quantity }) => ({ sku, quantity })) });
      message.success(`${title} thành công (${items.length} sản phẩm)`);
      setItems([]);
      setStatus({ type: "success", message: `${title} thành công` });
      await loadStock();
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || "Lỗi không xác định";
      setStatus({ type: "error", message: `Không lưu được phiếu: ${msg}` });
    } finally {
      setSaving(false);
    }
  };

  const totalQty = items.reduce((s, x) => s + x.quantity, 0);

  const itemColumns = [
    { title: "SKU", dataIndex: "sku", width: 100 },
    { title: "Tên sản phẩm", dataIndex: "name" },
    { title: "Tồn hiện tại", width: 110, render: (_, row) => stockOf(row.sku) },
    {
      title: "Số lượng",
      dataIndex: "quantity",
      width: 120,
      render: (q, row) => <InputNumber min={1} value={q} onChange={(v) => setQuantity(row.sku, v)} />,
    },
    {
      title: isIn ? "Sau khi nhập" : "Sau khi xuất",
      width: 130,
      render: (_, row) => {
        const after = isIn ? stockOf(row.sku) + row.quantity : stockOf(row.sku) - row.quantity;
        return after < 0 ? <Text type="danger">Vượt tồn kho</Text> : <Text strong>{after}</Text>;
      },
    },
    {
      title: "",
      width: 60,
      render: (_, row) => <Button type="text" danger icon={<DeleteOutlined />} onClick={() => removeItem(row.sku)} />,
    },
  ];

  const stockColumns = [
    { title: "SKU", dataIndex: "sku", width: 100 },
    { title: "Tên sản phẩm", dataIndex: "name" },
    {
      title: "Tồn kho",
      dataIndex: "stock",
      width: 110,
      render: (v) => <Text strong>{v}</Text>,
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card title={`${title} bằng quét mã`} extra={<Tag color={isIn ? "green" : "orange"}>{isIn ? "+ Nhập" : "- Xuất"}</Tag>}>
        <div style={{ display: "flex", flexDirection: "column", gap: 12, maxWidth: 560 }}>
          <ScannerCamera onScan={addSku} onError={(m) => setStatus({ type: "error", message: m })} />
          <Alert showIcon type={status.type} title={status.message} />
          <div style={{ display: "flex", gap: 8 }}>
            <Input
              placeholder="Nhập SKU thủ công (dự phòng)"
              value={manual}
              onChange={(e) => setManual(e.target.value)}
              onPressEnter={sendManual}
            />
            <Button onClick={sendManual}>Thêm</Button>
          </div>
        </div>
      </Card>

      <Card title={`Phiếu ${title.toLowerCase()}`}>
        <Table
          rowKey="sku"
          columns={itemColumns}
          dataSource={items}
          pagination={false}
          locale={{ emptyText: "Chưa có sản phẩm nào, hãy quét mã" }}
        />
        {overStock && (
          <Alert
            style={{ marginTop: 12 }}
            showIcon
            type="error"
            title="Có sản phẩm xuất vượt quá tồn kho. Hãy giảm số lượng hoặc xóa dòng đó."
          />
        )}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 16 }}>
          <Text strong>
            {items.length} sản phẩm, tổng số lượng: {totalQty}
          </Text>
          <div style={{ display: "flex", gap: 8 }}>
            <Button onClick={() => setItems([])} disabled={!items.length}>
              Xóa phiếu
            </Button>
            <Button type="primary" onClick={confirm} loading={saving} disabled={!items.length || overStock}>
              Xác nhận {title.toLowerCase()}
            </Button>
          </div>
        </div>
      </Card>

      <Card
        title="Tồn kho hiện tại"
        extra={
          <Button icon={<ReloadOutlined />} onClick={loadStock} loading={loadingStock}>
            Làm mới
          </Button>
        }
      >
        <Table
          rowKey="sku"
          columns={stockColumns}
          dataSource={stockList}
          pagination={false}
          size="small"
          locale={{ emptyText: "Chưa có hàng trong kho. Hãy nhập kho để bắt đầu." }}
        />
      </Card>
    </div>
  );
}