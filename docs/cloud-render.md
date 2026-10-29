# Triển khai IoT mức 3: Render + Aiven + GitHub Actions

## Phương án và trạng thái

React chạy trên Render Static Site Free; Spring Boot chạy trên một Render Web Service Free
ở Singapore; MySQL chạy trên Aiven Free. GitHub Actions chạy Python theo batch. ESP32
gửi số đo HTTPS tới Spring Boot và nhận phản hồi điều khiển ngay, độc lập với batch dự báo.

Code, migration và cấu hình được chuẩn bị trong repo. Chưa tạo dịch vụ hoặc nạp firmware
lên thiết bị; chỉ xác nhận mức 3 sau khi hoàn thành các bước nghiệm thu cuối tài liệu.

Mục tiêu là không có phí trong hạn mức miễn phí. Render Free có 512 MB RAM, dùng chung
750 giờ/tháng trong workspace, ngủ khi không có truy cập 15 phút và cold start có thể khoảng
một phút. Không tạo service Python Render thứ hai và không dùng ping giả để giữ backend thức.
Aiven Free có 1 GB RAM, 1 GB storage, có thể bị tắt khi không hoạt động; không có SLA.
GitHub Free có 2.000 phút Actions/tháng cho private repository. Kiểm tra Billing của từng
tài khoản và không tự bật trả phí. Chi phí/hạn mức có thể thay đổi.

Nguồn: [Render Free](https://render.com/docs/free),
[Aiven MySQL Free](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier),
[GitHub Actions allowance](https://docs.github.com/en/billing/reference/product-usage-included).

## 1. Chuẩn bị repository và Aiven

1. Đưa các thay đổi lên repository GitHub của bạn. Workflow
   `.github/workflows/demand-batch.yml` phải có trên default branch. Nếu branch khác `main`,
   đặt `DEMAND_GITHUB_REF` theo branch đó. Không commit `.env`, file Wi-Fi hoặc token.
2. Trên Aiven chọn **MySQL → Free**, không chọn trial trả phí. Chọn Singapore hoặc vùng
   gần Render nhất trong các lựa chọn miễn phí đang có. Ghi lại hostname, port, username,
   password, database; mặc định blueprint dùng `defaultdb`.
3. Download **CA certificate** của project. Backend dùng `sslMode=VERIFY_IDENTITY` và
   truststore riêng để xác minh cả CA và hostname; không thêm `useSSL=false`.
4. Khởi tạo trên database mới, trống. Flyway tự chạy baseline `B1`, V2–V15; không chạy
   `V1__initial_database.sql` thủ công, không dùng `baseline-on-migrate=true` với DB cloud.
   V13 seed 730 ngày tới hôm qua tại thời điểm migration; V14 chuẩn hóa tên; V15 lưu model.

Nguồn: [TLS trên Aiven](https://aiven.io/docs/platform/concepts/tls-ssl-certificates).

## 2. Deploy Render bằng Blueprint

Trên Render: **New → Blueprint → chọn repository → render.yaml**.
Blueprint chỉ tạo một backend Free và một Static Site; không tạo database hoặc disk trả phí.
Các tên có thể được Render thêm hậu tố. Dùng **URL thực tế**, không đoán từ tên trong YAML.

Nhập/cập nhật biến backend trong Render Environment:

| Biến | Giá trị |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` | Thông tin MySQL từ Aiven |
| `DB_NAME` | Tên database, mặc định `defaultdb` |
| `AIVEN_CA_PEM` | Toàn bộ CA PEM tải từ Aiven, gồm BEGIN/END và newline thật |
| `BOOTSTRAP_ADMIN_EMAIL` | Email admin muốn tạo |
| `BOOTSTRAP_ADMIN_PASSWORD` | Mật khẩu riêng, ít nhất 12 ký tự |
| `CORS_ALLOWED_ORIGINS` | URL frontend HTTPS, không có dấu `/` cuối; nhiều origin ngăn cách bằng dấu phẩy |
| `DEMAND_GITHUB_REPOSITORY` | `owner/repository`, mặc định repo hiện tại |
| `DEMAND_GITHUB_REF` | Branch chứa workflow, mặc định `main` |
| `DEMAND_GITHUB_TOKEN` | Fine-grained PAT chỉ cấp repo trên, quyền **Actions: Read and write** |

Blueprint tự tạo `JWT_SECRET`, `IOT_INGEST_KEY`, `DEMAND_BATCH_TOKEN`. Không đưa chúng vào
frontend; chỉ copy `IOT_INGEST_KEY` sang firmware và `DEMAND_BATCH_TOKEN` sang Actions Secrets.
Khóa JWT phải ổn định giữa các deploy; đổi khóa sẽ buộc người dùng đăng nhập lại.

`APP_CLOUD=true`, profile `cloud`, `TZ=Asia/Ho_Chi_Minh`, JVM 192 MB heap/SerialGC
và tối đa 20 luồng Tomcat đã được đặt sẵn.
Không ghi đè `SPRING_DATASOURCE_URL` bằng URL local. Container tạo truststore trong `/tmp`
từ CA khi khởi động; chỉ truststore là tạm thời, dữ liệu ứng dụng nằm trên Aiven.

Sau khi có URL backend, đặt biến của Static Site:

```text
VITE_API_BASE_URL=https://<backend-thuc-te>.onrender.com
```

Không thêm `/backend`: prefix này chỉ dùng với Nginx của Docker local. Rebuild Static Site
sau khi đổi biến Vite. SPA fallback `/* → /index.html` đã có trong blueprint. Nếu URL frontend
chưa biết tại lần tạo đầu, cập nhật lại `CORS_ALLOWED_ORIGINS` bằng URL thật rồi deploy backend.

Kiểm tra `https://<backend>/healthz` trả `{"status":"UP"}`, log Flyway tới V15, mở frontend,
đăng nhập bằng admin vừa cấu hình, thử refresh một đường dẫn bên trong app.
Bootstrap chỉ tạo admin khi email chưa tồn tại, không đặt lại mật khẩu tài khoản cũ.

## 3. GitHub Actions và model

Trong **Repository → Settings → Secrets and variables → Actions**, thêm:

| Secret | Giá trị |
|---|---|
| `WAREHOUSE_BACKEND_URL` | URL HTTPS gốc của backend, không có `/backend` |
| `DEMAND_BATCH_TOKEN` | Đúng token cùng tên trên Render |

Enable Actions, chọn **Demand cloud batch → Run workflow** để train khởi tạo. Việc backend
dispatch trước khi workflow/secrets sẵn sàng không làm mất dữ liệu; có thể chạy thủ công lại.
Nút **Cập nhật dự báo** trên web tạo job và dispatch workflow. Chỉ ADMIN/MANAGER được dùng;
PAT không được gửi về trình duyệt. Token sai/hết hạn sẽ hiện lỗi dispatch trong trạng thái job.

Lịch UTC của workflow:

| Cron | Giờ Việt Nam |
|---|---|
| `0 22 * * 0` | 05:00 thứ Hai: train tuần mới |
| `17 22 * * 0` | 05:17 thứ Hai: kiểm tra/chạy bù |
| `10 17 * * *` | 00:10 mỗi ngày: dự báo, kiểm tra kỳ train bị lỡ |

Backend quyết định TRAIN/PREDICT theo DB, không theo riêng tên event. Chạy bù không train
lại một kỳ đã thành công. Lịch GitHub có thể trễ hoặc bị bỏ; có thể dùng Run workflow bất cứ
lúc nào để bù. Public repo không hoạt động 60 ngày có thể bị GitHub tắt lịch tự động.
Nguồn: [Scheduled workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).

Job có lease 50 phút, workflow timeout 40 phút, tối đa ba lần nhận chạy cho một job.
Runner lặp tối đa năm job/lượt. Token nhận job thay đổi khi retry; callback từ lần cũ bị từ chối.
Gửi lại claim từ cùng runner hoặc gửi lại completion đã thành công không nhân đôi dữ liệu.
Restart backend không làm mất lease, model hoặc kết quả. Runner chết sẽ được lần chạy tiếp
theo thu hồi sau khi lease hết. Job hết ba lần thử cần kiểm tra log; kỳ sau vẫn hoạt động và
ADMIN/MANAGER có thể tạo một dự báo thủ công khi còn model tương thích.

Nếu cần chạy lại chính job đã hết ba lần thử, sửa nguyên nhân trước, kiểm tra đúng jobId
trong `/api/demand/status`, rồi dùng client quản trị MySQL qua TLS để reset riêng job đó:

```sql
UPDATE demand_job
SET status='QUEUED', attempts=0, claim_token=NULL, claim_until=NULL,
    runner_id=NULL, snapshot_json=NULL, message=NULL, updated_at=UTC_TIMESTAMP(6)
WHERE job_id='<jobId-da-kiem-tra>' AND status='FAILED' AND attempts>=3;
```

Sau đó Run workflow. Không reset job RUNNING hoặc reset hàng loạt khi runner còn hoạt động.

Model `.joblib`, checksum SHA-256, phiên bản thư viện và kết quả JSON lưu trong Aiven.
Giữ hai phiên bản model gần nhất và kết quả của 14 ngày gần nhất; chỉ thay con trỏ đang dùng
trong cùng transaction lưu kết quả thành công. Train lại cùng dữ liệu có thể cho cùng model
version. Runner dùng dependency pin giống local, kiểm tra checksum trước khi load joblib;
không load model tải từ nguồn ngoài không tin cậy. Không upload snapshot/model làm artifact
công khai hoặc commit chúng vào Git. Thư mục runner là tạm và được xóa sau mỗi job.

Các API web `/api/demand/*` giữ nguyên. Nhóm runner dùng `X-Batch-Token`, thêm
`X-Claim-Token` cho từng lần chạy; trình duyệt/JWT người dùng không có quyền thay thế token này:

| API | Mục đích |
|---|---|
| `POST /internal/demand/claim` | Header `X-Runner-Id`; nhận job, snapshot, metadata hoặc IDLE |
| `GET /internal/demand/jobs/{id}/model` | Tải model cho job PREDICT đang giữ lease |
| `POST /internal/demand/jobs/{id}/complete` | Multipart `result`, `metadata`, `model` khi TRAIN |
| `POST /internal/demand/jobs/{id}/fail` | Đánh dấu lỗi, giữ kết quả trước |

V13 vẫn là lịch sử seed, nguồn `SIMULATED` được giữ trong metadata. Sau ngày seed, dữ liệu
bị giới hạn bởi `complete_through`: không tự bịa thêm ngày có xuất kho bằng 0. Nếu hết dữ liệu
đầy đủ thì trạng thái STALE_DATA/cảnh báo không đủ dữ liệu là đúng. Để dùng lịch sử thực tế,
cần tích lũy ít nhất 90 ngày đầy đủ theo series nguồn REAL và chuyển cấu hình phù hợp;
không sửa nguồn seed thành REAL để làm kết quả có vẻ mới.

## 4. ESP32 HTTPS

1. Copy `hardware/warehouse_ai/config.example.h` thành `config.local.h` trong cùng thư mục
   (file local đã bị git-ignore). File local cũ được giữ khi chuẩn bị thay đổi này.
2. Điền Wi-Fi, `BACKEND_BASE_URL` là URL HTTPS gốc của Render và `WAREHOUSE_DEVICE_KEY`
   là `IOT_INGEST_KEY`. Giữ `WAREHOUSE_DEVICE_CODE=ESP_ENV_01` khớp Kho 1 và cân.
3. Đặt `WAREHOUSE_ALLOW_LOCAL_HTTP=0` khi dùng cloud. Download/export root CA của chuỗi
   chứng chỉ **hostname backend đang dùng** qua trình duyệt có kết nối hợp lệ. Điền PEM:

```cpp
#define WAREHOUSE_ROOT_CA R"PEM(-----BEGIN CERTIFICATE-----
...noi dung root CA...
-----END CERTIFICATE-----
)PEM"
```

Không dùng CA của MySQL cho Render. Không dùng leaf cert ngắn hạn hay `setInsecure()`.
Khi nhà cung cấp thay chuỗi CA, cập nhật root CA và nạp lại firmware. NTP cần truy cập
`pool.ntp.org`/`time.google.com`; chưa đồng bộ được giờ thì thiết bị từ chối gửi HTTPS.
4. Build bằng task **ESP32: Build firmware**, upload đúng cổng COM rồi mở Serial Monitor.
   Các tác vụ HTTP dùng TLS; HX711/Preferences vẫn chạy trong loop. Cold start có thể làm
   vài request timeout; thiết bị thử lại theo các lần gửi số đo thật, không phải ping giả.
5. Xem Kho 1 cập nhật. Dữ liệu môi trường quá hai phút phải được đánh dấu cũ; cân offline
   không được lập bằng chứng cân. Lệnh cân nằm trong RAM một backend instance; sau restart
   phải gửi lại lệnh đang chờ, vé cân đã lưu DB vẫn giữ quy tắc hạn dùng/đúng phiếu.

## 5. Kiểm thử, vận hành và backup

### Kiểm thử trước khi deploy

```powershell
cd frontend
npm run build
cd ../AI/demand
./.venv/Scripts/python.exe -m unittest test_service test_demand_model test_cloud_runner
```

Backend: chạy Maven test bằng JDK 17 và MySQL test riêng; các test cloud integration tạo
schema UUID rồi xóa chính schema đó. Đặt `MYSQL_TEST_URL`, `MYSQL_TEST_USER`,
`MYSQL_TEST_PASSWORD` trỏ database test; không trỏ DB cloud hoặc DB ứng dụng đang dùng.
`MySqlBootstrapTest` kiểm tra toàn bộ migration và restart; `CloudDemandBatchIntegrationTest`
kiểm tra khóa đồng thời, retry, checksum, rollback, giữ model/kết quả và retention.

### Nghiệm thu IoT mức 3

Đã ghi kết quả local trong [biên bản kiểm chứng](cloud-acceptance.md).
`scripts/smoke_cloud_batch.py` kiểm tra HTTP thật trên backend/DB localhost thử nghiệm,
gồm train, dự báo lại, CORS/token và thay đổi tồn kho. Script từ chối hostname từ xa;
không chạy với database ứng dụng đang dùng. Sau khi backend thử nghiệm khởi động hoàn tất:

```powershell
$env:DEMAND_BATCH_TOKEN = '<token-backend-thu-nghiem>'
$env:SMOKE_ADMIN_EMAIL = '<admin-thu-nghiem>'
$env:SMOKE_ADMIN_PASSWORD = '<mat-khau-thu-nghiem>'
./AI/demand/.venv/Scripts/python.exe scripts/smoke_cloud_batch.py --url http://127.0.0.1:18081
# Sau restart/redeploy backend thử nghiệm, giữ DB và kiểm tra model đã lưu:
./AI/demand/.venv/Scripts/python.exe scripts/smoke_cloud_batch.py --url http://127.0.0.1:18081 --resume
```

Các phép thử dưới đây vẫn phải thực hiện trên cloud và thiết bị thật:

- Tắt Docker local/máy chủ local. ESP32 dùng Wi-Fi có Internet, điện thoại dùng 4G/5G.
- Mở web HTTPS, xem số đo DHT22/MQ-2 từ đúng ESP_ENV_01 và timestamp cập nhật.
- Ghi bằng chứng response READY/controlAllowed và đèn/quạt/còi phản hồi đúng mức; thử lệnh
  cân từ web, thực hiện một nhập/xuất có bằng chứng cân.
- Tắt Wi-Fi: số đo cũ phải được đánh dấu; kết nối lại phục hồi. Sai device key trả 401;
  sai root CA không gửi được HTTPS. Không giả lập số đo để thay cho thiết bị thật.
- Run workflow train khởi tạo: xác nhận job thành công, model có trong Aiven và hai biểu đồ
  có kết quả. Chạy PREDICT: giữ modelVersion/trainedAt, cập nhật generatedAt.
- Nhập/xuất thay đổi tồn kho: cảnh báo hôm nay/7 ngày phải tính lại từ tồn mới.
- Redeploy backend, chạy lại workflow: dữ liệu/model còn nguyên, không train lặp cùng kỳ.
- Lưu ảnh/log đã che secrets và điền kết quả vào `docs/cloud-acceptance.md`.

### Hạn mức và xử lý lỗi

Render Metrics: theo dõi RAM và restart/OOM trong lúc khởi động, migration, snapshot và
xem dashboard; mục tiêu RSS dưới 450 MB để có khoảng trống trong giới hạn 512 MB.
Hikari tối đa 5 connection; train chạy trên Actions, không chiếm RAM Java. Nếu OOM phải đo
và điều chỉnh, không tự nâng gói trả phí. Theo dõi Aiven storage/connection, GitHub Billing
minutes/cache và Render outbound bandwidth. Forecast batch gọi lại backend để lấy dữ liệu
thực; lịch ghép các service free không có cam kết đúng giờ hay uptime.

### Backup/restore

Backup định kỳ và trước khi đổi schema. Dùng mysqldump qua TLS, không đặt password trên
command line hoặc commit file backup. Ví dụ dùng client MySQL trên máy quản trị:

```powershell
mysqldump --host=<aiven-host> --port=<aiven-port> --user=<db-user> --password --ssl-mode=VERIFY_IDENTITY --ssl-ca=./ca.pem --single-transaction --no-tablespaces --set-gtid-purged=OFF --result-file=./warehouse-backup.sql defaultdb
```

Giữ backup mã hóa ngoài Render, tối thiểu 7 bản ngày và 4 bản tuần. Không lưu backup vào
filesystem Render. Bản dump gồm `flyway_schema_history`, model và con trỏ kết quả.
Khi restore: tạo database đích trống, tạm ngừng backend/workflow ghi dữ liệu, chạy client:

```powershell
mysql --host=<aiven-host> --port=<aiven-port> --user=<db-user> --password --ssl-mode=VERIFY_IDENTITY --ssl-ca=./ca.pem defaultdb
```

Trong MySQL dùng `SOURCE D:/duong-dan/warehouse-backup.sql;`, sau đó kiểm tra model/result
và restart backend. Nếu còn job RUNNING từ môi trường cũ, chờ lease hết rồi Run workflow.
Thực hành restore trên DB thử trước khi dùng quy trình này để khôi phục DB đang chạy.

## Docker local và phạm vi

`docker compose up -d --build` vẫn dùng MySQL + Python worker trong mạng Compose,
`DEMAND_EXECUTION_MODE` mặc định HTTP_WORKER. Không cần secrets GitHub/Aiven cho local.
Đặt JWT_SECRET trong `.env` nếu muốn phiên đăng nhập tồn tại qua restart backend;
nếu bỏ trống, local tạo khóa ngẫu nhiên trong mỗi lần khởi động. `.env.example` có các biến
admin tùy chọn; chỉ cloud bắt buộc mật khẩu riêng. RFID/trang Lịch sử riêng chưa triển khai
không được bổ sung trong đợt cloud này.
