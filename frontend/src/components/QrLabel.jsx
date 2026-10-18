import { useRef, useState } from "react";
import { Button, Modal, QRCode, Typography } from "antd";
import { PrinterOutlined, QrcodeOutlined } from "@ant-design/icons";

const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

/**
 * Nút "Mã QR" cho một sản phẩm: mở cửa sổ xem mã QR của SKU và in tem.
 * Dùng: <QrLabel sku={record.sku} name={record.name} />
 */
export default function QrLabel({ sku, name }) {
  const [open, setOpen] = useState(false);
  const boxRef = useRef(null);

  const print = () => {
    const canvas = boxRef.current?.querySelector("canvas");
    if (!canvas) return;
    const src = canvas.toDataURL("image/png");
    const w = window.open("", "_blank", "width=420,height=520");
    if (!w) return;
    w.document.write(
      `<html><head><title>${esc(sku)}</title></head>` +
        `<body style="font-family:monospace;text-align:center;margin:20px">` +
        `<img src="${src}" style="width:3.5cm;height:3.5cm"/>` +
        `<div style="font-weight:bold;font-size:14px">${esc(sku)}</div>` +
        `<div style="font-size:11px">${esc(name)}</div></body></html>`
    );
    w.document.close();
    w.focus();
    setTimeout(() => w.print(), 300);
  };

  return (
    <>
      <Button size="small" icon={<QrcodeOutlined />} onClick={() => setOpen(true)} disabled={!sku}>
        Mã QR
      </Button>
      <Modal
        title={`Mã QR: ${sku}`}
        open={open}
        onCancel={() => setOpen(false)}
        footer={[
          <Button key="close" onClick={() => setOpen(false)}>
            Đóng
          </Button>,
          <Button key="print" type="primary" icon={<PrinterOutlined />} onClick={print}>
            In tem
          </Button>,
        ]}
      >
        <div ref={boxRef} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
          <QRCode value={String(sku || "")} size={200} errorLevel="M" bgColor="#ffffff" />
          <Typography.Text strong>{sku}</Typography.Text>
          <Typography.Text type="secondary">{name}</Typography.Text>
        </div>
      </Modal>
    </>
  );
}