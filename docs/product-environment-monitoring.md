# Giám sát bảo quản sản phẩm

## Luồng hiện tại

ESP gửi nhiệt độ, độ ẩm và ADC MQ-2. Backend dùng RandomForest nhiệt độ/độ ẩm
kết hợp ngưỡng gas; xem [AI/HYBRID.md](../AI/HYBRID.md).
Model MQ-2 ba đầu vào và TVOC trong tài liệu cũ là các luồng tham khảo,
không phải nhánh suy luận hiện tại. Không quy đổi ADC thành TVOC bằng hệ số tùy ý.

Ngưỡng bảo quản theo sản phẩm là kiểm tra riêng, không phải kết quả AI.
Model cảnh báo thử nghiệm chưa được xác nhận là hệ thống phát hiện cháy thực tế.

## Chạy và cấu hình

1. Khởi động MySQL/backend/frontend theo [hướng dẫn local](../README.md#local); không cần Python.
2. Admin/manager nhập ngưỡng nhiệt độ/độ ẩm trong Sản phẩm; để trống là không kiểm tra giới hạn đó.
3. Trong Kệ kho, chọn kho, tạo kệ và gán sản phẩm cần giám sát.
4. Sửa Wi-Fi, BACKEND_BASE_URL và deviceKey trong firmware; khóa phải khớp IOT_INGEST_KEY.
5. deviceCode phải khớp monitoringDeviceCode của Kho 1, mặc định ESP_ENV_01.

Kho 1 dùng số đo ESP chung cho các kệ, vẫn nhận dữ liệu khi chưa có kệ.
Kho 2–4 dùng số đo mô phỏng và không nhận thay thế bằng telemetry ESP.
Firmware tích hợp cân/môi trường tại [warehouse_ai.ino](../hardware/warehouse_ai/warehouse_ai.ino).
API local là POST /api/sensor/readings; qua Compose là /backend/api/sensor/readings.

```http
POST /api/sensor/readings
Content-Type: application/json
X-Device-Key: <khoa-thiet-bi>
```

```json
{"deviceCode":"ESP_ENV_01","temperature":31.6,"humidity":69.1,"gas":339}
```

Đây là ví dụ payload, không phải dữ liệu hiệu chuẩn. Thiếu/không hợp lệ đầu vào
thì không có lệnh điều khiển hợp lệ. Phản hồi chứa số đo và kết quả phân tích;
ESP kiểm tra phản hồi trước khi áp dụng đèn/quạt/còi.
Chu kỳ môi trường hiện là khoảng 60 giây, không nhầm với cập nhật cân khoảng 1 giây.

## Cảnh báo và API đọc

Backend đối chiếu số đo với ngưỡng từng sản phẩm đang được gán trên kệ.
Một đợt vượt ngưỡng được gộp cảnh báo; trở lại bình thường rồi vượt tiếp là đợt mới.
Cảnh báo mô phỏng có nhãn nguồn. Chuông trên web lấy cập nhật định kỳ bằng REST;
người dùng có trạng thái đã đọc riêng. Chưa có luồng gửi Telegram/Zalo/Email được xác nhận.

- GET /api/warehouses/monitoring: bốn kho, số đo và nguồn.
- GET /api/sensor/latest: số đo ESP gần nhất.
- GET /api/sensor/history?limit=60: lịch sử số đo ESP.
- GET /api/product-alerts: cảnh báo bảo quản; người dùng cần đăng nhập.

Kho 1 chưa nhận ESP hiển thị chờ dữ liệu, không tự thay bằng giả lập.

## Database

Database mới dùng B1 rồi V2–V11 theo [README](../README.md).
V2 thêm giám sát; V3 tạo kho demo; V4 phân biệt mô phỏng; V5–V7 thêm kết quả AI và liên kết thiết bị với kho.
Giữ baseline-on-migrate=false và ddl-auto=validate. Không bật baseline tự động
để che lỗi thiếu bảng; không chạy V1 legacy có DROP DATABASE.
Nếu schema cũ lỗi, sao lưu và đối chiếu lịch sử Flyway trước khi sửa.
