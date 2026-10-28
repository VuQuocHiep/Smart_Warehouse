# Test dữ liệu warehouse_seed.sql bằng Postman

- `warehouse_seed.postman_collection.json`: import trực tiếp vào Postman.
- `warehouse_seed.data.json`: dữ liệu chuyển từ SQL, gồm 2 kho, 4 thiết bị,
  12 sản phẩm, 24 kệ, 24 RFID, 24 tồn kho và 16.569 giao dịch.
  Đây là JSON dữ liệu nguồn, không phải body của API import hàng loạt.

Toàn bộ dữ liệu là **mô phỏng**. File SQL nguồn chỉ được đọc, không thực thi.
Các giá trị được khai báo trong INSERT và cập nhật thời gian quét RFID được
giữ lại; không tự điền các giá trị mặc định của MySQL. `_seedRef` và các giá trị
`@sim_...` giữ quan hệ giữa các hàng trong SQL, không phải UUID của backend.

## Cách chạy

1. Khởi động backend tại `http://localhost:8080`; database đã migrate thành công
   và có Kho 1–4. Không cần import SQL vào MySQL.
2. Postman → **Import** → chọn `warehouse_seed.postman_collection.json`.
3. Mở collection → **Variables**, đặt `baseUrl`, `email`, `password` theo tài
   khoản ADMIN/MANAGER của bạn. Mật khẩu để trống trong file xuất. Tài khoản
   admin khởi tạo mặc định là `ADMINPTIT@gmail.com` / `123456789` nếu chưa đổi.
4. Chạy các folder **01 → 04** theo thứ tự, mỗi request một lần (Runner dùng
   **1 iteration**, không cần chọn data file). Login tự lưu JWT vào
   `accessToken`; response tạo sản phẩm tự lưu UUID cho các request tạo kệ.
5. Kiểm tra kết quả trên màn hình Sản phẩm/Kệ kho, hoặc gửi
   `GET /product/getAll` và `GET /api/shelves` với Bearer `{{accessToken}}`.

Không chạy lại folder tạo dữ liệu khi các SKU/device code đã tồn tại, vì API
trả lỗi trùng dữ liệu. Collection không tự xóa hoặc ghi đè dữ liệu có sẵn.

## Ánh xạ sang API hiện tại

API `/api/shelves` chỉ chấp nhận kho có `monitoringSlot`, nên collection dùng:

| Kho trong SQL | Kho được sử dụng trong ứng dụng |
|---|---|
| SIM_WH01 / Simulation warehouse 1 | Kho 1 (`monitoringSlot=1`) |
| SIM_WH02 / Simulation warehouse 2 | Kho 2 (`monitoringSlot=2`) |

Collection lấy UUID kho từ `/warehouse/getAll`, tạo 12 sản phẩm và 24 kệ
(mỗi kho 12 kệ, mỗi kệ một SKU). Tên, SKU, loại, mô tả và khối lượng sản phẩm
lấy từ SQL; `unit=ITEM` theo mặc định schema nguồn. Device code của kệ được
tạo từ mã kho + tên kệ để không trùng giữa hai kho.

Ứng dụng đã có API `/inventory/stock-in`, `/inventory/stock-out` và lịch sử giao dịch,
nhưng collection này chưa nhập dữ liệu tồn/lịch sử nguồn qua các API đó.
Không có API import hàng loạt bảo toàn toàn bộ lịch sử nguồn trong collection.
Dữ liệu nguồn còn lưu ở `warehouse_seed.data.json`; xem [luồng nhập/xuất](../../docs/inventory.md)
để tạo tồn và giao dịch demo mới.

## Test dự báo nhu cầu

Sau khi tạo dữ liệu, bật Python service và cấu hình backend như hướng dẫn
trong `docs/demand-forecasting.md`. Token Java/Python phải giống nhau.

Chạy **từng request** trong folder **05**:

1. `Verify demand catalog`: kiểm tra đủ 24 cặp kho/sản phẩm của seed.
2. `Update database forecast`: tạo job PREDICT và tự lưu `jobId`; đợi job train khởi tạo sau V13 hoàn tất trước khi gọi.
3. `Check job`: gửi lại request này cho đến khi `status=SUCCEEDED`.
4. `Read latest forecast`: xem dự báo sau khi job hoàn tất.

Không chạy toàn bộ folder 05 một lượt rồi kỳ vọng train đã xong. API chỉ công
bố kết quả sau khi train thành công. Nếu đã có nhiều sản phẩm khác gán trên
kệ, giới hạn demo là 64 cặp kho/sản phẩm cho toàn bộ catalog.

Module dự báo hiện đọc lịch sử DB của **V13**, gồm catalog DEMAND_V13 riêng và 730 ngày mẫu;
không tự sinh lịch sử khi gọi API và không tự nhập 16.569 giao dịch nguồn của collection này.
Train khởi tạo tự chạy khi backend/ML sẵn sàng, sau đó train lúc 05:00 thứ Hai theo giờ Việt Nam.
`DEMAND_SERVICE_TOKEN` chỉ cấu hình ở Java/Python;
Postman dùng JWT đăng nhập backend, không dùng token nội bộ đó.

## Sinh lại file

Tại thư mục gốc dự án:

```powershell
python scripts/sql_seed_to_postman.py 'D:/Pham/STUDY/HK7/IOT/BTL/warehouse_seed.sql'
```

Script kiểm tra tất cả INSERT đã được đọc, các tham chiếu được khai báo và
tổng giao dịch SUCCESS khớp tồn kho của cả 24 kệ trước khi xuất file.
