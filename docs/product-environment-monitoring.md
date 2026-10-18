# Giám sát bảo quản sản phẩm theo kệ

Luồng dự đoán RandomForest và firmware nhận lệnh AI được mô tả tại [AI/README.md](../AI/README.md). Hướng dùng ADC MQ-2 trực tiếp, thu dữ liệu và train model riêng nằm tại [AI/MQ2.md](../AI/MQ2.md). `gas` lưu ADC MQ-2; nếu có model MQ-2 đúng thiết bị thì backend dùng trực tiếp số đo này để dự đoán. Thiếu model trả `MQ2_MODEL_REQUIRED` nhưng vẫn lưu số đo. API còn nhận `gasTvocPpb` cho model TVOC cũ riêng biệt, không dùng ADC thay TVOC. Phản hồi POST là số đo đã lưu kèm kết quả AI, thay vì bản ghi kệ.

1. Admin/Manager mở **Sản phẩm**, nhập nhiệt độ tối thiểu/tối đa (°C) và độ ẩm tối thiểu/tối đa (%). Ngưỡng do người quản lý tự cấu hình theo yêu cầu bảo quản của sản phẩm; để trống nghĩa là không kiểm tra giới hạn đó.
2. Mở **Kệ hàng**, chọn một trong 4 ô Kho 1–4 ở trên cùng để xem kệ của kho đó. Có thể tạo thêm kệ và gán các sản phẩm. Kho 1 nhận số đo ESP dùng chung cho tất cả kệ trong kho. Kho 2–4 dùng số đo giả lập và không cần gắn ESP.
3. Đặt biến môi trường `IOT_INGEST_KEY` cho backend trước khi khởi động, dùng cùng khóa trong firmware. Không đưa khóa vào frontend. Nếu khóa trống, backend từ chối nhận số đo.
4. ESP gửi HTTP POST `/api/sensor/readings` với header `Content-Type: application/json` và `X-Device-Key: <khóa cấu hình>`:

```json
{
  "deviceCode": "ESP_ENV_01",
  "temperature": 32.5,
  "humidity": 85,
  "gas": 320
}
```

ESP dùng địa chỉ IP LAN của máy chạy backend, cổng 8080. Endpoint này xác thực bằng khóa thiết bị, không cần JWT người dùng. Khi triển khai qua mạng công cộng, dùng HTTPS.

Mã `deviceCode` phải trùng thiết bị giám sát của Kho 1 (`monitoringDeviceCode`, xem tại Thông tin kho). Migration V6 chuyển liên kết ESP sang kho; không cần gắn ESP với kệ và vẫn nhận số đo nếu kho chưa có kệ. Backend lưu lịch sử và áp dụng số đo cho tất cả kệ Kho 1, kiểm tra mọi sản phẩm còn hoạt động trong từng kệ. Kho 2–4 từ chối số đo ESP và dùng số đo giả lập. Backend kiểm tra sản phẩm trong các kho giả lập mỗi 5 giây, lưu cảnh báo với nhãn Giả lập và hiển thị tại chuông giống luồng ESP. Migration V4 thêm trường phân biệt nguồn cảnh báo. Trường `gas` là tùy chọn. Bằng ngưỡng vẫn hợp lệ; thấp hơn tối thiểu hoặc cao hơn tối đa sẽ tạo cảnh báo kèm tên kho, kệ, tên sản phẩm/SKU, số đo và giới hạn bị vi phạm.

Một sản phẩm có một cảnh báo trong mỗi đợt vượt ngưỡng, gộp nhiệt độ và độ ẩm. Các gói tiếp theo trong cùng đợt không tạo thông báo lặp. Khi cả hai chỉ số về ngưỡng, đợt kết thúc; nếu lại vượt ngưỡng sẽ tạo cảnh báo mới. Gỡ sản phẩm khỏi kệ cũng kết thúc cảnh báo đang mở. Thay đổi ngưỡng sản phẩm được áp dụng từ gói ESP tiếp theo. Số đo cuối cùng hiển thị ở trang kệ; sau 60 giây không có số đo mới được đánh dấu dữ liệu cũ.

Thông báo hiển thị qua nút chuông ở mọi trang sau đăng nhập, kiểm tra mỗi 5 giây khi web đang mở. Lịch sử lưu trong database và hiển thị tối đa 100 cảnh báo gần nhất. Mỗi người dùng tự đánh dấu đã đọc; trạng thái đó lưu riêng. Các tài khoản hiện dùng chung phạm vi kho của ứng dụng. Backend vẫn ghi cảnh báo khi người dùng đóng web; khi mở lại sẽ tải từ server.

Migration `V2__product_environment_monitoring.sql` thêm các cột ngưỡng và bảng giám sát riêng, không sửa V1. Kệ giám sát nhiều SKU độc lập với bảng `shelf` cũ dành cho cân một SKU. Dữ liệu kệ cũ chưa được tự chuyển. **Lưu ý sẵn có của dự án:** V1 tạo database `smart_warehouse` với khóa BIGINT, trong khi cấu hình hiện tại trỏ `iot` và các entity Product/Warehouse dùng UUID. Cần đồng bộ schema hiện có với entity trước khi chạy ứng dụng mới trên database trắng; V2 không sửa sự lệch schema này. Không chạy lại V1 trên database có dữ liệu vì V1 chứa DROP DATABASE.

## Lỗi khởi động thiếu bảng cảnh báo

Spring Boot 4 cần dependency `org.springframework.boot:spring-boot-starter-flyway` để tự động chạy migration trước khi Hibernate kiểm tra schema; chỉ khai báo `flyway-core` và `flyway-mysql` chưa kích hoạt phần tự cấu hình này. Dự án đã dùng starter cùng `flyway-mysql`.

Với database `iot` hiện tại đã có các bảng UUID, `baseline-on-migrate=true` ghi nhận phiên bản nền 1 rồi chạy V2 để thêm cột và bảng giám sát. Đã xác nhận migration V2 chạy thành công và Hibernate kiểm tra schema thành công trên database này. Khi đổi dependency, reload Maven rồi khởi động lại toàn bộ backend. Giữ `ddl-auto=validate`.

Migration V3 tạo hoặc dùng lại Kho 1–4 theo tên, đánh dấu `monitoring_slot` cố định, thêm 2 kệ ban đầu cho kho chưa có kệ. Không tự gán sản phẩm mẫu. Các kho cũ khác được giữ trong database. Trang Kho hàng hiển thị 4 kho chính, cho sửa địa chỉ/mô tả và chuyển sang xem kệ của kho đã chọn.

`GET /api/warehouses/monitoring` trả 4 kho, số kệ/sản phẩm và số đo. Kho 1 thiếu dữ liệu hiển thị chờ ESP, không thay bằng giả lập. Kho 2–4 có nhãn giả lập và số đo thay đổi mỗi 5 giây. `/api/sensor/latest` và `/api/sensor/history?limit=60` trả dữ liệu ESP thật của Kho 1. Các trang IoT dùng chung nguồn số đo từ API giám sát kho.
