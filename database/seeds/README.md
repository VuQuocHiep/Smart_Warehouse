# Seed cho database hiện tại

Chạy `warehouse_seed_current.sql` bằng MySQL Workbench; không cần API seed.
File lấy dữ liệu từ `D:/Pham/STUDY/HK7/IOT/BTL/warehouse_seed.sql` và chuyển
sang khóa UUID, bảng `product`, `monitored_shelf`, `monitored_shelf_product`.

## Chạy trong Workbench

1. Chạy backend ít nhất một lần để Flyway tạo đầy đủ schema B1 + V2–V11 và
   Kho 1–4, rồi dừng backend trong lúc seed.
2. Mở `database/seeds/warehouse_seed_current.sql` trong MySQL Workbench.
3. Kiểm tra dòng `USE iot`; đổi nếu datasource của bạn dùng database khác.
4. Dùng tài khoản có quyền INSERT/SELECT và CREATE ROUTINE/ALTER ROUTINE/EXECUTE.
5. Chạy **toàn bộ file** (không bôi đen chỉ một vài dòng). Đọc Action Output:
   nếu có lỗi thì dừng và xử lý lỗi đó trước khi chạy tiếp. Truy vấn thống kê
   cuối file không thay thế cho việc kiểm tra `CALL` có thành công hay không.
6. Kết quả mong đợi: `seeded_products=12`; Kho 1 và Kho 2 mỗi kho có
   `seeded_shelves=12`, `seed_product_assignments=12`.
7. Khởi động lại backend, tải lại frontend. Các cặp kho/sản phẩm sẽ xuất hiện
   trong `/api/demand/catalog` và màn hình dự báo nhu cầu.

## Dữ liệu được seed

- 12 sản phẩm SIM_SKU001–SIM_SKU012, giữ tên, category, mô tả, khối lượng và
  ngày tạo từ SQL nguồn. Đơn vị ITEM theo mặc định nguồn.
- 24 kệ SIM_SHELF_001–SIM_SHELF_012 trên mỗi kho, mỗi kệ gán một sản phẩm.
- SIM_WH01 ánh xạ vào Kho 1; SIM_WH02 ánh xạ vào Kho 2. Giữ nguyên tên và
  mã thiết bị giám sát của các kho. Device code của kệ khớp Postman Collection.

Có thể chạy lại: dữ liệu seed đã có được tái sử dụng, quan hệ còn thiếu được
thêm. Script không xóa bảng/database, không đổi lịch sử Flyway, không thay đổi
sản phẩm hoặc kệ có sẵn. Nếu gặp SKU trùng nhiều hàng, sản phẩm đã xóa mềm,
nội dung sản phẩm xung đột hoặc device code thuộc kệ khác, script báo lỗi và
rollback các INSERT của lần chạy. Dừng backend và không chạy nhiều phiên seed
đồng thời để tránh tranh chấp với thao tác tạo sản phẩm.

Nếu trước đó gặp lỗi 1267 `Illegal mix of collations`, mở lại bản SQL mới và
chạy toàn bộ file để tạo lại procedure. File chỉ rõ charset/collation cho biến,
literal và các phép so sánh UUID, hỗ trợ cả bảng dùng `utf8mb4_unicode_ci` lẫn
`utf8mb4_0900_ai_ci`. Không cần ALTER bảng hoặc xóa database.

## Phần chưa thể seed vào schema hiện tại

V8/V9 đã thêm bảng tồn kho, giao dịch và tồn theo kệ, nhưng script seed này
chưa ánh xạ 16.569 giao dịch và 24 tồn kho nguồn vào các bảng mới. 24 RFID
và 4 thiết bị RFID nguồn cũng không được nhập bởi script. Dữ liệu đầy đủ vẫn có trong
`postman/warehouse-seed/warehouse_seed.data.json`.

Đây là seed **catalog phục vụ test ứng dụng và demo dự báo**. Python hiện tự
sinh lịch sử mô phỏng từ catalog; chưa đọc hoặc train từ lịch sử SQL nguồn.
Để dùng chính lịch sử đó cần ánh xạ dữ liệu nguồn vào schema hiện tại và thêm adapter dự báo.
Muốn thử luồng nhập/xuất mới, lập phiếu Nhập kho trên web để tạo tồn ban đầu;
gán sản phẩm vào kệ giám sát không tự tạo số lượng tồn.

## Sinh lại SQL

Từ thư mục gốc dự án:

```powershell
python scripts/sql_seed_to_current_schema.py 'D:/Pham/STUDY/HK7/IOT/BTL/warehouse_seed.sql'
```
