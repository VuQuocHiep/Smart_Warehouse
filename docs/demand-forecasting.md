# Tích hợp thử dự báo nhu cầu xuất kho

## Phần đã tích hợp

```text
Admin/Manager → Spring Boot /api/demand
                    ↓ danh mục kho/sản phẩm UUID từ kệ hiện tại
               Python FastAPI (127.0.0.1:8091)
                    ↓ sinh 730 ngày lịch sử mô phỏng
               joblib còn hạn → dự báo 14 ngày
               Chưa có model / đủ 7 ngày → train + validation + test → lưu joblib
                    ↓ lưu kết quả theo run và công bố run thành công
               Spring Boot → dashboard dự báo
```

Màn hình tại `/admin/forecast` và `/manager/forecast`, menu “Dự báo nhu cầu”. Staff không được gọi các API này. Token nội bộ chỉ ở backend/Python, không gửi về trình duyệt.

Module lấy danh mục từ `WarehouseEntity`, `ProductEntity`, `MonitoredShelf`, dùng `warehouseId`/`productId` dạng chuỗi UUID. Các cặp kho/sản phẩm được khử trùng giữa nhiều kệ; bỏ sản phẩm/kho đã xóa mềm. Không dùng mã SIM làm định danh cho hàng thật, không ghi lượng xuất giả vào database.

Lịch sử đang là **mô phỏng**, sinh độc lập theo ID+seed bằng Negative Binomial với 6 nhóm nhu cầu. SKU/người dùng đổi tên không đổi định danh chuỗi. Dữ liệu luôn đủ hàng; chưa phản ánh thiếu hàng hoặc đơn đang về. UI luôn ghi rõ nguồn mô phỏng.

## Khởi động
Backend và database hiện có phải khởi động được trước. Tích hợp này không chạy/sửa migration V1–V7 và không khắc phục sai lệch schema UUID/BIGINT có sẵn. Không chạy lại V1 chứa `DROP DATABASE` để thử module.

Từ thư mục gốc `Smart_Warehouse`, tạo môi trường Python riêng (đã kiểm tra với Python 3.14):

```powershell
python -m venv AI/demand/.venv
.\AI\demand\.venv\Scripts\python.exe -m pip install -r AI/demand/requirements.txt
```

Terminal Python:

```powershell
$env:DEMAND_SERVICE_TOKEN = '<chuoi-bi-mat-noi-bo-do-ban-chon>'
.\AI\demand\start.ps1
```

Terminal backend, dùng **cùng token**, giữ nguyên các biến DB/IOT đang sử dụng:

```powershell
$env:DEMAND_SERVICE_ENABLED = 'true'
$env:DEMAND_SERVICE_URL = 'http://127.0.0.1:8091'
$env:DEMAND_SERVICE_TOKEN = '<cung-chuoi-bi-mat-voi-python>'
cd backend/iot
.\mvnw.cmd spring-boot:run
```

Nếu Maven wrapper của máy báo `Cannot index into a null array`, dùng Maven đã cài để chạy `mvn spring-boot:run`; đây là lỗi wrapper hiện có. Không cần sửa wrapper để dùng module.

Terminal frontend:

```powershell
cd frontend
npm ci
npm run dev
```

1. Đăng nhập ADMIN/MANAGER.
2. Tạo sản phẩm nếu chưa có; gán sản phẩm vào kệ của kho.
3. Mở “Dự báo nhu cầu”: trang tự tạo tác vụ cập nhật dự báo. Có thể chọn “Cập nhật dự báo” để chạy lại bước dự đoán.
4. Chờ trạng thái `SUCCEEDED`, chọn kho/SKU để xem lịch sử 28 ngày và dự báo 14 ngày.

Service bị tắt mặc định để backend vẫn hoạt động khi không cài Python. API trả 503 nếu chưa bật/token chưa cấu hình/Python chưa chạy; dashboard hiển thị thông báo thay vì dựng dữ liệu giả phía frontend.

## Hợp đồng API

| Spring Boot | Quyền / hành vi |
|---|---|
| `GET /api/demand/catalog` | ADMIN/MANAGER; danh mục cặp kho/sản phẩm đang được gán |
| `POST /api/demand/demo-jobs` | ADMIN/MANAGER; tạo job, HTTP 202; không nhận ID do browser tự cung cấp |
| `GET /api/demand/jobs/{uuid}` | ADMIN/MANAGER; trạng thái QUEUED/RUNNING/SUCCEEDED/FAILED/INTERRUPTED |
| `GET /api/demand/forecasts` | ADMIN/MANAGER; run thành công gần nhất, history, forecasts, metrics |

Python dùng `X-Service-Token` cho cả health và API. Chạy `uvicorn` với **1 worker**, bind loopback. Tác vụ chạy bằng executor riêng nên API không chờ train xong. Tối đa 64 cặp; các truy cập đồng thời có cùng request dùng chung job, request khác nhận 409. Kết quả lỗi không thay thế run tốt trước đó. Nếu Python bị tắt khi job đang chạy, trạng thái được đọc là `INTERRUPTED`; mở lại trang hoặc bấm cập nhật để chạy lại.

Mỗi job sinh dữ liệu kết thúc vào hôm qua theo UTC+7, ghi `daily.csv` và dự báo vào `AI/demand/runtime/runs/<uuid>/`. Dữ liệu của cùng một ngày/ID/seed không đổi khi cửa sổ lịch sử dịch chuyển. Chỉ job huấn luyện mới tạo `artifacts/model.joblib` và báo cáo đánh giá; job dự đoán đọc trực tiếp artifact của lần huấn luyện gần nhất và dùng lịch sử mới.

Con trỏ `active.json` lưu riêng run kết quả và run mô hình, cùng `trainedAt` (UTC) và seed. Tuổi mô hình tính từ thời điểm hoàn thành huấn luyện, không tính từ ngày dữ liệu hoặc lần dự đoán gần nhất. Restart service vẫn giữ chu kỳ. Đủ 7 × 24 giờ, **lần truy cập tiếp theo** kích hoạt huấn luyện lại; không có lịch chạy nền khi không ai truy cập. Thiếu artifact hoặc run cũ chưa có metadata thì huấn luyện khởi tạo. CLI `demand_model.py train` vẫn là lệnh huấn luyện thủ công, không áp dụng lịch service.

`modelTrainedAt`, `nextTrainingAt` và `modelAction` (`TRAINED`/`REUSED`) được trả trong kết quả. Metrics vẫn thuộc lần train/test gần nhất. Cặp kho/SKU mới có đủ 90 ngày lịch sử dùng LightGBM chung; thiếu lịch sử trả INSUFFICIENT_DATA. Seed khác bị từ chối trong job nếu mô hình chưa đến hạn (dashboard luôn dùng seed 42).

Con trỏ chỉ cập nhật sau khi kết quả hoàn chỉnh; lưu file tạm rồi rename khi công bố. Huấn luyện/dự đoán lỗi vẫn giữ kết quả và mô hình trước đó. Runtime không được commit; chưa có chính sách tự xóa run cũ. Không xóa run mô hình mà `active.json.model.runId` đang tham chiếu.

## Mô hình và giới hạn

- LightGBM Poisson và L1, mỗi mô hình 200 cây, học chung các kho/SKU; so với trung bình 28 ngày, seasonal naive và trung bình theo thứ trong 4 tuần gần nhất.
- Dự báo trực tiếp horizon 1–14, đặc trưng chỉ đến hết ngày gốc.
- 18 tháng train ban đầu, 3 cửa sổ validation theo tháng với tập train mở rộng; phần còn lại là test độc lập. Chọn riêng LightGBM Poisson hoặc L1 cho từng kho/SKU theo MAE validation, kể cả khi baseline tốt hơn. Baseline chỉ dùng đối chiếu báo cáo, không dùng xuất dự báo. Chuỗi mới có đủ 90 ngày dùng mô hình chung; ít hơn 90 ngày trả INSUFFICIENT_DATA. Báo cáo thêm sai số tổng lượng xuất 7/14 ngày.
- Đặc trưng chỉ lấy lịch sử đến ngày gốc và lịch của ngày đích; gồm lượng xuất trễ, trung bình/độ lệch chuẩn/tỷ lệ ngày không xuất 7/28 ngày, thứ, tháng, chu kỳ năm và trung bình cùng thứ. Chưa tích hợp lịch nghỉ lễ.
- Artifact v3 chỉ chọn LightGBM để xuất dự báo. Khởi động lại Python; lần cập nhật dự báo tiếp theo tự huấn luyện thay model cũ dù chưa đủ 7 ngày. Kết quả cũ được giữ nếu huấn luyện mới thất bại. Nguồn lịch sử và seed nội bộ không thay đổi khi chỉnh nhãn giao diện.
- Cuối cùng refit artifact triển khai trên toàn bộ dữ liệu; metrics test thuộc model trước refit.
- Mỗi lần cập nhật tạo một run dự đoán; tái sử dụng joblib và chỉ retrain khi đủ 7 ngày. Chưa query lịch sử xuất thật, chưa tính lượng nhập hoặc tạo đơn mua hàng.
- CSV REAL offline dùng cùng khóa UUID và data_source rõ ràng; cần adapter từ nghiệp vụ nhập/xuất sau khi nghiệp vụ và schema được thống nhất.
- Kết quả mô phỏng không chứng minh độ chính xác trên nhu cầu thực tế. Không dùng kết quả này tự động đặt hàng.

## Kiểm tra

```powershell
.\AI\demand\.venv\Scripts\python.exe -m unittest discover -s AI/demand -p 'test_*.py' -v
.\AI\demand\.venv\Scripts\python.exe AI/demand/smoke_test.py
```

Smoke test dùng danh mục UUID fixture, train model thật, kiểm tra HTTP job và kết quả; không cần database hoặc sửa dữ liệu chương trình.

Backend:

```powershell
cd backend/iot
mvn '-Dtest=DemandForecastServiceTest,DemandForecastAuthorizationTest' test
```

Tùy chọn kiểm tra Java gọi Python thật: bật Python service trên một port/thư mục runtime thử nghiệm, đặt `DEMAND_LIVE_TEST_URL` trỏ tới service đó và `DEMAND_SERVICE_TOKEN` tương ứng, chạy `mvn '-Dtest=DemandForecastLiveTest' test`. Test sử dụng repository mock và train thật; không kết nối DB nhưng sẽ công bố run fixture trên service thử nghiệm đó.
