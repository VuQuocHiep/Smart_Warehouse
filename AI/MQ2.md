# Dùng AI với MQ-2 ADC

Giữ MQ-2 nối như hiện tại; không quy đổi ADC thành TVOC/ppm. Firmware gửi `temperature`, `humidity`, `gas` (ADC 12 bit) và xuất dòng `MQ2_SAMPLE:` qua USB. Model mới học ba feature này từ dữ liệu đo thực. Model TVOC cũ được giữ riêng.

## 1. Nạp firmware và thu dữ liệu

Mở firmware bằng Arduino IDE. Cài DHT sensor library, Adafruit Unified Sensor và ArduinoJson 7; chọn board ESP32 và cổng USB. Sửa Wi-Fi, IP server, `deviceKey` và `deviceCode` trùng thiết bị giám sát của Kho 1, rồi nạp. ESP gắn trực tiếp với kho, không phụ thuộc kệ A1. Migration V6 chuyển mã thiết bị từng nhận dữ liệu sang Kho 1, mặc định `ESP_ENV_01` khi chưa có lịch sử. Các kệ dùng chung số đo kho. Firmware không có ngưỡng nhiệt độ/gas cố định để giả kết quả AI.

Đóng Serial Monitor trước khi thu bằng Python. Mỗi cổng USB chỉ nên có một chương trình đọc. Đã tạo môi trường `AI/.venv` dùng thư viện Python hiện có và cài pyserial. Nếu cài trên máy khác, tạo venv và cài `AI/requirements.txt`.

Từ PowerShell tại `C:\BTL_IOT`:

```powershell
.\AI\.venv\Scripts\python.exe AI/mq2.py ports
.\AI\.venv\Scripts\python.exe AI/mq2.py collect --port COM4 --session observed_01 --seconds 600
```

Thay `COM4` bằng cổng thực tế. Dữ liệu ghi vào `AI/data/mq2_readings.csv`, nhãn mặc định `UNLABELED`. Không tự gán NORMAL chỉ vì số ADC thấp. USB vẫn thu được khi Wi-Fi/backend lỗi. Mở cổng có thể reset ESP; loại bỏ dữ liệu lúc khởi động/chưa ổn định trước khi gán nhãn hoặc train. Số giây trong lệnh chỉ là thời lượng ghi ví dụ, không phải tiêu chí cảm biến đã ổn định. Ctrl+C kết thúc và giữ các hàng đã ghi.

Mỗi lần thay điều kiện đo, tạo một session mới; không dùng nhiều tên cho các đoạn sát nhau của cùng một lần thử để lách phép tách train/test. Giữ cùng cảm biến, nguồn cấp, mạch đo và vị trí khi thu và sử dụng model.

## 2. Gán nhãn từ quan sát độc lập

Sau khi xác nhận điều kiện thực tế của cả phiên, dừng collector rồi gán nhãn:

```powershell
.\AI\.venv\Scripts\python.exe AI/mq2.py label --session observed_01 --label NORMAL --note "Mo ta dieu kien binh thuong da xac nhan cua phien do"
.\AI\.venv\Scripts\python.exe AI/mq2.py status
```

Các nhãn: NORMAL (0), WARNING (1), DANGER (2), EMERGENCY (3). Chỉ gán những cấp có tiêu chí và bằng chứng tương ứng. Lệnh trên là ví dụ thao tác; không phải xác nhận phiên của bạn đang bình thường. Không tạo khí rò rỉ hoặc đám cháy để lấy dữ liệu. Các tình huống bất thường cần bộ dữ liệu/điều kiện thử được kiểm soát với giám sát phù hợp.

Có thể bắt đầu với NORMAL/WARNING; model hai lớp chỉ phát lệnh đèn xanh hoặc đèn đỏ, không tự sinh cấp bật quạt/còi. Muốn các cấp 2/3 phải có dữ liệu đã gán nhãn tương ứng. Không gán nhãn từ chính ngưỡng ADC tự đặt rồi báo đó là mức nguy hiểm được AI xác định.

## 3. Train và đánh giá

```powershell
.\AI\.venv\Scripts\python.exe AI/mq2.py train
```

Công cụ yêu cầu NORMAL và ít nhất một lớp cảnh báo, mỗi lớp tối thiểu ba session độc lập để chia train/validation/test. Đây chỉ là điều kiện cho phép chia tập, không chứng minh dữ liệu đủ hay mô hình đáng tin cậy. Các hàng UNLABELED bị bỏ qua và số lượng được báo lại. Không tạo mẫu giả thay dữ liệu còn thiếu. Không trộn nhiều `deviceCode` trong một model.

Kết quả:

- `AI/models/mq2_risk_model.joblib`: pipeline RandomForest 300 cây với metadata `MQ2_ADC` và thiết bị đã đo.
- `AI/models/mq2_risk_model.metrics.json`: thống kê từng lớp, session từng tập, balanced accuracy, confusion matrix và precision/recall.
- `AI/models/mq2_risk_model.test.csv`: tập test độc lập để đối chiếu Java/Python.

Kiểm tra recall và các trường hợp bỏ sót trên validation/test, đánh giá thêm ở điều kiện sử dụng thực trước khi dùng lệnh điều khiển. Độ tin cậy do model trả về không thay thế đánh giá này. Nếu thay dây/mạch/cảm biến thì cần thu và đánh giá lại.

## 4. Đưa model MQ-2 vào backend

Sau khi đánh giá model:

```powershell
.\AI\.venv\Scripts\python.exe AI/export_mq2.py
```

Exporter kiểm tra loại đầu vào, giữ đúng ID lớp (kể cả model chỉ có lớp 0 và 2), tạo `backend/iot/src/main/resources/ai/mq2-risk.rf.gz`, manifest SHA-256 và parity fixture từ tập test thực. Chạy kiểm thử Maven rồi khởi động lại backend với các biến DB và `IOT_INGEST_KEY` như trước. Không đổi API ESP.

Backend chọn model MQ-2 cho yêu cầu chỉ có `gas` ADC, chỉ cho thiết bị có trong metadata. Thiếu model trả `MQ2_MODEL_REQUIRED`; thiết bị chưa train trả `MQ2_DEVICE_NOT_TRAINED`. Hai trường hợp đều không có `riskLevel` và `controlAllowed=false`, nhưng vẫn lưu số đo. Khi có model đúng, phản hồi có `predictionStatus=READY`, cấp dự đoán, xác suất và lệnh; ESP điều khiển theo kết quả này. Nếu gửi thêm `gasTvocPpb`, backend dùng luồng model TVOC riêng, không coi ADC là ppb.

Firmware báo đèn đỏ nhấp nháy khi chưa có AI hoặc lỗi; quạt/còi giữ lệnh AI cuối, ban đầu LOW. Chờ HTTP/Wi-Fi có thể làm chậm nhịp đèn lỗi. Chưa compile/nạp firmware trên phần cứng trong môi trường này.

## Kiểm thử phần mềm

```powershell
.\AI\.venv\Scripts\python.exe AI/test_mq2.py --write-java-fixtures
```

Dữ liệu tổng hợp ở `test_mq2.py` chỉ để kiểm thử code, không phải dữ liệu huấn luyện sử dụng thực. Model tổng hợp được xuất duy nhất vào `src/test/resources/ai/mq2-test.rf.gz`, không vào tài nguyên chạy thật. Java đối chiếu xác suất/lớp với Python và kiểm tra rằng ADC thay đổi có thể đổi dự đoán khi nhiệt độ/độ ẩm giữ nguyên.
