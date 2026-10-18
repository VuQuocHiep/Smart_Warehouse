# Chạy mô hình cảnh báo

**Luồng hiện tại:** [HYBRID.md](HYBRID.md) — model thử nghiệm chỉ dùng nhiệt độ/độ ẩm, gas MQ-2 chia cấp bằng ngưỡng ADC cấu hình; cấp cuối lấy mức cao hơn. Model này tổng quát hóa kém ở các phiên test và được ghi rõ là thử nghiệm. Phần MQ-2 ba đầu vào và TVOC bên dưới là các luồng trước, không được gọi trong API nhận số đo hiện tại.

**Hướng dùng MQ-2 hiện tại:** xem [MQ2.md](MQ2.md) để thu ADC qua USB, gán nhãn, train RandomForest riêng và đưa vào backend. Backend nhận ADC trực tiếp khi có model MQ-2 phù hợp; chưa có model trả `MQ2_MODEL_REQUIRED`. Phần TVOC dưới đây mô tả model cũ và đầu vào `gasTvocPpb` riêng.

Backend Java đọc `src/main/resources/ai/smoke-risk.rf.gz` khi khởi động. Đây là bản xuất từ toàn bộ 300 cây của `models/smoke_risk_model.joblib`, bao gồm median của imputer, ngưỡng chia nhánh, xác suất tại lá và thứ tự lớp. Không có ngưỡng nhiệt độ/gas thay thế mô hình.

`export_model.py` tạo tài nguyên model, manifest SHA-256 và 165 mẫu đối chiếu với `Pipeline.predict_proba` gốc, bao phủ cả bốn cấp và một mẫu có gas thiếu. Backend kiểm thử cả lớp dự đoán và xác suất với sai số 1e-12. Chạy lại exporter sau khi thay model, rồi chạy kiểm thử và khởi động lại backend.

## Dữ liệu đầu vào của model TVOC cũ

Model gốc sử dụng `temperature` (°C), `humidity` (%) và `gas` từ cột `TVOC[ppb]` trong tập training. ADC 12 bit của MQ-2 không phải TVOC ppb. API lưu ADC trong `gas`; chỉ `gasTvocPpb` đã hiệu chuẩn mới được đưa vào model. Không quy đổi ADC bằng hệ số tùy ý, không dùng median để giả vờ gas đã được đo.

Luồng TVOC không suy luận nếu thiếu TVOC đã hiệu chuẩn. Với firmware MQ-2 chỉ gửi ADC, backend chọn model MQ-2 riêng; nếu chưa có model này thì trả `predictionStatus=MQ2_MODEL_REQUIRED`, `riskLevel=null`, `controlAllowed=false`. Các ngưỡng bảo quản do người dùng cấu hình là luồng riêng, không phải kết quả AI. Các số đo cũ chưa qua model không được tự gán cấp 0.

Muốn chạy đầy đủ trên phần cứng, cần dữ liệu hiệu chuẩn được kiểm chứng cho MQ-2, cảm biến đo TVOC tương thích, hoặc tập dữ liệu MQ-2 có nhãn để train model mới. Kết quả test trên tập TVOC không chứng minh độ chính xác trên MQ-2.

## Backend và ESP

Đặt `DB_USERNAME`, `DB_PASSWORD`, `IOT_INGEST_KEY` trong môi trường trước khi chạy backend. Flyway V5 thêm các cột lưu kết quả AI; không sửa migration cũ. API nhận số đo:

```http
POST /api/sensor/readings
Content-Type: application/json
X-Device-Key: <IOT_INGEST_KEY>
```

```json
{"deviceCode":"ESP_ENV_01","temperature":31.6,"humidity":69.1,"gas":339,"gasTvocPpb":42}
```

`42` chỉ là ví dụ giá trị TVOC đã đo, không phải kết quả quy đổi ADC 339. Không gửi giá trị ví dụ thay số đo thực. `deviceCode` phải trùng `monitoringDeviceCode` của Kho 1, không còn tra qua kệ; migration V6 chuyển liên kết này sang kho. Với dữ liệu hợp lệ, phản hồi chứa số đo cùng `riskLevel`, `confidence`, `aiStatus`, `actionCode`, `modelVersion`, `predictionStatus` và `controlAllowed`. Kết quả được lưu cùng số đo và đưa lên `/api/sensor/latest`, `/api/sensor/history`, `/api/warehouses/monitoring`.

Firmware không comment nằm tại `hardware/warehouse_ai/warehouse_ai.ino`. Cài thư viện DHT sensor library, Adafruit Unified Sensor và ArduinoJson 7 trong Arduino IDE. Sửa Wi-Fi, IP backend, khóa và mã thiết bị trước khi nạp. Firmware hiện gửi ADC trực tiếp, xuất mẫu qua USB để thu dữ liệu và không còn hàm giả định quy đổi ADC sang TVOC.

ESP chỉ áp dụng lệnh khi HTTP thành công, JSON hợp lệ, đúng thiết bị, `predictionStatus=READY`, `controlAllowed=true` và cấp khớp `actionCode`. Cấp 0 bật đèn xanh, cấp 1 đèn đỏ, cấp 2 đèn đỏ/quạt, cấp 3 đèn đỏ/quạt/còi. Khi lỗi mạng, cảm biến hoặc chưa có AI, đèn đỏ báo lỗi, đèn xanh tắt; trạng thái quạt/còi từ lệnh AI cuối cùng được giữ lại. Sau khởi động chúng ở LOW cho đến khi có lệnh AI hợp lệ. Việc chờ Wi-Fi/HTTP có thể làm chậm nhịp nháy đèn lỗi.

Chưa có Arduino toolchain/board trong môi trường kiểm thử; cần compile và nạp firmware trên máy có ESP32 Arduino core cùng các thư viện trên.
