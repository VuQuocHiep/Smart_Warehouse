# Kế hoạch triển khai lại dự báo xuất kho và nhu cầu nhập kho

- Ngày lập: 28/10/2026.
- Trạng thái: đã triển khai trong code; đã kiểm tra migration và train thật trên MySQL tạm.
- Múi giờ nghiệp vụ: `Asia/Ho_Chi_Minh`.

## 1. Mục tiêu

Triển khai luồng dữ liệu:

```text
Flyway V12 (schema) → Flyway V13 (seed lịch sử nhập/xuất vào DB)
          → backend tổng hợp lịch sử theo kho/sản phẩm/ngày
          → Python huấn luyện → lưu model.joblib
          → dự báo lượng xuất → tính lượng cần nhập
          → hai biểu đồ và danh sách cảnh báo
```

Kết quả cần có:

- Chart 1: lịch sử xuất kho và số lượng xuất kho dự kiến theo ngày.
- Chart 2: số lượng nhập kho cần bổ sung theo ngày.
- Danh sách hàng cần nhập thêm hôm nay.
- Danh sách hàng cần nhập thêm trong 7 ngày, gồm hôm nay và 6 ngày tiếp theo.
- Tự huấn luyện lại lúc **05:00 sáng thứ Hai hằng tuần**, kể cả khi không có người mở dashboard.
- Lưu model bằng joblib và tái sử dụng qua các lần khởi động dịch vụ.

## 2. Hiện trạng và phần cần thay đổi

| Thành phần | Hiện trạng | Thay đổi dự kiến |
|---|---|---|
| Flyway | Migration đến V11 | Thêm V12 cho schema và V13 cho dữ liệu mẫu, giữ nguyên các migration đã áp dụng |
| Lịch sử huấn luyện | Python tự sinh dữ liệu từ catalog khi chạy demo | Đọc lịch sử đã lưu trong DB qua backend |
| Giao dịch | `stock_transaction` chưa lưu kho phát sinh | Ghi nhận giao dịch theo kho |
| Tồn kho | `inventory` lưu tổng theo sản phẩm; `shelf_stock` lưu tồn trên kệ | Thêm tồn theo kho/sản phẩm và đối soát các mức tồn |
| Model | Đã có LightGBM và lưu joblib | Tận dụng code hiện tại, thay nguồn dữ liệu và quản lý vòng đời |
| Train lại | Sau 7 ngày, được kích hoạt khi truy cập | Scheduler cố định 05:00 thứ Hai |
| Dashboard | Một chart lịch sử và dự báo xuất | Hai chart và hai danh sách cảnh báo |

Các file chính liên quan:

- `backend/iot/src/main/resources/db/migration/`
- `backend/iot/src/main/java/com/example/iot/service/InventoryService.java`
- `backend/iot/src/main/java/com/example/iot/service/DemandForecastService.java`
- `backend/iot/src/main/java/com/example/iot/controller/DemandForecastController.java`
- `AI/demand/demand_model.py`
- `AI/demand/service.py`
- `frontend/src/components/DemandForecastDashboard.jsx`
- `frontend/src/api/demand.js`

## 3. Phạm vi và quy ước

- Đơn vị dự báo: cặp `(warehouse_id, product_id)`.
- Mặc định hiển thị 7 ngày; có thể giữ tùy chọn 14 ngày đang có.
- Lịch sử dùng làm đặc trưng chỉ đến hết hôm qua theo giờ Việt Nam.
- Model dự báo lượng xuất; lượng nhập được tính từ dự báo, tồn hiện tại và tồn an toàn.
- Tồn an toàn mặc định `0`, có thể cấu hình theo kho/sản phẩm.
- Giai đoạn đầu giả định hàng bổ sung có thể nhận trong ngày; chưa có tính toán thời gian giao hàng hoặc đơn mua đang về.
- Nguồn dữ liệu mô phỏng phải được nhận diện trong DB, artifact và giao diện. Không tự động trộn dữ liệu mô phỏng với dữ liệu thật khi train.
- Cảnh báo chỉ đề xuất bổ sung hàng; không tự tạo giao dịch nhập hoặc đơn mua.

## 4. Giai đoạn 1 — Migration V12 và nghiệp vụ theo kho

Tạo file dự kiến:

```text
backend/iot/src/main/resources/db/migration/V12__warehouse_demand_forecasting.sql
```

### 4.1. Thay đổi schema

- Bổ sung `warehouse_id`, `data_source` và mã đợt seed cho `stock_transaction`.
- Thêm index phục vụ truy vấn lịch sử theo kho, sản phẩm, loại giao dịch và thời gian.
- Thêm bảng `warehouse_inventory`, duy nhất theo `(warehouse_id, product_id)`, lưu số lượng tồn và thời điểm cập nhật.
- Thêm bảng cấu hình tồn an toàn theo kho/sản phẩm.
- Thêm bảng theo dõi job huấn luyện, gồm:
  - Mã job, loại kích hoạt: khởi tạo, lịch tuần, chạy bù hoặc thủ công.
  - Kỳ huấn luyện, trạng thái, thời điểm bắt đầu/kết thúc.
  - Ngày chốt dữ liệu, nguồn dữ liệu và phiên bản model.
  - Số lần thử, thông báo lỗi và thông tin khóa/lease để chống chạy trùng.

### 4.2. Điều chỉnh nhập/xuất

- Phiếu nhập/xuất mới phải xác định kho; kệ được chọn phải thuộc kho đó.
- Xuất kho chỉ trừ tồn và kệ thuộc kho đã chọn. Nếu cần xuất từ nhiều kho, tách thành các dòng giao dịch theo kho.
- Cập nhật giao dịch, tồn theo kho, tồn tổng và tồn kệ trong cùng transaction; khóa dữ liệu phù hợp để tránh xuất vượt tồn khi có yêu cầu đồng thời.
- Làm rõ `stock_after` hiện tại là tồn tổng; nếu cần tồn sau giao dịch theo kho, lưu riêng để giữ tương thích lịch sử cũ.
- Phần tồn chưa xếp kệ vẫn thuộc một kho cụ thể; không đồng nhất tồn kho với tổng tồn đã xếp kệ.

### 4.3. Xử lý dữ liệu cũ

- Chỉ ánh xạ kho cho giao dịch cũ khi có căn cứ xác định.
- Không suy ra kho lịch sử từ vị trí kệ hiện tại hoặc ghi chú không đủ tin cậy.
- Giữ dấu nhận diện phần chưa phân bổ; không đưa vào chuỗi train theo kho như dữ liệu đầy đủ.
- Đối soát tồn theo kho từ tồn kệ có căn cứ; phần tồn tổng chưa xác định kho phải được báo rõ và xử lý trước khi dùng để tính nhu cầu nhập.

Tiêu chí hoàn thành: V12 chạy được trên DB mới và DB đang có; nhập/xuất ghi nhận đúng kho, tồn không âm và các mức tồn đối soát được.

## 5. Giai đoạn 2 — Migration V13 seed lịch sử mẫu

Đưa dữ liệu mẫu vào migration V13 để Flyway tự nạp sau V12 khi backend khởi động, không cần chạy script seed thủ công:

```text
backend/iot/src/main/resources/db/migration/V13__seed_demo_stock_history.sql
```

V12 thay đổi cấu trúc bảng; V13 là migration dữ liệu, chỉ nạp catalog mẫu cần thiết, lịch sử nhập/xuất và số dư tồn tương ứng. Theo phạm vi demo của dự án, V13 được đặt trong migration mặc định nên mọi DB áp dụng V13 đều nhận bộ dữ liệu mẫu này.

### 5.1. Bộ dữ liệu

- V13 dùng Kho 1/2 hiện có, tự tạo 12 sản phẩm DEMAND_V13 và 24 kệ riêng để không xung đột catalog SIM_SKU cũ; có 24 chuỗi kho/sản phẩm và không cần chạy script catalog cũ trước đó.
- Chuẩn bị sẵn bộ dữ liệu 730 ngày bằng seed cố định; ghi dữ liệu vào SQL V13 trước khi triển khai.
- SQL biểu diễn ngày lịch sử theo độ lệch so với ngày chốt. Khi V13 chạy lần đầu, chốt ngày kết thúc là hôm qua theo giờ Việt Nam và lưu mốc này trong metadata đợt seed.
- Không tính lại mốc ngày hoặc sinh lại dữ liệu khi restart. Cùng bộ fixture và cùng ngày chốt phải cho cùng kết quả.
- Các kiểu nhu cầu: ổn định, theo thứ trong tuần, tăng, giảm, theo mùa và xuất không thường xuyên.
- Sinh giao dịch nhập tương ứng để bảo đảm tồn không âm tại mọi thời điểm.
- Tồn cuối khớp lịch sử, tồn theo kho, tồn tổng và phần phân bổ trên kệ; kiểm tra sức chứa kệ.
- Tạo tình huống đủ hàng, cần nhập hôm nay và cần nhập trong tuần để kiểm tra dashboard.

### 5.2. Quy tắc seed

- Gắn `SIMULATED` và mã đợt seed cho dữ liệu mẫu.
- Dùng mã định danh ổn định và kiểm tra xung đột trước khi nạp; chỉ tái sử dụng catalog khớp với bộ mẫu.
- Flyway ghi nhận V13 trong `flyway_schema_history`; mỗi DB chỉ áp dụng thành công một lần. Restart hoặc deploy lại không seed trùng, không cộng tồn lại và không ghi đè thay đổi nghiệp vụ sau seed.
- Không ghi đè tồn hoặc lịch sử thật của sản phẩm có sẵn; kiểm tra xung đột trước khi nạp.
- Giữ V13 là DML trên các bảng InnoDB, không trộn DDL; triển khai transaction nạp dữ liệu và kiểm thử rollback khi lỗi để không để lại số dư tồn dở dang.
- Sau seed, xuất báo cáo số giao dịch, khoảng thời gian, tổng nhập/xuất và kết quả đối soát.

### 5.3. Khởi động, chạy lại và train lần đầu

| Tình huống | Hành vi dự kiến |
|---|---|
| DB mới hoặc DB đang ở V11 | Khởi động backend → Flyway chạy V12 → V13 → kiểm tra dữ liệu → tạo job train khởi tạo |
| Restart backend với DB đã ở V13 | Flyway bỏ qua migration đã áp dụng; tiếp tục dùng dữ liệu và model hợp lệ hiện có |
| Muốn nạp lại toàn bộ mẫu từ đầu | Tạo lại DB demo khi chủ động reset môi trường; V12/V13 sẽ chạy lại trên DB mới |
| Muốn bổ sung hoặc sửa bộ mẫu trên DB đang dùng | Tạo migration phiên bản mới, không sửa nội dung V13 đã áp dụng hoặc xóa lịch sử Flyway |

- Không gọi Python hoặc train model bên trong SQL migration. Backend chỉ tạo job train sau khi Flyway hoàn tất, dữ liệu hợp lệ và dịch vụ ML sẵn sàng; nếu ML chưa sẵn sàng thì ghi trạng thái chờ/retry.
- Khi tạo lại DB demo nhưng giữ volume model, kiểm tra metadata và định danh bộ dữ liệu trước khi tái sử dụng artifact; model không tương thích phải được train lại.
- Nếu thời gian chạy hệ thống đã vượt xa ngày cuối seed, các ngày sau đó phải đến từ nghiệp vụ hoặc được nhận diện thiếu dữ liệu; restart không tự kéo dài lịch sử mẫu.

Tiêu chí hoàn thành: chỉ cần khởi động backend để migrate và seed, đủ dữ liệu train lần đầu, restart không trùng và có các tình huống kiểm thử cảnh báo.

## 6. Giai đoạn 3 — Adapter dữ liệu và huấn luyện model

### 6.1. Chuẩn bị dữ liệu

- Backend truy vấn lịch sử đầy đủ từ DB, không dùng API lịch sử chỉ trả 200 giao dịch gần nhất.
- Tổng hợp `OUT` theo ngày/kho/sản phẩm thành `export_quantity`.
- Dữ liệu `IN` phục vụ đối soát tồn, không dùng trực tiếp làm nhãn dự báo xuất.
- Chỉ điền `0` cho ngày không xuất khi xác nhận dữ liệu ngày đó đầy đủ; ngày thiếu dữ liệu phải có trạng thái riêng.
- Xác định rõ múi giờ của timestamp DB và chuyển về ngày nghiệp vụ Việt Nam trước khi tổng hợp.
- Chụp snapshot nhất quán, có ngày chốt và checksum để tái hiện lần train.
- Python nhận dữ liệu qua hợp đồng API nội bộ có token, không cần truy cập DB trực tiếp.

Định dạng dữ liệu tiếp tục tương thích với module hiện tại:

```text
date,warehouse_id,product_id,export_quantity,data_quality,data_source
```

### 6.2. Train và đánh giá

- Tận dụng LightGBM hiện tại và các đặc trưng trễ, trung bình trượt, thứ trong tuần, mùa vụ.
- Giữ chia tập theo thời gian, không xáo trộn quá khứ và tương lai.
- Module hiện yêu cầu khoảng 22 tháng để thực hiện đầy đủ benchmark; bộ seed 730 ngày đáp ứng thiết kế này.
- Báo cáo MAE theo ngày, theo kho/sản phẩm và sai số tổng lượng xuất 7 ngày.
- Chuỗi thiếu lịch sử trả `INSUFFICIENT_DATA`; không thay bằng dự báo `0`.
- Sau khi đánh giá, refit model triển khai trên dữ liệu hợp lệ đã quan sát.

### 6.3. Lưu artifact

```text
AI/demand/runtime/runs/<run-id>/artifacts/
  model.joblib
  metrics.json
  metadata.json
```

Metadata gồm phiên bản model, ngày chốt dữ liệu, nguồn dữ liệu, checksum, danh sách đặc trưng, phiên bản thư viện và thời điểm train.

- Ghi artifact ra file tạm, nạp lại và kiểm tra trước khi công bố.
- Chỉ cập nhật con trỏ model đang dùng sau khi job hoàn tất thành công.
- Khi train lỗi, tiếp tục dùng model trước và ghi nhận lỗi.
- Runtime được lưu trên volume bền vững khi chạy Docker; restart vẫn nạp được model.
- Khởi tạo model mới từ dữ liệu DB, không tiếp tục dùng ngầm artifact thuộc luồng tự sinh lịch sử cũ.

Tiêu chí hoàn thành: train lần đầu thành công từ lịch sử DB, joblib nạp lại được và suy luận không cần train lại.

## 7. Giai đoạn 4 — Lịch huấn luyện và cập nhật dự báo

### 7.1. Huấn luyện hằng tuần

Đặt scheduler tại Spring Boot:

```java
@Scheduled(cron = "0 0 5 * * MON", zone = "Asia/Ho_Chi_Minh")
```

- Chạy lúc 05:00 thứ Hai hằng tuần, chốt dữ liệu đến hết Chủ nhật.
- Train khởi tạo sau seed không làm dịch chuyển lịch tuần.
- Scheduler tạo job nền; không giữ HTTP request chờ train xong.
- Lưu kỳ chạy và khóa/lease trong DB để tránh nhiều backend cùng tạo một kỳ train.
- Phân biệt trạng thái job ở backend và worker Python, đồng bộ kết quả thay vì coi HTTP 202 là train thành công.
- Khi khởi động lại, kiểm tra kỳ còn thiếu và chạy bù một job cho kỳ gần nhất.
- Job đang bận hoặc thất bại được ghi nhận để retry có giới hạn; kiểm tra job đang chạy trước khi retry.
- Trả về thời điểm train gần nhất, lịch train tiếp theo và trạng thái lỗi cho dashboard.

### 7.2. Dự báo hằng ngày

- Tách job `TRAIN` và `PREDICT`.
- Đề xuất cập nhật dự báo lúc 00:10 hằng ngày từ dữ liệu đã chốt hôm trước, dùng joblib hiện có.
- Sau train thành công, chạy dự báo bằng model mới.
- Nút “Cập nhật dự báo” chỉ tạo job suy luận; bỏ cơ chế tự train theo tuổi model khi mở trang.
- Lượng cần nhập và cảnh báo được tính lại khi tải dữ liệu hoặc sau giao dịch nhập/xuất, dựa trên tồn mới.
- Kết quả phải có `modelVersion`, `dataCutoff`, `forecastDate` và `stockAsOf` để nhận diện dữ liệu cũ.
- Nếu chưa có dự báo cho ngày hiện tại, hiển thị trạng thái cũ/đang cập nhật thay vì trình bày như kết quả mới.

Tiêu chí hoàn thành: không cần truy cập dashboard vẫn train đúng lịch; chạy bù được sau downtime và không chạy trùng kỳ.

## 8. Giai đoạn 5 — Tính lượng nhập cần thiết

### 8.1. Quy tắc

Với từng cặp kho/sản phẩm:

- `S`: tồn hiện tại trong kho.
- `B`: tồn an toàn, mặc định bằng 0.
- `E[d]`: lượng xuất dự báo của ngày `d`, giữ độ chính xác nội bộ đến khi tính nhu cầu nhập.
- `R[d]`: nhu cầu nhập lũy kế đến ngày `d`.
- `Q[d]`: lượng nhập riêng ngày `d`.

```text
E[hôm nay còn lại] = max(0, dự báo xuất cả ngày − đã xuất hôm nay)

R[d] = ceil(max(0, tổng E từ hiện tại đến d + B − S))

Q[d] = R[d] − R[d−1], với R[trước hôm nay] = 0
```

- Chart 2 hiển thị `Q[d]`, tránh cộng lặp lượng thiếu lũy kế.
- Tổng cần nhập 7 ngày bằng tổng `Q[d]` trong cửa sổ 7 ngày, cũng bằng `R[ngày cuối]`.
- Các giao dịch nhập đã hoàn tất đã nằm trong tồn `S`, không cộng thêm lần nữa.
- Đây là kế hoạch giả định nhập đủ từng ngày; không tự ghi lượng nhập đề xuất thành tồn thực tế.
- Nếu thiếu dự báo hợp lệ hoặc chưa xác định được tồn theo kho, trả trạng thái chưa đủ dữ liệu thay vì lượng nhập bằng 0.

### 8.2. Ví dụ

Tồn hiện tại 20, tồn an toàn 0, lượng xuất dự báo ba ngày là 8, 9 và 10:

| Ngày | Xuất dự báo | Xuất lũy kế | Nhập cần lũy kế | Nhập riêng ngày |
|---|---:|---:|---:|---:|
| Ngày 1 | 8 | 8 | 0 | 0 |
| Ngày 2 | 9 | 17 | 0 | 0 |
| Ngày 3 | 10 | 27 | 7 | 7 |

## 9. Giai đoạn 6 — API và dashboard

### 9.1. API dự kiến

Giữ quyền ADMIN/MANAGER cho module dự báo, phân biệt với quyền thao tác nhập/xuất hiện có.

| API | Mục đích |
|---|---|
| `GET /api/demand/catalog` | Danh sách cặp kho/sản phẩm hợp lệ |
| `POST /api/demand/forecast-jobs` | Tạo job suy luận bằng model hiện có |
| `GET /api/demand/jobs/{id}` | Trạng thái job |
| `GET /api/demand/forecasts` | Lịch sử, dự báo xuất và metadata |
| `GET /api/demand/replenishment` | Lượng nhập từng ngày, tổng 7 ngày và hai nhóm cảnh báo |

- Thay lời gọi `demo-jobs` trên frontend bằng hợp đồng mới.
- Trả trạng thái rõ ràng khi chưa có model, thiếu lịch sử, worker lỗi hoặc kết quả cũ.
- Tính công thức nhập tại backend để chart, bảng và cảnh báo dùng cùng quy tắc.
- Dữ liệu cảnh báo có thời điểm chụp tồn; dữ liệu biểu đồ có mã run dự báo để tránh ghép nhầm phiên bản.

### 9.2. Giao diện

- Bộ lọc kho và sản phẩm cho biểu đồ.
- Chart 1: lịch sử xuất và dự báo xuất từng ngày.
- Chart 2: lượng nhập cần bổ sung từng ngày, cùng khoảng thời gian với chart 1.
- Thẻ tổng quan: tồn hiện tại, xuất dự báo 7 ngày, cần nhập hôm nay và cần nhập 7 ngày.
- Hiển thị nguồn dữ liệu, thời điểm train gần nhất, lịch train tiếp theo và thời điểm cập nhật tồn.

### 9.3. Danh sách cảnh báo

Hai danh sách lấy tất cả sản phẩm thuộc phạm vi kho đang chọn; không bị giới hạn vào sản phẩm đang xem chart.

1. **Cần nhập hôm nay:** `Q[hôm nay] > 0`.
2. **Cần nhập trong 7 ngày:** tổng `Q[d]` của hôm nay và 6 ngày tiếp theo lớn hơn 0.

Mỗi dòng gồm kho, SKU, tên sản phẩm, tồn hiện tại, lượng xuất dự báo trong kỳ, tồn an toàn, lượng cần nhập và ngày đầu cần bổ sung theo ngưỡng cấu hình. Sắp xếp hàng cần nhập hôm nay trước, sau đó theo ngày cần bổ sung.

Danh sách tuần đã bao gồm hôm nay; không cộng tổng hai danh sách với nhau. Phân biệt rõ thiếu hàng dự kiến với trường hợp chỉ cần bổ sung để giữ tồn an toàn.

## 10. Kiểm thử và tiêu chí nghiệm thu

### Database và nhập/xuất

- [x] V12 chạy được trên DB mới và DB đang ở V11.
- [x] Dữ liệu cũ không bị gán kho tùy tiện.
- [x] V13 tự nạp đủ catalog và lịch sử sau V12, không cần chạy seed thủ công.
- [x] Restart với DB đã ở V13 không nạp lại dữ liệu hoặc thay đổi số dư tồn.
- [x] V13 lỗi rollback phần dữ liệu chưa hoàn tất; kiểm tra tình huống catalog xung đột.
- [x] Tạo DB demo mới áp dụng được V12/V13; không tái sử dụng nhầm model của bộ dữ liệu cũ.
- [x] Tổng nhập/xuất, tồn cuối, tồn theo kho và tồn tổng khớp nhau.
- [x] Nhập/xuất chỉ ảnh hưởng kho đã chọn; xuất đồng thời không làm tồn âm.

### Model và job

- [x] Python train từ snapshot DB, không gọi hàm tự sinh lịch sử demo.
- [x] Không dùng dữ liệu tương lai và không biến ngày thiếu dữ liệu thành ngày xuất 0.
- [x] Artifact nạp lại được sau restart; dự báo thông thường không train lại.
- [x] Train lỗi giữ model trước; metadata nguồn và phiên bản nhất quán.
- [x] Scheduler chạy đúng 05:00 thứ Hai theo giờ Việt Nam.
- [x] Chạy bù sau downtime, chống chạy trùng và xử lý retry đúng trạng thái worker.

### Công thức và dashboard

- [x] Đủ hàng trả lượng nhập 0; thiếu hôm nay và thiếu cuối tuần cho kết quả đúng.
- [x] Tồn an toàn, lượng đã xuất hôm nay và lượng đã nhập hôm nay được tính đúng.
- [x] Chart 2 dùng lượng nhập từng ngày, không cộng lặp nhu cầu lũy kế.
- [x] Cửa sổ 7 ngày đúng qua ranh giới tuần/tháng/năm và không phụ thuộc múi giờ trình duyệt.
- [x] Hai danh sách cảnh báo khớp API và biểu đồ; tổng tuần bao gồm hôm nay.
- [x] Thiếu lịch sử hoặc tồn chưa xác định được hiển thị rõ, không hiển thị như đủ hàng.
- [x] Kiểm thử phân quyền API, chạy test Python/backend phù hợp và build frontend thành công.

## 11. Thứ tự triển khai

| Bước | Công việc | Điều kiện chuyển bước |
|---|---|---|
| 1 | V12, entity/repository và nghiệp vụ nhập/xuất theo kho | Giao dịch và tồn theo kho nhất quán |
| 2 | V13 seed dữ liệu mẫu và báo cáo đối soát | Tự nạp sau V12, dữ liệu đủ 730 ngày, restart không trùng |
| 3 | Adapter lịch sử, train lần đầu và lưu joblib | Artifact hợp lệ, nạp lại được |
| 4 | Tách train/predict, scheduler tuần và cập nhật dự báo ngày | Lịch chạy, khóa job, chạy bù hoạt động |
| 5 | API tính nhập và danh sách cảnh báo | Công thức và trạng thái dữ liệu được kiểm thử |
| 6 | Hai chart và cảnh báo trên dashboard | Giao diện khớp hợp đồng API |
| 7 | Kiểm thử tích hợp, cập nhật hướng dẫn local/Docker | Hoàn tất các tiêu chí nghiệm thu |

Tài liệu vận hành cần cập nhật khi triển khai: `docs/demand-forecasting.md`, `AI/demand/README.md`, `database/seeds/README.md` và cấu hình môi trường/Docker liên quan.

## 12. Kết quả triển khai và kiểm tra ngày 28/10/2026

- Đã tạo V12 và V13; V13 có 30.920 giao dịch, 24 chuỗi và 730 ngày, dùng namespace DEMAND_V13 để giữ nguyên catalog SIM_SKU cũ.
- Đã kiểm tra DB mới, nâng cấp từ V11, bỏ qua seed khi migrate lại, và rollback khi SKU mẫu xung đột.
- Đã kiểm tra nhập/xuất đúng kho, từ chối kệ thuộc kho khác, chống xuất đồng thời vượt tồn và chống nhập hai sản phẩm quá sức chứa kệ.
- 62 test backend chạy thành công, bao gồm kiểm thử xuyên suốt DB → Java HTTP → Python train → joblib → lượng nhập/cảnh báo.
- 21 test Python chạy thành công; job PREDICT nạp artifact mà không train lại, kể cả khi model quá 7 ngày tuổi.
- Frontend build thành công; lint không có lỗi, còn các cảnh báo về biến không sử dụng và React effect. Chưa kiểm tra trực quan trên trình duyệt.
- Đã cập nhật hướng dẫn local/Docker và endpoint dự báo trong Postman.
- Model kiểm chứng được lưu tại `AI/demand/runtime/integration-live/runs/f065d185-a601-46f3-9ba9-6ca7939dc647/artifacts/model.joblib` (runtime không commit).

Các lần migrate/train trên đây dùng MySQL và worker kiểm thử riêng, không áp dụng vào DB đang dùng. Khi khởi động backend với cấu hình DB/ML thực tế, Flyway tự chạy V12/V13 và bộ điều phối tạo job train khởi tạo tương ứng. Xem [hướng dẫn chạy](demand-forecasting.md).
