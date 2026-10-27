import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { Alert, Button, Card, Checkbox, Input, InputNumber, Select, Table, Tag, Typography, message } from "antd";
import { DeleteOutlined, ReloadOutlined } from "@ant-design/icons";
import ScannerCamera from "../../components/ScannerCamera";
import ProductQuickCreate from "../../components/ProductQuickCreate";
import { productApi } from "../../api/product";
import { inventoryApi } from "../../api/inventory";
import { scaleApi } from "../../api/scale";
import useScaleStatus from "../../hooks/useScaleStatus";
import ScaleLive from "../../components/ScaleLive";
import { canCaptureScale } from "../../utils/scale";

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
 * Nhập kho: AI đề xuất kệ phù hợp cho từng dòng (môi trường, sức chứa, tần suất xuất), có thể đổi kệ.
 * Quét mã chưa có trong mục Sản phẩm: admin/manager được tạo sản phẩm mới khi nhập kho, staff chỉ nhận thông báo.
 */
export default function StockPage({ type }) {
  const isIn = type === "IN";
  const title = isIn ? "Nhập kho" : "Xuất kho";
  const { pathname } = useLocation();
  const canCreateProduct = isIn && !pathname.startsWith("/staff"); // chỉ nhập kho, và chỉ admin/manager được tạo sản phẩm mới

  const [items, setItems] = useState([]);
  const { status: scaleStatus, error: scaleError } = useScaleStatus();
  const [capturing, setCapturing] = useState(null);
  const [clock, setClock] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setClock(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => { setItems([]); setShelfChoice({}); }, [type]);
  const patchItem = (sku, patch) => setItems((prev) => prev.map((x) => x.sku === sku ? { ...x, ...patch } : x));
  const captureWeight = async (row) => {
    setCapturing(row.sku);
    patchItem(row.sku, { weighing: null, weightError: '' });
    try {
      const ticket = await scaleApi.capture(row.sku, row.quantity, type);
      setItems((prev) => prev.map((x) => x.sku === row.sku && x.quantity === ticket.quantity && x.useScale ?
        { ...x, weighing: ticket, weightError: '' } : x));
    } catch (err) { patchItem(row.sku, { weightError: err.message }); }
    finally { setCapturing(null); }
  };
  const ticketValid = (row) => row.weighing && row.weighing.quantity === row.quantity && row.weighing.stockType === type &&
    clock - new Date(row.weighing.capturedAt).getTime() < 600000;
  const [status, setStatus] = useState({ type: "info", message: "Quét mã sản phẩm để thêm vào phiếu" });
  const [saving, setSaving] = useState(false);
  const [manual, setManual] = useState("");
  const [stockList, setStockList] = useState([]);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [createSku, setCreateSku] = useState(null); // mã chưa có sản phẩm, đang chờ tạo
  const createSkuRef = useRef(null);
  createSkuRef.current = createSku;

  const [placements, setPlacements] = useState({}); // sku -> danh sách kệ đề xuất
  const [shelfChoice, setShelfChoice] = useState({}); // sku -> kệ do người dùng chọn
  const [suggesting, setSuggesting] = useState(false);

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

  // Mỗi khi phiếu nhập thay đổi (thêm hàng, đổi số lượng), xin AI đề xuất lại vị trí xếp hàng
  const itemsKey = JSON.stringify(items.map((x) => [x.sku, x.quantity]));
  useEffect(() => {
    if (!isIn || items.length === 0) {
      setPlacements({});
      return undefined;
    }
    const timer = setTimeout(async () => {
      setSuggesting(true);
      try {
        const res = unwrap(
          await inventoryApi.getPlacements({ items: items.map(({ sku, quantity }) => ({ sku, quantity })) })
        );
        const map = {};
        (Array.isArray(res) ? res : []).forEach((r) => {
          map[r.sku] = r.options || [];
        });
        setPlacements(map);
      } catch (err) {
        setPlacements({});
      }
      setSuggesting(false);
    }, 400);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [itemsKey, isIn]);

  // Kệ đang được chọn cho một SKU: ưu tiên lựa chọn của người dùng (nếu còn phù hợp), không thì kệ AI đề xuất
  const chosenShelf = (sku) => {
    const options = placements[sku] || [];
    const picked = options.find((o) => o.shelfId === shelfChoice[sku] && o.eligible);
    if (picked) return picked.shelfId;
    return options.find((o) => o.recommended)?.shelfId;
  };

  // Tra nhanh tồn kho theo SKU (sản phẩm chưa từng nhập thì tồn = 0)
  const stockOf = (sku) => stockList.find((s) => s.sku === sku)?.stock ?? 0;

  // Tra SKU rồi thêm vào phiếu; nếu chưa có sản phẩm thì (admin/manager, nhập kho) mở hộp tạo mới
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
          next[i] = { ...next[i], quantity: next[i].quantity + 1, weighing: null, weightError: '' };
          return next;
        }
        return [...prev, { sku: key, name: product.name || key, quantity: 1, unitWeight: product.unit_weight, useScale: false }];
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
          message: `Mã ${sku} chưa có trong mục Sản phẩm (hoặc lỗi kết nối). Hãy nhờ quản lý tạo sản phẩm này trước.`,
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
    setItems((prev) => prev.map((x) => (x.sku === sku ? { ...x, quantity: quantity || 1, weighing: null, weightError: '' } : x)));

  const removeItem = (sku) => setItems((prev) => prev.filter((x) => x.sku !== sku));

  const sendManual = () => {
    if (manual.trim()) {
      addSku(manual.trim());
      setManual("");
    }
  };

  // Khi xuất: có dòng nào vượt tồn kho không
  const overStock = !isIn && items.some((x) => x.quantity > stockOf(x.sku));
  const weighingBlocked = items.some((x) => x.useScale && !ticketValid(x));

  const confirm = async () => {
    if (!items.length || overStock || weighingBlocked || capturing) return;
    setSaving(true);
    try {
      await inventoryApi.stock(type, {
        items: items.map(({ sku, quantity, useScale, weighing }) => ({ sku, quantity,
          shelfId: isIn ? chosenShelf(sku) : undefined, weighRequired: useScale,
          scaleTicketId: useScale ? weighing?.id : undefined })),
      });
      message.success(`${title} thành công (${items.length} sản phẩm)`);
      setItems([]);
      setShelfChoice({});
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

  // Cột "Vị trí đề xuất" chỉ có ở trang Nhập kho
  const placementColumn = {
    title: "Vị trí đề xuất (AI)",
    width: 360,
    render: (_, row) => {
      const options = placements[row.sku] || [];
      if (!options.length) {
        return <Text type="secondary">{suggesting ? "Đang phân tích..." : "Chưa có kệ nào"}</Text>;
      }
      const selectedId = chosenShelf(row.sku);
      const selected = options.find((o) => o.shelfId === selectedId);
      const top = options[0];
      return (
        <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <Select
            size="small"
            style={{ width: "100%" }}
            value={selectedId}
            placeholder="Không có kệ phù hợp"
            onChange={(v) => setShelfChoice((prev) => ({ ...prev, [row.sku]: v }))}
            options={options.map((o) => ({
              value: o.shelfId,
              disabled: !o.eligible,
              label: `${o.recommended ? "★ " : ""}${o.shelfName} · ${o.warehouseName} · ${o.score} điểm${
                o.eligible ? "" : " (không phù hợp)"
              }`,
            }))}
          />
          {selected ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {selected.reasons.slice(0, 3).join(" · ")}
            </Text>
          ) : (
            <Text type="danger" style={{ fontSize: 12 }}>
              {top?.reasons?.[0] || "Không có kệ phù hợp"}
            </Text>
          )}
        </div>
      );
    },
  };

  const itemColumns = [
    { title: "SKU", dataIndex: "sku", width: 100 },
    { title: "Tên sản phẩm", dataIndex: "name" },
    { title: "Tồn hiện tại", width: 110, render: (_, row) => stockOf(row.sku) },
    {
      title: "Số lượng",
      dataIndex: "quantity",
      width: 120,
      render: (q, row) => <InputNumber min={1} precision={0} value={q} disabled={saving || Boolean(capturing)} onChange={(v) => setQuantity(row.sku, v)} />,
    },
    {
      title: 'Đối chiếu cân (g)', width: 320,
      render: (_, row) => {
        const expected = Number(row.unitWeight) * row.quantity;
        const tolerance = Math.max(scaleStatus?.settings?.absoluteGrams ?? 50, expected * (scaleStatus?.settings?.percent ?? 0) / 100);
        const actual = scaleStatus?.reading?.weightGrams;
        const excessive = row.useScale && canCaptureScale(scaleStatus) && actual != null && Math.abs(actual - expected) > tolerance;
        return <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <Checkbox checked={row.useScale} disabled={saving || Boolean(capturing)} onChange={(e) => patchItem(row.sku,
            { useScale: e.target.checked, weighing: null, weightError: '' })}>Cân đối chiếu dòng này</Checkbox>
          {row.useScale && <>
            <Text>Dự kiến: {Number.isFinite(expected) ? expected.toFixed(2) : '?'} g · ±{Number.isFinite(tolerance) ? tolerance.toFixed(2) : '?'} g</Text>
            {expected > 5000 && <Text type="danger">Vượt 5 kg; chia nhỏ lượng hàng trên phiếu để cân.</Text>}
            <Button size="small" loading={capturing === row.sku} disabled={saving || Boolean(capturing) || !canCaptureScale(scaleStatus) || !Number.isFinite(expected) || expected <= 0 || expected > 5000}
              onClick={() => captureWeight(row)}>{row.weighing ? 'Cân lại' : 'Lấy khối lượng'}</Button>
            {excessive && !ticketValid(row) && <Text type="danger">Cân lệch {Math.abs(actual - expected).toFixed(2)} g, vượt ngưỡng ±{tolerance.toFixed(2)} g. Kiểm tra hàng và bì.</Text>}
            {ticketValid(row) ? <Tag color="green">Đã cân {row.weighing.weightGrams.toFixed(2)} g · đạt</Tag> :
              <Text type="warning">{row.weighing ? 'Kết quả cân hết hạn; hãy cân lại' : 'Chưa lấy kết quả cân hợp lệ'}</Text>}
            {row.weightError && <Text type="danger">{row.weightError}</Text>}
          </>}
        </div>;
      },
    },
    {
      title: isIn ? "Sau khi nhập" : "Sau khi xuất",
      width: 130,
      render: (_, row) => {
        const after = isIn ? stockOf(row.sku) + row.quantity : stockOf(row.sku) - row.quantity;
        return after < 0 ? <Text type="danger">Vượt tồn kho</Text> : <Text strong>{after}</Text>;
      },
    },
    ...(isIn ? [placementColumn] : []),
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
    { title: 'Kết quả cân', width: 210, render: (_, row) => row.weighing ?
      `${row.weighing.weightGrams.toFixed(2)} / ${row.weighing.expectedGrams.toFixed(2)} g (±${row.weighing.toleranceGrams.toFixed(2)} g)` : 'Không cân' },
    { title: "Vị trí / ghi chú", dataIndex: "note", ellipsis: true },
    { title: "Người thực hiện", dataIndex: "createdBy" },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card title={`${title} bằng quét mã`} extra={<Tag color={isIn ? "green" : "orange"}>{isIn ? "+ Nhập" : "- Xuất"}</Tag>}>
        <div style={{ display: "flex", flexDirection: "column", gap: 12, width: "100%", maxWidth: 560, marginInline: "auto" }}>
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

      <Card title="Cân hàng trước khi xác nhận"><ScaleLive status={scaleStatus} error={scaleError} />
        <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>Đặt riêng toàn bộ hàng của một dòng SKU lên cân, chờ ổn định rồi lấy khối lượng cho dòng đó. Trừ bì và hiệu chuẩn tại mục Cân điện tử. Cân không tự đổi khối lượng thành số lượng.</Text>
      </Card>
      <Card title={`Phiếu ${title.toLowerCase()}`}>
        <Table
          rowKey="sku"
          columns={itemColumns}
          dataSource={items}
          pagination={false}
          scroll={{ x: 1300 }}
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
            <Button
              onClick={() => {
                setItems([]);
                setShelfChoice({});
              }}
              disabled={!items.length}
            >
              Xóa phiếu
            </Button>
            <Button type="primary" onClick={confirm} loading={saving} disabled={!items.length || overStock || weighingBlocked || Boolean(capturing)}>
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

      <Card title={pathname.startsWith("/staff") ? "Lịch sử nhập / xuất của bạn" : "Lịch sử nhập / xuất"}>
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
