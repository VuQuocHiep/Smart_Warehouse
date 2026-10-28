# Chạy hệ thống bằng Docker

Yêu cầu Docker Engine hoặc Docker Desktop dùng Linux containers và Docker Compose v2+.
Chạy lệnh từ thư mục gốc repository.

## Khởi động

```powershell
Copy-Item .env.example .env
# Điền mật khẩu và token riêng trong .env.
docker compose up -d --build
docker compose ps
docker compose logs -f backend ml
```

Mở http://localhost:8088. Lần build đầu tải dependency Maven, npm và Python.
MySQL và ML có healthcheck; backend chờ hai service sẵn sàng rồi chạy Flyway.
Frontend có thể khởi động trước khi Spring Boot hoàn tất, khi đó API tạm trả 502.
Healthcheck frontend chỉ xác nhận Nginx hoạt động, không kiểm tra backend.

## Luồng hệ thống

```text
Browser: React + camera → Nginx :8088 → /backend/* → Spring Boot :8080
                                                       ↕
                                                  MySQL :3306
                                                       ↕ qua Spring Boot
                                                FastAPI ML :8091

ESP32 + DHT22/MQ-2/HX711 → Nginx :8088 → Spring Boot
ESP32 ← kết quả phân tích / lệnh cân trong phản hồi HTTP
```

| Service | Image / build | Dữ liệu |
|---|---|---|
| frontend | Node 22 build React; Nginx phục vụ web và API proxy | Static assets trong image |
| backend | Maven/Java 17 build Spring Boot | Model AI cảnh báo và migration trong JAR |
| mysql | MySQL 8.0 | Volume mysql_data |
| ml | Python 3.12, FastAPI/LightGBM, một worker | Volume ml_data tại /app/runtime |

Chỉ frontend xuất cổng ra host. Backend gọi mysql:3306 và ml:8091 trong mạng Compose.
Camera chạy trên trình duyệt người dùng; HX711 nối với ESP32.
Không cần truyền camera, USB hay cổng COM vào container.

## Camera, barcode và API

Docker build cài dependency từ package-lock.json, gồm html5-qrcode và jsbarcode.
React dùng VITE_API_BASE_URL=/backend. Tất cả API giữ cùng origin với web:

| URL qua Nginx | Chức năng |
|---|---|
| /backend/auth/login | Đăng nhập |
| /backend/inventory/stock-in | Nhập kho |
| /backend/inventory/stock-out | Xuất kho |
| /backend/inventory/placement-suggestions | Gợi ý kệ |
| /backend/api/scale | Đọc trạng thái cân |
| /backend/api/scale/capture | Lấy bằng chứng cân |
| /backend/api/scale/commands | Trừ bì / hiệu chuẩn |
| /backend/api/demand/forecasts | Kết quả dự báo |

Các API người dùng yêu cầu JWT. Proxy bỏ prefix /backend trước khi chuyển sang Java,
giữ query string, header xác thực và trả Cache-Control: no-store cho API.
Refresh đường dẫn React được phục vụ bằng index.html.

Camera dùng được tại http://localhost:8088 khi đã cấp quyền.
Điện thoại truy cập bằng IP LAN cần HTTPS được trình duyệt tin cậy.

## HTTPS cho camera trên thiết bị khác

Chuẩn bị chứng chỉ hợp lệ cho đúng hostname/IP truy cập:

```text
certs/
  fullchain.pem
  privkey.pem
```

Thư mục certs được bỏ qua bởi Git. Có thể đặt TLS_CERT_DIR trong .env sang thư mục khác.
Với chứng chỉ do CA nội bộ cấp, phải cài và tin cậy CA trên từng máy/điện thoại dùng camera.
Chỉ bỏ qua cảnh báo chứng chỉ không đảm bảo camera được cấp quyền.

```powershell
docker compose -f compose.yaml -f compose.https.yaml up -d --build
```

Mở https://<hostname-hoac-ip-trong-chung-chi>:8443.
HTTPS_PORT đổi cổng mặc định; HTTP 8088 vẫn được giữ cho firmware demo LAN hiện tại.
Dùng cùng hai tham số -f khi quản lý hoặc dừng bộ service có HTTPS.
File override mount chứng chỉ chỉ đọc, không đưa private key vào image.
Hai listener dùng cùng cấu hình proxy nên API không bị mixed content trên trang HTTPS.

## ESP32 và load cell

Trong hardware/warehouse_ai/warehouse_ai.ino, đặt:

```cpp
#define BACKEND_BASE_URL "http://<IP-LAN-may-chay-Docker>:8088/backend"
```

Firmware tự ghép cả hai URL:
- /backend/api/sensor/readings: môi trường.
- /backend/api/scale/readings: cân và nhận lệnh trừ bì/hiệu chuẩn.

Đặt deviceKey khớp IOT_INGEST_KEY trong .env, deviceCode khớp IOT_SCALE_DEVICE_CODE
(mặc định ESP_ENV_01). Mã này cũng phải khớp monitoringDeviceCode của Kho 1 cho giám sát môi trường.
Không dùng localhost trong firmware để chỉ máy chủ. Mở cổng HTTP_PORT trên firewall
cho mạng LAN nếu ESP không kết nối được, sau đó nạp lại firmware.

Hiệu chuẩn, trừ bì và khai báo khối lượng mặt cân theo [HX711](hx711-weighing.md).
Chạy một backend instance: số đo cân và lệnh chờ nằm trong RAM.
Sau restart, chờ ESP gửi mẫu mới; cấu hình sai số và bằng chứng cân vẫn trong MySQL.
Hệ số hiệu chuẩn lưu trên ESP32, không nằm trong Docker volume.

## Cấu hình

| Biến .env | Ý nghĩa |
|---|---|
| DB_PASSWORD | Mật khẩu user warehouse trong database iot |
| MYSQL_ROOT_PASSWORD | Mật khẩu root MySQL |
| DEMAND_SERVICE_TOKEN | Token dùng chung backend/ML |
| DEMAND_DATA_SOURCE | Nguồn lịch sử `SIMULATED` (mặc định) hoặc `REAL` |
| IOT_INGEST_KEY | Khóa xác thực telemetry môi trường và cân |
| IOT_SCALE_DEVICE_CODE | Mã ESP được service cân chấp nhận |
| MQ2_GAS_LEVEL1/2/3 | Ngưỡng ADC tăng dần, mặc định 1800/2500/3200 |
| HTTP_PORT | Cổng HTTP, mặc định 8088 |
| HTTPS_PORT / TLS_CERT_DIR | Cổng và chứng chỉ khi dùng override HTTPS |

Compose bật ML và đặt URL nội bộ. Frontend chỉ nhận URL API lúc build,
không nhận mật khẩu database hoặc token ML. Java dùng MaxRAMPercentage=70.0
cho heap; đây không phải giới hạn tổng RAM của container.

## Database, model và cập nhật

MySQL mới chạy B1 rồi V2–V13: gồm tồn kho, tồn kệ, bằng chứng cân, sai số mặc định ±50 g, tồn theo kho và seed lịch sử dự báo.
Không chạy V1 legacy thủ công. Volume mới không tự nhập database đang chạy ngoài Docker.
V13 tự nạp 12 sản phẩm DEMAND_V13, 24 chuỗi và 730 ngày lịch sử mẫu; restart không nạp lại.
Xem [dự báo](demand-forecasting.md) và [demo nhập/xuất](inventory.md).

ML lưu joblib, metadata và kết quả trong ml_data. Sau migration, backend tự tạo train khởi tạo khi ML sẵn sàng;
train lại 05:00 thứ Hai và suy luận 00:10 hằng ngày theo giờ Việt Nam. Build image không sao chép runtime local.
Lịch sử mẫu dừng tại ngày chốt V13; không tự bổ sung ngày xuất 0 khi restart hoặc khi thời gian trôi qua.

Sau khi cập nhật code:

```powershell
docker compose up -d --build
docker compose logs --tail=100 backend ml frontend
```

```powershell
docker compose stop
docker compose start
docker compose down
```

Các lệnh này giữ volumes. down -v xóa dữ liệu MySQL và ML.
Sao lưu trước khi chuyển máy. Đổi mật khẩu trong .env không đổi user trong database đã khởi tạo;
cần cập nhật mật khẩu trong MySQL tương ứng.

## Kiểm tra sau triển khai

1. Đăng nhập, tải lại trực tiếp trang Nhập kho để kiểm tra SPA routing.
2. Quét tem trên localhost hoặc HTTPS, nhập/xuất và kiểm tra tồn/lịch sử.
3. Kiểm tra POST của ESP ở cả endpoint môi trường và cân.
4. Hiệu chuẩn, lấy cân vào phiếu và xác nhận giao dịch.
5. Chạy dự báo, restart container và xác nhận model/database còn dữ liệu.

HTTP của firmware dùng cho demo LAN. Khi triển khai Internet cần xác minh chứng chỉ
HTTPS trên ESP32 và chuyển khóa ký JWT hiện có sang cấu hình riêng.
