import { useEffect, useRef, useState } from "react";
import { Button, Select } from "antd";
import { CameraOutlined, StopOutlined } from "@ant-design/icons";
import { Html5Qrcode } from "html5-qrcode";

const READER_ID = "sku-reader";
const COOLDOWN_MS = 1500;

/**
 * Khung camera quét QR/barcode, dùng lại ở nhiều trang.
 * Props: onScan(text) - gọi mỗi khi đọc được một mã (đã chống quét trùng liên tục)
 *        onError(message) - gọi khi không mở được camera
 */
export default function ScannerCamera({ onScan, onError }) {
  const [cameras, setCameras] = useState([]);
  const [cameraId, setCameraId] = useState(null);
  const [running, setRunning] = useState(false);
  const scannerRef = useRef(null);
  const lastRef = useRef({ code: "", time: 0 });
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;

  useEffect(() => {
    Html5Qrcode.getCameras()
      .then((list) => {
        setCameras(list);
        if (list.length) setCameraId(list[list.length - 1].id);
      })
      .catch(() => onError?.("Chưa cấp quyền camera (cần mở bằng localhost hoặc HTTPS)"));
    return () => stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleScan = (text) => {
    const now = Date.now();
    const { code, time } = lastRef.current;
    if (text === code && now - time < COOLDOWN_MS) return;
    lastRef.current = { code: text, time: now };
    onScanRef.current?.(text.trim());
  };

  const start = async () => {
    if (!cameraId || scannerRef.current) return;
    const scanner = new Html5Qrcode(READER_ID);
    scannerRef.current = scanner;
    try {
      await scanner.start(cameraId, { fps: 10, qrbox: { width: 250, height: 150 } }, handleScan);
      setRunning(true);
    } catch (e) {
      scannerRef.current = null;
      onError?.("Không mở được camera: " + e);
    }
  };

  const stop = async () => {
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

  return (
    <div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
        <Select
          style={{ minWidth: 220 }}
          value={cameraId}
          onChange={setCameraId}
          disabled={running}
          placeholder="Chọn camera"
          options={cameras.map((c) => ({ value: c.id, label: c.label || c.id }))}
        />
        {!running ? (
          <Button type="primary" icon={<CameraOutlined />} onClick={start} disabled={!cameraId}>
            Bật camera
          </Button>
        ) : (
          <Button danger icon={<StopOutlined />} onClick={stop}>
            Tắt camera
          </Button>
        )}
      </div>
      <div id={READER_ID} style={{ width: "100%", minHeight: 240, background: "#000", borderRadius: 8 }} />
    </div>
  );
}