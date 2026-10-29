# Smart Warehouse

Hệ thống quản lý kho hàng tích hợp giám sát môi trường bằng ESP32, quản lý nhập/xuất, cân đối chiếu và dự báo nhu cầu.

## Chức năng

- Quản lý sản phẩm, kho, kệ và tồn kho.
- Nhập/xuất bằng QR/barcode hoặc SKU; lưu lịch sử giao dịch và in tem sản phẩm.
- Giám sát nhiệt độ, độ ẩm và gas; phân tích cảnh báo và điều khiển đèn, quạt, còi.
- Gợi ý vị trí kệ theo điều kiện bảo quản, sức chứa và lịch sử xuất hàng.
- Hiệu chuẩn, trừ bì và cân đối chiếu phiếu bằng HX711.
- Dự báo nhu cầu 14 ngày bằng LightGBM trên dữ liệu mô phỏng.
- Xác thực JWT và phân quyền Admin, Manager, Staff.

## Công nghệ

| Thành phần | Công nghệ | Thư mục |
|---|---|---|
| Backend | Java 17, Spring Boot, Spring Security, JPA, Flyway | `backend/iot` |
| Frontend | React, Vite, Ant Design, Redux Toolkit | `frontend` |
| Database | MySQL 8.0 | `database` |
| Dự báo | Python, FastAPI, LightGBM, joblib | `AI/demand` |
| Thiết bị | ESP32, DHT22, MQ-2, HX711 | `hardware/warehouse_ai` |

## Khởi chạy

### Local

Yêu cầu Java 17, Maven 3.9 hoặc Maven Wrapper, Node.js 22.12+, Python và MySQL 8.0. Các lệnh dưới đây sử dụng PowerShell.

Tạo database trong MySQL:

```sql
CREATE DATABASE IF NOT EXISTS iot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**Service dự báo:** từ thư mục gốc dự án, tạo môi trường Python và cài thư viện lần đầu:

```powershell
python -m venv AI/demand/.venv
.\AI\demand\.venv\Scripts\python.exe -m pip install -r AI/demand/requirements.txt
```

Đặt token nội bộ và khởi chạy service tại cổng **8091**:

```powershell
$env:DEMAND_SERVICE_TOKEN = '<token-noi-bo>'
.\AI\demand\start.ps1
```

**Backend:** mở terminal khác tại thư mục gốc dự án, sử dụng cùng token với service dự báo:

```powershell
cd backend/iot
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '<mat-khau-mysql>'
$env:IOT_INGEST_KEY = '<khoa-khop-firmware>'
$env:DEMAND_SERVICE_ENABLED = 'true'
$env:DEMAND_SERVICE_URL = 'http://127.0.0.1:8091'
$env:DEMAND_SERVICE_TOKEN = '<token-noi-bo>'
./mvnw.cmd spring-boot:run
```

**Frontend:** mở terminal khác tại thư mục gốc dự án:

```powershell
cd frontend
npm ci
npm run dev
```

Truy cập **http://localhost:5173**. Backend sử dụng cổng **8080**; Flyway tự quản lý schema khi khởi động.

Đăng nhập bằng tài khoản **Admin/Manager**, tạo sản phẩm và gán vào kệ của kho. Mở **Dự báo nhu cầu**, chờ tác vụ hoàn tất rồi chọn kho/SKU để xem kết quả. Service tự huấn luyện khi chưa có model và lưu artifact tại `AI/demand/runtime`.

### Docker

Yêu cầu Docker Engine hoặc Docker Desktop ở chế độ Linux containers.

1. Sao chép cấu hình mẫu:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Điền mật khẩu database và token trong `.env`.
3. Khởi chạy các service:

   ```powershell
   docker compose up -d --build
   ```

Truy cập **http://localhost:8088**. Cấu hình gồm frontend, backend, ML và MySQL; dữ liệu được lưu trong Docker volumes.

## Tài liệu

- [Kiến trúc hệ thống](docs/architecture.md)
- [Triển khai Docker](docs/docker.md)
- [Triển khai cloud miễn phí: Render, Aiven, GitHub Actions](docs/cloud-render.md)
- [Biên bản nghiệm thu cloud IoT mức 3](docs/cloud-acceptance.md)
- [Nhập/xuất, quét mã và in tem](docs/inventory.md)
- [Cấu hình và hiệu chuẩn cân HX711](docs/hx711-weighing.md)
- [Giám sát bảo quản sản phẩm](docs/product-environment-monitoring.md)
- [Mô hình cảnh báo môi trường](AI/HYBRID.md)
- [Dự báo nhu cầu](docs/demand-forecasting.md)
- [Dữ liệu mẫu](database/seeds/README.md)
