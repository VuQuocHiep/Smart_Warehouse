# Hệ thống quản lý kho hàng thông minh

## I. Tổng quan dự án

- Dự án gồm **3 chức năng chính**, mỗi người tự làm trọn vẹn chức năng của mình (hardware + backend) rồi gộp lại thành sản phẩm hoàn chỉnh.
- Các phần dùng chung có thể chia riêng cho 1 người phụ trách xuyên suốt: **frontend**, **báo cáo**, **database**, **mua sắm linh kiện**.
- Còn **hardware + backend** thì mỗi người tự làm phần feature của mình.
- Linh kiện được **mua chung** cho cả nhóm.

### Luồng dữ liệu chung (áp dụng cho mọi feature)

```
Sensor → ESP32 → Gửi dữ liệu (MQTT broker / WiFi HTTP) → Backend + Database → Frontend (web dashboard hiển thị)
```

### Tech stack

| Thành phần | Lựa chọn |
|---|---|
| Backend | SpringBoot 4.1 + Java 17 |
| Frontend | React 19 + Vite|
| Database | MySQL |
| Message Queue | RabbitMQ |
| Security | JWT |
| Cache | Redis |

---

## II. Kiến trúc hệ thống

```
┌─────────────┐     ┌───────────┐     ┌────────────────────┐     ┌───────────────────┐
│   Sensors   │ --> │   ESP32   │ --> │  Backend + Database │ --> │ Frontend Dashboard │
│ (nhiệt độ,  │     │ (xử lý &  │     │ (lưu trữ, xử lý     │     │  (web, hiển thị    │
│  độ ẩm,     │     │  gửi dữ   │     │  logic, cảnh báo)   │     │  real-time,        │
│  khói/gas,  │     │  liệu qua │     │                     │     │  báo cáo, chart)   │
│  RFID,      │     │  MQTT/    │     │                     │     │                    │
│  Load Cell) │     │  HTTP)    │     │                     │     │                    │
└─────────────┘     └───────────┘     └────────────────────┘     └────────────────────┘
       │                   │
       │                   └──> Điều khiển thiết bị output tại chỗ:
       │                        quạt (relay), buzzer, LED, OLED
       │
       └──> Cảnh báo có thể đẩy ra ngoài: Zalo/Telegram/Email cho người dùng
```

**Nguyên tắc kiến trúc chung:**
- Mỗi feature đi theo pipeline: **Sensor → ESP32 → Backend/DB → Frontend**.
- ESP32 vừa đọc sensor, vừa điều khiển output tại chỗ (quạt, còi, LED) khi vượt ngưỡng, đồng thời gửi dữ liệu lên backend.
- Backend chịu trách nhiệm lưu lịch sử, so sánh/đối chiếu số liệu, và phát cảnh báo qua kênh ngoài (tin nhắn/telegram/mail).
- Frontend là dashboard web hiển thị số liệu real-time, lịch sử, và biểu đồ (chart theo tuần/tháng).

---

## III. Chi tiết chức năng

### Feature 1: Giám sát môi trường

**Mục tiêu:** Theo dõi điều kiện môi trường trong kho và tự động phản ứng khi vượt ngưỡng an toàn.

**Chức năng:**
- **Giám sát nhiệt độ:** khi quá nóng → ESP32 tự động bật quạt + gửi cảnh báo.
- **Giám sát độ ẩm.**
- **Giám sát khói/khí gas/báo cháy:** khi phát hiện → chuông kêu + bật đèn báo động.
- Các thông số được hiển thị liên tục trên dashboard web.
- Khi vượt một ngưỡng xác định (cấu hình trước), hệ thống có thể gửi cảnh báo qua Zalo/Telegram/Email cho người dùng, đồng thời phát cảnh báo tại chỗ qua chuông/đèn và hiển thị trên dashboard.
- Có thể mở rộng thêm biểu đồ (chart) theo tuần/tháng cho từng chỉ số.

**Phần cứng liên quan:** ESP32, DHT22 (nhiệt độ + độ ẩm), MQ-2 (khói/gas), Quạt DC 5V, Relay/MOSFET module, Buzzer, LED đỏ/xanh.

---

### Feature 2: Nhập xuất hàng bằng RFID

**Mục tiêu:** Tự động hóa việc ghi nhận nhập/xuất hàng hóa bằng RFID, không cần nhập liệu thủ công.

**Chức năng:**
- Quét RFID để xác định SKU/UID sản phẩm. Mỗi mã RFID đại diện cho **1 loại sản phẩm** (không phải 1 đơn vị hàng hóa riêng lẻ).
- Cần **2 bộ đầu đọc riêng biệt**: một bộ cho nhập hàng, một bộ cho xuất hàng — vì RFID chỉ xác định được mã sản phẩm, không tự phân biệt được chiều nhập/xuất.
  - Quét ở bộ nhập → số lượng SKU đó **+1** trên hệ thống.
  - Quét ở bộ xuất → số lượng SKU đó **-1** trên hệ thống.
- Mọi lượt nhập/xuất được **tự động lưu vào database** (lịch sử giao dịch).
- Thông báo trạng thái quét bằng LED:
  - LED xanh: quét thành công.
  - LED đỏ: mã sản phẩm không tồn tại hoặc có lỗi.
- (Tùy chọn mở rộng) Hiển thị SKU + tên sản phẩm vừa quét lên màn hình LED/OLED.

**Phần cứng liên quan:** 2× RC522 (đầu đọc RFID — 1 cho nhập, 1 cho xuất), RFID Tag/Card 13.56 MHz, LED đỏ/xanh, OLED 0.96" I2C (tùy chọn hiển thị).

---

### Feature 3: Đối chiếu tồn kho bằng Load Cell

**Mục tiêu:** Kiểm tra chéo số lượng tồn kho thực tế (đo bằng cân) với số lượng ghi nhận trên hệ thống, phát hiện sai lệch.

**Giả định & cách tính:**
- Mỗi kệ chỉ chứa **một loại hàng**, đã có sẵn dữ liệu tham chiếu: SKU, tên sản phẩm, khối lượng 1 đơn vị hàng.
- Công thức tính số lượng thực tế:

```
Số lượng = (Tổng khối lượng đo được − Khối lượng tĩnh (vỏ hộp, kệ, ...)) / Khối lượng 1 đơn vị hàng
```

- Kết quả được làm tròn, chấp nhận sai số x% (ngưỡng cấu hình được).
- **Không tính toán ngay lập tức** — hệ thống đợi khối lượng đo được ổn định khoảng **3 giây** rồi mới chốt số liệu, tránh nhiễu do rung/lắc khi đặt hàng lên kệ.

**Chức năng:**
- Cảm biến Load Cell cân tổng trọng lượng trên kệ → tính ra số lượng tồn kho thực tế.
- So sánh số lượng thực tế với số lượng ghi trên hệ thống (được cập nhật từ Feature 2).
- Nếu có sai lệch → bật **LED đỏ** và tạo cảnh báo trên hệ thống.
- Màn hình (LED/OLED) hiển thị: tên sản phẩm, SKU, số lượng — có thể hiển thị tại chỗ hoặc trên dashboard web.
- Tần suất đối chiếu: có thể delay theo chu kỳ (ví dụ mỗi 10 phút) hoặc cố định 1 lần vào cuối ngày — cần chốt phương án.

> **Phương án thay thế đã cân nhắc (không chọn):** Tự động đối soát bằng RFID tương tự Feature 1/2 — bị loại vì không đo được khối lượng thực tế, chỉ xác định được sản phẩm đã quét qua.

**Phần cứng liên quan:** Load Cell 5kg, HX711 (module đọc load cell), OLED 0.96" I2C, LED đỏ.

---
