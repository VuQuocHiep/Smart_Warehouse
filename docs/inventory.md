# Nhập/xuất, quét mã và cân đối chiếu

## Chuẩn bị

Chạy MySQL, backend và frontend theo [hướng dẫn local](../README.md#local), migration đến V13.
Không cần Python. Đăng nhập bằng tài khoản được cấp; quyền lấy từ JWT.
Tạo sản phẩm có SKU, trọng lượng đơn vị theo gram; tạo kệ nếu muốn demo xếp hàng.
V13 tự tạo catalog DEMAND_V13 và lịch sử/tồn demo. [Script catalog cũ](../database/seeds/README.md) vẫn không tạo tồn cho SIM_SKU.

## Tem và camera

Ở Sản phẩm, bấm Tem để xem/in barcode Code128 theo SKU; dùng SKU chữ không dấu/số.
Component tên QrLabel nhưng tem hiện là barcode.
Camera đọc QR/barcode có nội dung là SKU; dùng SKU thuần để demo.
Vào Nhập kho hoặc Xuất kho, cấp quyền rồi bật camera.
Quét lại cùng SKU tăng số lượng; chỉ chống lặp liên tục trong 1,5 giây:
đưa tem ra khỏi khung sau khi đọc hoặc tắt camera để tránh cộng thêm.
Có ô SKU thủ công. Camera cần localhost/HTTPS.
Admin/manager có thể tạo nhanh sản phẩm chưa biết khi nhập; staff cần sản phẩm có sẵn.

## Phiếu

1. Chọn kho nhập/xuất, thêm SKU và kiểm tra số lượng nguyên.
2. Nhập: chọn kệ/gợi ý nếu có; gợi ý là quy tắc Java. Kệ thiếu sức chứa bị từ chối khi ghi phiếu.
3. Không có cân: bỏ chọn cân đối chiếu và xác nhận.
4. Xuất: không vượt tồn của kho đã chọn; chỉ trừ các kệ thuộc kho đó. Kệ nhập phải thuộc kho đã chọn.
5. Kiểm tra tồn và lịch sử sau mỗi phiếu.

Tồn tổng, tồn theo kho và phần tồn đã xếp kệ là các mức dữ liệu khác nhau. Tồn legacy chưa xác định kho không tự được phân bổ.
Gán sản phẩm vào kệ giám sát không tự tạo số lượng tồn.

## Cân

Nạp/hiệu chuẩn theo [HX711](hx711-weighing.md).
Chọn Cân đối chiếu dòng này, đặt riêng hàng đúng SKU/số lượng lên cân đã trừ bì.
Chờ hợp lệ/ổn định, bấm Lấy khối lượng rồi xác nhận.
Đổi số lượng hoặc quét thêm SKU phải lấy cân lại.

Ví dụ 2 × 250 g = 500 g; mặc định ±50 g chấp nhận 450–550 g.
Đây là sai số nghiệp vụ, không phải độ chính xác thiết bị.
Bằng chứng đúng người/SKU/số lượng/loại phiếu, một lần, hết hạn 10 phút.
Tổng tải gồm mặt cân/bì/hàng phải trong 5 kg; không tự suy ra số lượng.

## API

| Đường dẫn | Mục đích |
|---|---|
| POST /inventory/stock-in | Nhập |
| POST /inventory/stock-out | Xuất |
| GET /inventory/getAll | Tồn tổng |
| GET /inventory/warehouses | Danh sách kho cho ADMIN/MANAGER/STAFF |
| GET /inventory/warehouse-stock | Tồn theo kho |
| GET /inventory/history | Lịch sử |
| GET /inventory/used-skus | SKU đã giao dịch |
| POST /inventory/placement-suggestions | Gợi ý kệ |
| GET /api/scale | Trạng thái cân |
| POST /api/scale/capture | Bằng chứng cân |

API người dùng dùng Bearer token; qua Compose thêm /backend trước đường dẫn.
Body phiếu nhập/xuất và gợi ý kệ cần có `warehouseId` bên cạnh `items`.
POST /api/scale/readings dùng X-Device-Key của ESP.

## Demo

Nhập SKU thủ công → nhập 2 → kiểm tra tồn → xuất 1 → kiểm tra còn 1.
Tiếp theo thử tem/camera và cân với vật chuẩn/sản phẩm có trọng lượng đúng.
Kiểm tra xuất quá tồn, cân lệch ngưỡng và mất ESP.
Frontend build không xác nhận backend, migration hoặc phần cứng.
