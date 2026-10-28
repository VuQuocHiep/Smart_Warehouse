# Dự báo xuất kho và nhu cầu nhập kho

## Luồng dữ liệu

```text
MySQL: V12 schema → V13 seed (một lần)
  → Spring tổng hợp OUT theo ngày/kho/sản phẩm
  → snapshot có nguồn và ngày chốt → FastAPI /jobs
  → TRAIN: LightGBM + đánh giá theo thời gian → model.joblib
  → PREDICT: nạp model đã lưu → 14 ngày dự báo xuất
  → Spring kết hợp tồn hiện tại → lượng nhập và cảnh báo 7 ngày
  → /admin/forecast hoặc /manager/forecast
```

Lịch train do backend điều phối, không phụ thuộc việc mở dashboard:

- Khởi tạo khi chưa có model phù hợp và đã có dữ liệu đầy đủ.
- **05:00 sáng thứ Hai**, múi giờ `Asia/Ho_Chi_Minh`; chốt đến hết Chủ nhật.
- **00:10 hằng ngày** cập nhật dự báo bằng model đã lưu.
- Backend kiểm tra mỗi 30 giây để theo dõi job và chạy bù kỳ gần nhất sau downtime.
- Khóa lease trong DB và mã job bền vững ở Python ngăn chạy trùng. Job thất bại được thử lại tối đa 3 lượt gửi, cách nhau ít nhất một phút.
- Train lỗi giữ model và kết quả trước; dashboard hiển thị lỗi hoặc dữ liệu cũ.

## V12 và V13

V12 thêm kho phát sinh cho giao dịch, tồn theo kho, cấu hình tồn an toàn, thông tin độ đầy đủ lịch sử và quản lý job. Giao dịch cũ thiếu kho vẫn giữ `warehouse_id=NULL`; chỉ tồn thực sự trên kệ được phân bổ sang kho. Phần tồn chưa đối soát được sẽ không dùng để đề xuất nhập.

V13 tự tạo **12 sản phẩm `DEMAND_V13_001`–`DEMAND_V13_012`, 24 kệ tại Kho 1/2, 24 chuỗi và 30.920 giao dịch** trên 730 ngày. Catalog này có namespace riêng để giữ nguyên catalog `SIM_SKU` cũ. Ngày cuối mẫu là hôm qua theo giờ Việt Nam khi V13 lần đầu chạy. Các sản phẩm có tồn cuối 0, 40 hoặc 500 để demo các mức thiếu hàng.

Flyway chạy V13 một lần trên mỗi database. Restart không cộng tồn lại, không kéo dài lịch sử mẫu và không thay đổi các giao dịch sau seed. Không sửa V13 đã áp dụng; muốn bổ sung dữ liệu thì thêm migration mới. Muốn bắt đầu lại toàn bộ demo thì dùng database demo mới. Mọi DB dùng migration mặc định đều nhận bộ mẫu V13.

`scripts/generate_demand_v13.py` là công cụ tạo SQL cho nhà phát triển **trước khi phát hành V13**, không phải bước bắt buộc khi khởi động. Bộ dữ liệu có seed cố định 42. Nạp SQL có transaction; ID/SKU xung đột sẽ làm migration thất bại thay vì ghi đè.

## Chạy local

Khởi động MySQL theo README; cấu hình datasource và các biến môi trường ứng dụng như hiện có. Tạo môi trường Python nếu chưa có:

```powershell
python -m venv AI/demand/.venv
.\AI\demand\.venv\Scripts\python.exe -m pip install -r AI/demand/requirements.txt
```

Terminal ML:

```powershell
$env:DEMAND_SERVICE_TOKEN = '<token-noi-bo>'
.\AI\demand\start.ps1
```

Terminal backend, dùng cùng token và giữ các biến DB đang sử dụng:

```powershell
$env:DEMAND_SERVICE_ENABLED = 'true'
$env:DEMAND_SERVICE_URL = 'http://127.0.0.1:8091'
$env:DEMAND_SERVICE_TOKEN = '<token-noi-bo>'
$env:DEMAND_DATA_SOURCE = 'SIMULATED'
cd backend/iot
.\mvnw.cmd spring-boot:run
```

Nếu wrapper gặp lỗi môi trường, dùng Maven đã cài. Backend tự migrate V12/V13; sau khi ML sẵn sàng sẽ tạo job train khởi tạo. Mở dashboard bằng tài khoản ADMIN/MANAGER để theo dõi. Nút **Cập nhật dự báo** chỉ suy luận; việc mở trang không kích hoạt train.

Frontend:

```powershell
cd frontend
npm ci
npm run dev
```

Với Docker, Compose đã bật ML và mặc định chọn `SIMULATED`. Joblib và kết quả lưu tại volume `ml_data`; image không đóng gói model runtime của máy local. Xem [Docker](docker.md).

## Nguồn dữ liệu và giới hạn

V14 chuẩn hóa tên/SKU/ghi chú hiển thị: hàng hóa dùng mã `HH...`, tên hàng và tên kệ không còn tiền tố demo. Bảng `product_sku_alias` giữ mã cũ để barcode vẫn tra cứu được. V13 đã áp dụng được giữ nguyên checksum; V14 cập nhật dữ liệu một lần khi backend khởi động. Metadata nguồn gốc `data_source` và đợt seed vẫn được giữ nội bộ, không đổi dữ liệu sinh mẫu thành dữ liệu thực đã xác minh.

Biểu đồ xuất tự hiển thị 21 ngày lịch sử + 7 ngày dự báo khi chiều rộng từ 1120 px, hoặc 14 + 7 trên khung hẹp hơn. Nhãn ngày là chữ không viền; toàn bộ ngày hiển thị dùng `dd/mm`, thời gian dùng `dd/mm HH:mm`. Định dạng ISO của API và dữ liệu model không thay đổi.

- `DEMAND_DATA_SOURCE=SIMULATED` dùng ledger mẫu V13. `REAL` chỉ dùng chuỗi nghiệp vụ thật. Một model không trộn hai nguồn.
- Backend truy vấn ledger đầy đủ; không dùng danh sách 200 giao dịch trên giao diện lịch sử để train.
- Timestamp giao dịch mới và seed lưu UTC; backend cộng UTC+7 trước khi nhóm ngày. Dữ liệu cũ thiếu kho không tự được xem là lịch sử đã xác minh.
- Lịch sử mới REAL được xem là đầy đủ từ ngày sau giao dịch đầu tiên; ngày đầu chỉ có một phần ngày được bỏ qua. Ngày không phát sinh trong khoảng ledger liên tục được ghi nhận 0.
- `demand_series.complete_through` của dữ liệu mẫu được giới hạn ở ngày chốt seed. Sau ngày đó, hệ thống không tự giả định ngày trống là xuất 0; kết quả trả `STALE_DATA` nếu lịch sử chưa được bổ sung và xác minh.
- Các thao tác demo trên sản phẩm thuộc chuỗi mẫu giữ nguồn `SIMULATED`; chúng không tự mở rộng mốc xác minh lịch sử. Để demo nhiều ngày cần bộ dữ liệu mới hoặc cập nhật phạm vi đã xác minh bằng migration bổ sung.
- Chế độ REAL cần lịch sử đã xác minh đủ dài: benchmark hiện tại cần khoảng 22 tháng; suy luận bằng model chung cần tối thiểu 90 ngày cho chuỗi mới.
- Tối đa 64 cặp mỗi model, một worker và một job tại một thời điểm. Dùng một Uvicorn worker.
- Dữ liệu mẫu không chứng minh độ chính xác trên nhu cầu thực tế. Model dự báo lượng xuất đã quan sát, chưa phản ánh nhu cầu bị mất do hết hàng.

## Artifact

```text
AI/demand/runtime/
  active.json
  runs/<job-id>/
    request.json
    job.json
    daily.csv
    result.json
    artifacts/
      model.joblib            # chỉ job TRAIN
      metrics.json           # chỉ job TRAIN
      metadata.json
      forecast_14_days.csv
```

Metadata lưu dataset ID, nguồn, checksum snapshot, ngày chốt và phiên bản thư viện. Con trỏ chỉ đổi sau khi kết quả hoàn tất. PREDICT đọc artifact của lần train trước; train không diễn ra ngầm trong job dự báo. Dataset ID thay đổi khi tạo lại DB demo sẽ khiến model cũ không được dùng nhầm.

Không xóa run mà `active.json.model.runId` đang tham chiếu. Chỉ nạp joblib tin cậy và giữ cùng phiên bản dependency khi chuyển artifact.

## Lượng nhập và cảnh báo

Dashboard hiển thị 7 ngày gồm hôm nay và 6 ngày kế tiếp. Chart 1 thể hiện lượng xuất; chart 2 thể hiện lượng nhập riêng mỗi ngày. Model giữ số thực nội bộ; giao diện làm tròn để hiển thị, lượng nhập làm tròn lên sau khi cộng nhu cầu.

```text
xuất còn lại hôm nay = max(0, dự báo cả ngày − đã xuất hôm nay)
nhập lũy kế(d) = ceil(max(0, tổng xuất còn lại đến d + tồn an toàn − tồn hiện tại))
nhập riêng(d) = nhập lũy kế(d) − nhập lũy kế(d−1)
```

Mặc định tồn an toàn là 0. Có thể cấu hình bằng bảng `demand_policy` theo kho/sản phẩm. Giai đoạn này giả định hàng bổ sung nhận trong ngày; chưa có đơn mua đang về hoặc thời gian giao hàng. Nhập thực tế đã nằm trong tồn hiện tại, không cộng lại.

Hai danh sách cảnh báo lấy tất cả sản phẩm theo bộ lọc kho. Tổng tuần bao gồm hôm nay; không cộng hai tổng với nhau. Tồn chưa phân bổ hoặc dự báo thiếu/cũ có trạng thái riêng, không bị biến thành đề xuất nhập 0. Backend tính lại theo tồn hiện tại trên mỗi lần tải, dashboard làm mới mỗi 15 giây.

## API

Các API người dùng yêu cầu JWT ADMIN/MANAGER:

| API | Chức năng |
|---|---|
| `GET /api/demand/catalog` | Cặp kho/sản phẩm từ kệ, tồn và chuỗi lịch sử |
| `POST /api/demand/forecast-jobs` | Tạo job PREDICT, trả HTTP 202 |
| `GET /api/demand/jobs/{uuid}` | Trạng thái worker |
| `GET /api/demand/forecasts` | Run dự báo công bố gần nhất |
| `GET /api/demand/replenishment` | Tồn, lượng nhập từng ngày, cảnh báo ngày/tuần và trạng thái dữ liệu |
| `GET /api/demand/status` | Các job gần nhất và lịch train tiếp theo |

Python nội bộ dùng `X-Service-Token`: `GET /health`, `POST /jobs`, `GET /jobs/{uuid}`, `GET /forecasts/latest`. Request job gồm `jobId`, `action=TRAIN/PREDICT` và snapshot chứa `pairs,rows,dataCutoff,dataSource,datasetId`. Endpoint demo tự sinh lịch sử đã được thay thế.

Phiếu `POST /inventory/stock-in`, `stock-out`, `placement-suggestions` yêu cầu `warehouseId`. STAFF được đọc `GET /inventory/warehouses` và `GET /inventory/warehouse-stock` để chọn kho và xem tồn, vẫn không được truy cập module dự báo.

## Kiểm tra

```powershell
.\AI\demand\.venv\Scripts\python.exe -m unittest discover -s AI/demand -p 'test_*.py' -v
cd backend/iot
mvn test
```

Test backend cần datasource hợp lệ cho `IotApplicationTests`. Để kiểm tra migration trên các schema tạm, đặt `MYSQL_TEST_URL`, `MYSQL_TEST_USER`, `MYSQL_TEST_PASSWORD` trỏ đến máy MySQL test có quyền tạo/xóa schema. `MySqlBootstrapTest` chỉ xóa schema ngẫu nhiên do chính test tạo, không xóa DB trong URL. Test kiểm tra cả DB mới và nâng cấp từ V11.

Để xuất snapshot từ DB test, đặt thêm `DEMAND_SNAPSHOT_OUTPUT` là đường dẫn tuyệt đối trước khi chạy `MySqlBootstrapTest`. Sau đó train kiểm chứng bằng worker HTTP trong tiến trình:

```powershell
.\AI\demand\.venv\Scripts\python.exe AI/demand/smoke_test.py --snapshot AI/demand/runtime/integration/snapshot.json
```

Smoke test lưu model vào `runtime/integration-worker` và chứng minh job PREDICT tái sử dụng artifact. Test Java xuyên suốt `DemandForecastLiveTest` yêu cầu một DB test đã migrate, worker Python riêng tại `DEMAND_LIVE_TEST_URL` và token `DEMAND_SERVICE_TOKEN`; test tạo train thật và kiểm tra lượng nhập/cảnh báo.
