import { useCallback, useRef, useState } from "react";
import { Button, Modal, Typography } from "antd";
import { PrinterOutlined, BarcodeOutlined } from "@ant-design/icons";
import JsBarcode from "jsbarcode";

const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

export default function QrLabel({ sku, name }) {
  const [open, setOpen] = useState(false);
  const boxRef = useRef(null);

  // Vẽ mã vạch Code128 vào thẻ <svg> ngay khi thẻ xuất hiện
  const drawBarcode = useCallback(
    (el) => {
      if (!el || !sku) return;
      try {
        JsBarcode(el, String(sku), { format: "CODE128", displayValue: false, margin: 0, width: 2, height: 70 });
      } catch (e) {
        /* SKU có ký tự không mã hóa được (chỉ hỗ trợ chữ không dấu, số) */
      }
    },
    [sku]
  );

  const print = () => {
    const svg = boxRef.current?.querySelector("svg");
    if (!svg) return;
    const clone = svg.cloneNode(true);
    clone.removeAttribute("width");
    clone.removeAttribute("height");
    clone.setAttribute("preserveAspectRatio", "none");

    const w = window.open("", "_blank", "width=480,height=360");
    if (!w) return;
    w.document.write(
      `<html><head><title>${esc(sku)}</title><style>` +
        `@page{size:35mm 22mm;margin:0}` +
        `html,body{margin:0;padding:0}` +
        `body{width:35mm;height:22mm;display:flex;flex-direction:column;align-items:center;justify-content:center;font-family:Arial,sans-serif;overflow:hidden}` +
        `.head{font-size:5pt;line-height:1.1}` +
        `.bar{width:31mm;height:9mm;margin:0.6mm 0}` +
        `.bar svg{width:100%;height:100%;display:block}` +
        `.sku{font:bold 9pt Arial,sans-serif;line-height:1}` +
        `.name{font-size:5pt;max-width:33mm;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.1}` +
        `</style></head><body>` +
        `<div class="head">MÃ SẢN PHẨM</div>` +
        `<div class="bar">${clone.outerHTML}</div>` +
        `<div class="sku">${esc(sku)}</div>` +
        `<div class="name">${esc(name)}</div>` +
        `</body></html>`
    );
    w.document.close();
    w.focus();
    setTimeout(() => w.print(), 300);
  };

  return (
    <>
      <Button size="small" icon={<BarcodeOutlined />} onClick={() => setOpen(true)} disabled={!sku}>
        Tem
      </Button>
      <Modal
        title={`Tem sản phẩm: ${sku}`}
        open={open}
        onCancel={() => setOpen(false)}
        footer={[
          <Button key="close" onClick={() => setOpen(false)}>
            Đóng
          </Button>,
          <Button key="print" type="primary" icon={<PrinterOutlined />} onClick={print}>
            In tem 35x22
          </Button>,
        ]}
      >
        <div ref={boxRef} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
          <Typography.Text>MÃ SẢN PHẨM</Typography.Text>
          <svg ref={drawBarcode} preserveAspectRatio="none" style={{ width: 320, height: 90 }} />
          <Typography.Text strong style={{ fontSize: 20 }}>
            {sku}
          </Typography.Text>
          <Typography.Text type="secondary">{name}</Typography.Text>
        </div>
      </Modal>
    </>
  );
}