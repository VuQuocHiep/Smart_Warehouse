import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { Alert, Button, Card, Input, InputNumber, Table, Tag, Typography, message } from "antd";
import { DeleteOutlined, ReloadOutlined } from "@ant-design/icons";
import ScannerCamera from "../../components/ScannerCamera";
import ProductQuickCreate from "../../components/ProductQuickCreate";
import { productApi } from "../../api/product";
import { inventoryApi } from "../../api/inventory";

const { Text } = Typography;

// Tồn kho từ mức này trở xuống (nhưng còn > 0) được coi là "Sắp hết"
const LOW_STOCK = 5;

// Lấy dữ liệu thật từ response (hỗ trợ cả kiểu bọc trong "data" lẫn trả thẳng)
const unwrap = (res) => {
  const body = res?.data ?? res;
  return body?.data ?? body;
};

// Backend có thể trả thời gian dạng chuỗi ISO hoặc mảng [năm, tháng, ngày, giờ, phút, giây]
const formatTime = (t) => {
  if (!t) return "";
  const d = Array.isArray(t) ? new Date(t[0], t[1] - 1, t[2], t[3] || 0, t[4] || 0, t[5] || 0) : new Date(t);
  return Number.isNaN(d.getTime()) ? String(t) : d.toLocaleString("vi-VN");
};

const stockStatus = (stock) => {
  if (stock <= 0) return <Tag color="red">Hết hàng</Tag>;
  if (stock <= LOW_STOCK) return <Tag color="orange">Sắp hết</Tag>;
  return <Tag color="green">Còn hàng</Tag>;
};

/**
 * Trang nhập/xuất kho dùng chung.
 * type = "IN": nhập kho (+), type = "OUT": xuất kho (-)
 * Quy trình: quét SKU -> thêm vào phiếu (quét lại thì +1) -> chỉnh số lượng -> Xác nhận.
 * Quét mã chưa có trong mục Sản phẩm: admin/manager được tạo sản phẩm mới ngay, staff chỉ nhận thông báo.
 */
export default function StockPage({ type }) {
  const isIn = type === "IN";
  const title = isIn ? "Nhập kho" : "Xuất kho";
  const { pathname } = useLocation();
  const canCreateProduct = isIn && !pathname.startsWith("/staff"); // chỉ nhập kho, và chỉ admin/manager được tạo sản phẩm mới

  const [items, setItems] = useState([]);
  const [status, setStatus] = useState({ type: "info", message: "Quét mã sản phẩm để thêm vào phiếu" });
  const [saving, setSaving] = useState(false);
  const [manual, setManual] = useState("");
  const [stockList, setStockList] = useState([]);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [createSku, setCreateSku] = useState(null); // mã chưa có sản phẩm, đang chờ tạo
  const createSkuRef = useRef(null);
  createSkuRef.current = createSku;

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const list = unwrap(await inventoryApi.getAll());
      setStockList(Array.isArray(list) ? list : []);
    } catch (err) {
      setStockList([]);
    }
    try {
      const h = unwrap(await inventoryApi.getHistory());
      setHistory(Array.isArray(h) ? h : []);
    } catch (err) {
      setHistory([]);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Tra nhanh tồn kho theo SKU (sản phẩm chưa từng nhập thì tồn = 0)
  const stockOf = (sku) => stockList.find((s) => s.sku === sku)?.stock ?? 0;

  // Tra SKU rồi thêm vào phiếu; nếu chưa có sản phẩm thì (admin/manager) mở hộp tạo mới
  const lookupAndAdd = async (sku) => {
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
      if (!isIn) {
        setStatus({ type: "error", message: `Mã ${sku} chưa có trong mục Sản phẩm nên không thể xuất kho.` });
      } else if (canCreateProduct) {
        setStatus({ type: "warning", message: `Mã ${sku} chưa có trong mục Sản phẩm.` });
        setCreateSku(sku);
      } else {
        setStatus({
          type: "error",
          message: `Mã ${sku} chưa có trong mục Sản phẩm. Hãy nhờ quản lý tạo sản phẩm này trước.`,
        });
      }
    }
  };

  // Dùng cho quét camera và nhập tay: đang mở hộp tạo sản phẩm thì bỏ qua các lần quét
  const addSku = (sku) => {
    if (createSkuRef.current) return;
    return lookupAndAdd(sku);
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
      await loadData();
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
    { title: "Tồn kho", dataIndex: "stock", width: 100, render: (v) => <Text strong>{v}</Text> },
    { title: "Trạng thái", dataIndex: "stock", width: 120, render: (v) => stockStatus(v) },
  ];

  const historyColumns = [
    { title: "Thời gian", dataIndex: "createdAt", width: 170, render: formatTime },
    {
      title: "Loại",
      dataIndex: "type",
      width: 90,
      render: (t) => (t === "IN" ? <Tag color="green">Nhập</Tag> : <Tag color="orange">Xuất</Tag>),
    },
    { title: "SKU", dataIndex: "sku", width: 100 },
    { title: "Tên sản phẩm", dataIndex: "name" },
    {
      title: "Số lượng",
      dataIndex: "quantity",
      width: 100,
      render: (q, row) => (row.type === "IN" ? <Text type="success">+{q}</Text> : <Text type="warning">-{q}</Text>),
    },
    { title: "Tồn sau", dataIndex: "stockAfter", width: 90 },
    { title: "Người thực hiện", dataIndex: "createdBy" },
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
          <Button icon={<ReloadOutlined />} onClick={loadData} loading={loading}>
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
        <Text type="secondary" style={{ display: "block", marginTop: 8 }}>
          Sắp hết: tồn từ 1 đến {LOW_STOCK}. Hết hàng: tồn bằng 0.
        </Text>
      </Card>

      <Card title={pathname.startsWith("/staff") ? "Lịch sử nhập / xuất" : "Lịch sử nhập / xuất"}>
        <Table
          rowKey={(row, i) => `${row.createdAt}-${row.sku}-${i}`}
          columns={historyColumns}
          dataSource={history}
          size="small"
          pagination={{ pageSize: 8 }}
          locale={{ emptyText: "Chưa có giao dịch nào" }}
        />
      </Card>

      <ProductQuickCreate
        open={!!createSku}
        sku={createSku}
        onClose={() => setCreateSku(null)}
        onCreated={(sku) => {
          setCreateSku(null);
          lookupAndAdd(sku); // thêm ngay sản phẩm vừa tạo vào phiếu
          loadData(); // làm mới bảng tồn kho để thấy sản phẩm vừa tạo
        }}
      />
    </div>
  );
}