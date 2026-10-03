import { useEffect, useRef, useState } from "react";
import { Card, Radio, Select, Button, Alert, List, Input, Space, Typography } from "antd";
import { CameraOutlined, StopOutlined } from "@ant-design/icons";
import { Html5Qrcode } from "html5-qrcode";
import { productApi } from "../../api/product";

const COOLDOWN_MS = 2000;
const READER_ID = "sku-reader";


export default function ScannerPage({ onDetected }) {
  const [mode, setMode] = useState("IN");
  const [cameras, setCameras] = useState([]);
  const [cameraId, setCameraId] = useState(null);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState({ type: "info", message: "Chưa quét" });
  const [history, setHistory] = useState([]);
  const [manualSku, setManualSku] = useState("");

  const scannerRef = useRef(null);
  const lastRef = useRef({ code: "", time: 0 });
  const modeRef = useRef(mode);
  modeRef.current = mode;
  const onDetectedRef = useRef(onDetected);
  onDetectedRef.current = onDetected;

  useEffect(() => {
    Html5Qrcode.getCameras()
      .then((list) => {
        setCameras(list);
        if (list.length) setCameraId(list[list.length - 1].id);
      })
      .catch(() => setResult({ type: "error", message: "Chưa cấp quyền camera (cần localhost hoặc HTTPS)" }));
    return () => stopScanner();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const pushHistory = (text) =>
    setHistory((h) => [`${new Date().toLocaleTimeString()} - ${text}`, ...h].slice(0, 30));

  const handleSku = async (sku) => {
    const type = modeRef.current;
    const label = type === "IN" ? "Nhập" : "Xuất";
    try {
      const res = await productApi.getProductBySku(sku);
      const body = res?.data ?? res;
      const product = body?.data ?? body;
      if (!product || (!product.name && !product.sku && !product.id)) throw new Error("Không tìm thấy");

      onDetectedRef.current?.(product, type);
      setResult({
        type: "success",
        message: `${label}: ${product.name || sku} (SKU: ${product.sku || sku})`,
      });
      pushHistory(`${label} ${product.sku || sku} - ${product.name || ""} ✔`);
    } catch (err) {
      setResult({ type: "error", message: `SKU không tồn tại hoặc lỗi: ${sku}` });
      pushHistory(`${label} ${sku} ✖`);
    }
  };

  const onScanSuccess = (text) => {
    const now = Date.now();
    const { code, time } = lastRef.current;
    if (text === code && now - time < COOLDOWN_MS) return;
    lastRef.current = { code: text, time: now };
    handleSku(text.trim());
  };

  const startScanner = async () => {
    if (!cameraId || scannerRef.current) return;
    const scanner = new Html5Qrcode(READER_ID);
    scannerRef.current = scanner;
    try {
      await scanner.start(cameraId, { fps: 10, qrbox: { width: 250, height: 150 } }, onScanSuccess);
      setRunning(true);
      setResult({ type: "info", message: "Đang quét... đưa mã vào khung" });
    } catch (e) {
      scannerRef.current = null;
      setResult({ type: "error", message: "Không mở được camera: " + e });
    }
  };

  const stopScanner = async () => {
    const scanner = scannerRef.current;
    scannerRef.current = null;
    if (scanner) {
      try {
        await scanner.stop();
        scanner.clear();
      } catch (e) {
        /* đã dừng */
      }
    }
    setRunning(false);
  };

  const sendManual = () => {
    if (manualSku.trim()) {
      handleSku(manualSku.trim());
      setManualSku("");
    }
  };

  return (
    <Card title="Quét mã sản phẩm bằng camera" style={{ maxWidth: 560, margin: "0 auto" }}>
      <Space direction="vertical" style={{ width: "100%" }} size="middle">
        <Radio.Group value={mode} onChange={(e) => setMode(e.target.value)}>
          <Radio.Button value="IN">Nhập kho (+1)</Radio.Button>
          <Radio.Button value="OUT">Xuất kho (-1)</Radio.Button>
        </Radio.Group>

        <Space wrap>
          <Select
            style={{ minWidth: 220 }}
            value={cameraId}
            onChange={setCameraId}
            disabled={running}
            placeholder="Chọn camera"
            options={cameras.map((c) => ({ value: c.id, label: c.label || c.id }))}
          />
          {!running ? (
            <Button type="primary" icon={<CameraOutlined />} onClick={startScanner} disabled={!cameraId}>
              Bật camera
            </Button>
          ) : (
            <Button danger icon={<StopOutlined />} onClick={stopScanner}>
              Tắt
            </Button>
          )}
        </Space>

        <div id={READER_ID} style={{ width: "100%", minHeight: 240, background: "#000", borderRadius: 8 }} />

        <Alert showIcon type={result.type} message={result.message} />

        <Space.Compact style={{ width: "100%" }}>
          <Input
            placeholder="Nhập SKU thủ công (dự phòng)"
            value={manualSku}
            onChange={(e) => setManualSku(e.target.value)}
            onPressEnter={sendManual}
          />
          <Button onClick={sendManual}>Gửi</Button>
        </Space.Compact>

        <div>
          <Typography.Text strong>Lịch sử quét</Typography.Text>
          <List size="small" dataSource={history} locale={{ emptyText: "Trống" }} renderItem={(t) => <List.Item>{t}</List.Item>} />
        </div>
      </Space>
    </Card>
  );
}
