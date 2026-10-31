# Biên bản nghiệm thu cloud IoT mức 3

Trạng thái: **CHƯA NGHIỆM THU CLOUD/THIẾT BỊ THẬT**. Không ghi đạt chỉ dựa trên build/test local.

## Kiểm chứng tại máy ngày 29/10/2026

- Backend: 71 test case, 70 đạt, 1 bỏ qua (live worker tùy chọn); 0 lỗi/thất bại.
  Có MySQL 8.0 thật, toàn bộ migration tới V15, kiểm tra DB mới/nâng cấp và chạy lại migration.
- Python: 26 test đạt. Frontend build đạt; kiểm tra số đo mới/cũ/chưa có dữ liệu đạt.
- `render.yaml` hợp lệ theo JSON Schema chính thức; cron UTC và khóa workflow được kiểm tra.
- Entrypoint được kiểm tra với CA test hợp lệ trong image JRE; nhập lại truststore khi khởi
  động lần hai thành công, không lỗi alias trùng. Chưa kiểm tra kết nối Aiven thật.
- HTTP smoke test với DB riêng: TRAIN tạo joblib 679.761 byte, 336 dòng dự báo cho 24 cặp;
  PREDICT giữ nguyên modelVersion/trainedAt; nhập hàng thay đổi cảnh báo theo tồn mới.
  CORS cho đúng origin/từ chối origin lạ, thiếu runner token trả 401.
- Thay container backend, giữ DB: model và dự báo còn nguyên. Runner Docker Python 3.12
  tải model từ DB, kiểm tra checksum, dự báo lại và công bố kết quả thành công.
- Docker giới hạn 512 MiB, JVM 192 MiB heap/SerialGC: working set sau smoke khoảng 418 MiB,
  cgroup memory peak khoảng 447 MiB, oom/oom_kill bằng 0. Đây là phép đo local, không phải
  cam kết RAM/độ trễ trên Render Free; chưa mô phỏng quota CPU của Render.
- Firmware đã sửa TLS/CA/NTP và tách config local; chưa biên dịch/nạp do môi trường này
  không có Arduino CLI/toolchain tại đường dẫn task đã cấu hình.

Log kiểm tra nằm trong các thư mục build/runtime bị git-ignore; không chứa secrets cloud.

## Kiểm tra cập nhật V16 ngày 31/10/2026

- Backend: 73 test case, 72 đạt, 1 bỏ qua (live worker tùy chọn); 0 lỗi/thất bại.
- Python: 27 test đạt, gồm việc từ chối model/kết quả cũ sau khi đổi nguồn lịch sử.
- MySQL 8.0 riêng biệt: DB mới, nâng cấp V11/V15 tới V16, validate checksum và chạy lại
  migration không phát sinh thay đổi. Hai nguồn cũ cùng kho/sản phẩm được gộp thành REAL;
  số giao dịch, tổng lượng xuất và số dư tồn giữ nguyên. Lịch sử tiếp tục tới hết hôm qua.
- Nhập/xuất với hàng đã nhập ban đầu dùng chung nguồn REAL; kiểm tra cạnh tranh tồn/kệ
  và rollback đạt. Cấu hình Docker/Render không còn chọn nguồn lịch sử.
- Chỉ kiểm thử trên MySQL tạm; chưa áp dụng V16 vào database ứng dụng đang chạy,
  chưa build/deploy Docker hoặc cloud cho cập nhật này.

## Nghiệm thu trên cloud và thiết bị thật

| Hạng mục | Kết quả thực tế / bằng chứng |
|---|---|
| Commit triển khai, ngày giờ, người kiểm tra | Chưa điền |
| URL frontend/backend (không ghi secrets) | Chưa tạo |
| Flyway trên Aiven tới V16 | Chưa chạy trên cloud |
| ESP32 thực, đúng deviceCode, HTTPS xác minh CA | Chưa kiểm tra |
| Điện thoại khác mạng, máy Docker local tắt | Chưa kiểm tra |
| Số đo → phân tích cloud → đèn/quạt/còi | Chưa kiểm tra |
| Điều khiển cân, nhập/xuất và bằng chứng cân | Chưa kiểm tra |
| Mất mạng, sai khóa, sai CA, số đo cũ | Chưa kiểm tra |
| Train: jobId/modelVersion/trainedAt/checksum | Chưa chạy trên cloud |
| PREDICT dùng lại model, hai biểu đồ, cảnh báo tồn mới | Chưa chạy trên cloud |
| Lịch tuần, chạy bù, retry và chống trùng | Chưa chạy trên cloud |
| Restart/redeploy vẫn giữ dữ liệu và model | Chưa chạy trên cloud |
| Render RAM đỉnh / OOM, Aiven storage/connections | Chưa đo cloud |
| Actions minutes, Render bandwidth, chi phí | Chưa đo |
| Backup và restore DB thử thành công | Chưa kiểm tra cloud |

Kết luận mức 3: **chờ bằng chứng triển khai và thiết bị thật**.
