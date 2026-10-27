# Cân HX711 5 kg trong nhập / xuất kho

## Thiết bị và cấu hình

- Toàn bộ firmware ESP32, gồm cân HX711 và giám sát môi trường, nằm trong một file `hardware/warehouse_ai/warehouse_ai.ino`.
- Giữ các chân DHT22, MQ-2, đèn, quạt và còi hiện có. HX711: **DT → GPIO16**, **SCK → GPIO21**, nối chung GND. Dùng nguồn và mức tín hiệu phù hợp module HX711 / ESP32 thực tế.
- Arduino IDE: ESP32 board package, thư viện **HX711 Arduino Library (bogde)**, **ArduinoJson 7**, **DHT sensor library**, **Adafruit Unified Sensor**.
- Sửa Wi-Fi và **`BACKEND_BASE_URL`** ngay trong `warehouse_ai.ino`. Cả `serverUrl` và `scaleServerUrl` tự ghép từ địa chỉ chung này; endpoint cân là `/api/scale/readings`. Cấu hình hiện tại là `http://172.20.10.2:8080` theo IP Wi-Fi máy tính ngày 27/10/2026; khi đổi mạng phải kiểm tra IP máy tính và sửa địa chỉ chung. Backend mặc định dùng khóa `esp32-warehouse-01`, khớp `deviceKey` trong firmware. Nếu đặt biến môi trường `IOT_INGEST_KEY`, giá trị đó sẽ thay khóa mặc định và phải khớp `deviceKey`.
- `deviceCode` mặc định `ESP_ENV_01`; backend dùng `iot.scale.device-code` (mặc định cùng giá trị).
- **Khai báo `SCALE_PLATFORM_GRAMS`** bằng khối lượng mặt mica / gá đỡ mà load cell phải chịu. Giá trị mặc định 0 chỉ là chỗ điền cấu hình; chưa tính được phần tải này cho tới khi khai báo đúng. Tổng tải dùng để kiểm tra 5 kg = phần tải khai báo + bao bì + hàng. Trừ bì không tăng khả năng chịu tải.
- `SCALE_STABLE_RANGE_GRAMS = 2` là biên dao động cho việc chờ ổn định; `ZERO_THRESHOLD = 0.5` đưa nhiễu rất nhỏ về 0. Đây là tham số lọc, khác với ngưỡng sai số đối chiếu hàng. Điều chỉnh sau khi thử thiết bị thực tế nếu cần.

Firmware `INSTANT-RAW-V3` đọc đúng một mẫu HX711 mới nhất bằng `scale.read()` khi `scale.is_ready()` trong mỗi lượt `loop()`. Khối lượng trực tiếp không qua trung vị hay trung bình, không chờ gom 7 mẫu và không còn khoảng nghỉ 300 ms. Vòng lặp kiểm tra dữ liệu mới khoảng mỗi 10 ms; tốc độ mẫu thực tế phụ thuộc HX711. Serial in tối đa 10 lần/giây, HTTP và giao diện vẫn theo chu kỳ gửi / lấy dữ liệu hiện có khoảng 1 giây.

Trừ bì và hiệu chuẩn vẫn dùng trung vị 15 mẫu (`docRAW`) để thiết lập mốc / hệ số. Khi bật nguồn phải để mặt cân trống để tự trừ bì trước khi bật Wi-Fi. Cờ ổn định yêu cầu RAW nằm trong biên tương đương 2 g ít nhất 1 giây; nó chỉ cho biết có thể nhận kết quả vào phiếu, không làm trễ hoặc thay đổi số đo trực tiếp. Vẫn giữ đưa giá trị tuyệt đối dưới 0,5 g về 0 và các kiểm tra lỗi / quá tải.

Hai task HTTP chỉ dùng bản sao số cân hoặc mẫu DHT22/MQ-2 được đưa qua hàng đợi; không truy cập HX711 hoặc Preferences. Kết quả điều khiển môi trường được chuyển về `loop()` để cập nhật đèn/quạt/còi. Mẫu cân quá 3 giây được đánh dấu `STALE`, không dùng để lập phiếu. Kiến trúc này giữ việc đọc cân độc lập với thời gian chờ backend, nhưng chưa tự chứng minh hoặc loại trừ các vấn đề nguồn, dây hoặc GPIO khi Wi-Fi hoạt động.

Thư viện được đối chiếu với [API chính thức HX711](https://github.com/bogde/HX711/blob/master/src/HX711.h).

## Chạy phần mềm

1. Khởi động backend với MySQL theo [hướng dẫn local](../README.md#local). Flyway áp dụng migration còn thiếu đến V11: V8/V9 tạo tồn và vị trí kệ, V10 thêm bằng chứng cân, V11 đặt sai số mặc định ±50 g. Không cần Python cho cân.
2. Chạy frontend như trước (`npm run dev`). Mục **Cân hàng** dùng chung cho admin, manager, staff.
3. Biên dịch / nạp lại sketch `warehouse_ai`, mở Serial 115200 baud. Chưa có hiệu chuẩn thì giao diện báo **Chưa hiệu chuẩn**; không dùng số RAW như số gram.

## Hiệu chuẩn và trừ bì

### Dùng VS Code trên máy hiện tại

Đã có `.vscode/tasks.json` dùng Arduino CLI đi kèm bản Arduino IDE đang cài, cùng board package / thư viện có sẵn. Không cần copy sketch hoặc cài PlatformIO.

1. Mở **thư mục gốc repository hiện tại** trong VS Code; các task dùng `${workspaceFolder}`. Lưu firmware trước khi build, không cần dùng đường dẫn trên máy tác giả.
2. Nhấn **Ctrl+Shift+B** để chạy `ESP32: Build firmware`. Đây chỉ là biên dịch, chưa nạp thiết bị.
3. Cắm ESP32 qua USB dữ liệu. Nhấn **Ctrl+Shift+P → Tasks: Run Task → ESP32: Xem cong COM** để xác định cổng.
4. Chạy task **ESP32: Upload firmware**, nhập đúng cổng COM. Task tự build rồi nạp. Đóng Serial Monitor trước khi nạp để nhả cổng COM.
5. Chạy task **ESP32: Serial Monitor**, nhập cùng cổng để xem log 115200 baud. Nếu cần, bấm EN/Reset trên ESP sau khi mở monitor. Dùng `t` / `c100` rồi Enter, hoặc thao tác trừ bì / hiệu chuẩn trên web.

Các task đang dùng board **ESP32 Dev Module** (`esp32:esp32:esp32`), cũng là cấu hình đã biên dịch kiểm tra. Nếu phần cứng là ESP32-S3/C3 hoặc board khác, đổi `--fqbn` ở cả task Build và Upload sang đúng board trước khi nạp. Khi chuyển sang máy khác hoặc Arduino IDE cài ở đường dẫn khác, cập nhật `command` / `--config-file` trong tasks.

### Thao tác hiệu chuẩn

1. Mỗi lần bật nguồn: để mặt cân trống, đặt cân trên bề mặt chắc chắn và chờ `Tru bi thanh cong` rồi mới đặt hàng. Nếu đang hiệu chuẩn lại, vào **Cân hàng → Trừ bì về 0 g** khi mặt cân trống.
2. Đặt quả cân có khối lượng biết trước, chờ ổn định. Nhập đúng gram và bấm **Hiệu chuẩn** trên web một lần. Không cần nhập lệnh Serial. Thiết bị nhận chuẩn 10–5.000 g nhưng tổng tải gồm mặt mica phải trong 5 kg.
3. Chờ ESP xác nhận `OK_CALIBRATED`. Hệ số và mốc mặt cân trống được lưu vào Preferences. Không hiệu chuẩn với bao bì/hàng còn trên mặt cân lúc trừ bì, vì mốc tải tổng sẽ sai.
4. Bỏ quả cân; dùng một vật chuẩn khác trong mục **Kiểm tra sai số** để thử lại. Việc vượt ngưỡng sẽ hiển thị cảnh báo đỏ.
5. Đặt bao bì rỗng, trừ bì lần nữa rồi đặt hàng vào. Khi đổi bao bì thì trừ bì lại.

ESP nhận lại `factor` từ bản test trong Preferences `hx711` và giữ hệ số qua các lần khởi động; không cần nhập `t` / `c100` mỗi lần cân. Mốc 0 được lấy lại tự động khi bật nguồn nên không để hàng hoặc bao bì trên mặt cân lúc này. Nếu trừ bì khởi động thất bại, giữ mặt cân trống cho tới khi firmware đọc được mẫu ổn định và tự thử lại. Nếu chưa có hệ số, cần hiệu chuẩn một lần trên web. Bao bì vẫn dùng nút trừ bì trên web khi cần. Nếu thay load cell, thay cơ khí hoặc mốc 0 bị trôi thì kiểm tra và hiệu chuẩn lại. Sức cân danh định 5 kg không đủ để suy ra hệ số RAW/gram của từng bộ cân; không dùng hệ số giả để hiển thị gram.

Nếu hiệu chuẩn thất bại, xem log Serial mới:

- `FAILED_REFERENCE_OR_TARE`: chưa trừ bì hoặc khối lượng vật chuẩn không hợp lệ / không còn đủ tải cho mặt mica.
- `FAILED_SENSOR_DATA`: không nhận đủ mẫu hợp lệ, tín hiệu bị lỗi hoặc thao tác trừ bì gặp quá tải.
- `FAILED_NO_REFERENCE_LOAD`: RAW sau khi đặt vật lệch dưới 200 counts so với lúc trừ bì. Kiểm tra việc đặt vật sau bước `t`, đường truyền lực qua load cell và dây nối.
- `FAILED_CALIBRATION_NOISY`: biên dao động quy đổi từ hệ số dự kiến vượt giới hạn ổn định 2 g hoặc hệ số không hợp lệ. Không dùng sai số nghiệp vụ ±50 g để bỏ qua tín hiệu hiệu chuẩn nhiễu.
- `SIGNAL_FAULT` / `FAILED_SIGNAL_FAULT`: ít nhất ba khung liên tiếp trả đúng RAW 0 và DT vẫn LOW ngay sau đọc. Không dùng các khung này làm số cân; không kết luận module hỏng chỉ từ thông báo này. Một giá trị RAW 0 thật vẫn được chấp nhận nếu DT trở lại HIGH đúng giao thức.

### Nhận biết đúng phiên bản đang nạp

Serial phải có `FIRMWARE HX711: INSTANT-RAW-V3`, tiếp theo là chân DT/SCK, hệ số đã lưu và kết quả trừ bì. Hai dòng `Backend moi truong` và `Backend can` phải cùng host, khác đường dẫn API.

Các dòng `HX711 PROBE BEFORE_WIFI`, `AFTER_WIFI` và `SUMMARY` thuộc bản chẩn đoán cũ; bản hiện tại đã bỏ chúng cùng tác vụ đọc cân riêng.

Để kiểm tra sau nạp: giữ mặt cân trống tới khi trừ bì thành công, rồi đặt vật có khối lượng biết trước và so sánh RAW/offset/gram. Nếu lỗi chỉ bắt đầu khi Wi-Fi hoạt động vẫn cần kiểm tra nguồn, loại board và đường GPIO; việc biên dịch thành công không xác nhận phần cứng đo đúng.

Nếu RAW gần như luôn 0 khi thêm/bớt tải, kiểm tra đường DT/SCK và dây từ load cell tới E+/E−, A+/A− theo sơ đồ chính module; không đoán chân theo màu dây. Nếu xuất hiện `connection refused`, kiểm tra backend đang chạy ở địa chỉ máy tính trong cả `serverUrl` và `scaleServerUrl`; không thay bằng IP của chính ESP. Lỗi HTTP và tín hiệu cân là hai phần độc lập.

Log in `raw`, `offset` và `factor` để kiểm tra cách quy đổi ra gram. `UNCALIBRATED`, `SIGNAL_FAULT`, `DISCONNECTED` là trạng thái không được dùng để nhận kết quả vào phiếu; giá trị in kèm không đại diện một lần cân hợp lệ.

Admin / manager được hiệu chuẩn và chỉnh sai số. Staff được đọc số đo, trừ bì và lấy cân vào phiếu. Lệnh web có mã xác nhận, thời hạn 30 giây và ESP tránh thực hiện lặp lại cùng mã trong lần chạy hiện tại. Nếu báo thất bại hoặc hết thời gian chờ, kiểm tra trạng thái trước khi thử lại.

## Cân khi nhập / xuất

1. Khai báo **Trọng lượng đơn vị (g)** cho sản phẩm. Ví dụ 1 hộp nặng 250 g thì nhập `250`, không nhập `0.25`.
2. Quét / thêm SKU, chỉnh số lượng; bật **Cân đối chiếu dòng này** cho dòng cần cân. Dòng không chọn cân vẫn xử lý như luồng hiện có.
3. Đặt riêng toàn bộ hàng của đúng dòng SKU lên cân. Khối lượng dự kiến = số lượng × trọng lượng đơn vị. Hệ thống giữ quản lý tồn theo số lượng nguyên, không tự đổi gram sang số lượng.
4. Chờ số đo ổn định rồi bấm **Lấy khối lượng**. Số cân phải nằm trong sai số và tổng tải không vượt 5 kg. Nếu dòng hàng vượt sức cân, chia thành các phiếu nhỏ và cân từng phiếu.
5. Xác nhận nhập / xuất. Kết quả cân lưu cùng giao dịch: số gram thực tế, dự kiến, tổng tải, sai số cho phép, người và thời điểm lấy cân.

Đổi số lượng hoặc quét thêm cùng SKU sẽ hủy kết quả cân của dòng. Mỗi bằng chứng cân chỉ dùng một lần, đúng SKU / số lượng / loại phiếu / người thực hiện, và hết hạn sau 10 phút. Backend khóa bằng chứng khi dùng trong transaction nhập/xuất; nếu phiếu thất bại thì việc sử dụng bằng chứng cũng được rollback. Mất ESP quá 8 giây hoặc backend mất kết nối sẽ không cho lấy số đo mới. Đã lấy cân hợp lệ thì có thể nhấc hàng khỏi cân để cân dòng tiếp theo.

## Ngưỡng sai số

Mặc định **±50 g cố định** (sai số tuyệt đối 50 g, phần trăm 0%). Migration `V11__scale_tolerance_50_grams.sql` cập nhật cả cấu hình đã có khi khởi động lại backend. Ví dụ:

| Dự kiến | Sai số cho phép | Khoảng chấp nhận |
|---|---|---|
| 100 g | ±50 g | 50–150 g |
| 1.000 g | ±50 g | 950–1.050 g |

Đúng biên được chấp nhận; vượt biên bị báo lỗi và không tạo kết quả cân hợp lệ. Admin / manager chỉnh mức tuyệt đối (0–500 g) và phần trăm (0–20%) ở màn hình cân, lưu trong DB. Thay đổi áp dụng cho lần lấy cân tiếp theo; lịch sử giữ ngưỡng đã dùng lúc cân.

Đây là dung sai nghiệp vụ đối chiếu hàng, không phải chứng nhận độ chính xác của load cell 5 kg. Cần đo thử với các vật chuẩn trên nhiều mức tải để chọn ngưỡng phù hợp với cân, cơ khí mặt mica và độ đồng đều sản phẩm.

## API

| API | Chức năng |
|---|---|
| `POST /api/scale/readings` | ESP gửi số đo / trạng thái, nhận lệnh; yêu cầu `X-Device-Key` |
| `GET /api/scale` | Người dùng đăng nhập đọc số đo, kết nối và cấu hình |
| `POST /api/scale/commands` | `{ "type": "TARE" }` hoặc `{ "type": "CALIBRATE", "referenceGrams": 100 }` |
| `PUT /api/scale/settings` | `{ "absoluteGrams": 50, "percent": 0 }` |
| `POST /api/scale/capture` | `{ "sku": "SP001", "quantity": 2, "type": "IN" }`; trả bằng chứng cân |
| `POST /inventory/stock-in`, `/inventory/stock-out` | Mỗi dòng thêm `weighRequired` và `scaleTicketId` khi chọn cân |

Số đo trực tiếp / lệnh đang chờ được giữ trong RAM backend cho một cân cấu hình. Sau khi restart backend, cần ESP gửi lại số đo; cấu hình sai số và bằng chứng cân đã lưu vẫn nằm trong DB. Thiết kế này dành cho một backend instance; triển khai nhiều instance cần chuyển trạng thái trực tiếp và hàng đợi lệnh sang kho dùng chung.

## Kiểm tra thực tế sau khi nạp

- Thử mặt cân trống, vật chuẩn nhỏ / lớn và thay đổi bao bì.
- Thử sai lệch vừa trong / ngoài ngưỡng, rút HX711, mất Wi-Fi, restart ESP và restart backend.
- Xác nhận giao dịch không nhận số đo khi chưa hiệu chuẩn, chưa trừ bì, chưa ổn định hoặc quá tải.
- Không cố đặt tải vượt giới hạn để thử; kiểm tra logic quá tải bằng dữ liệu kiểm thử. Phần mềm không ngăn được hư hỏng cơ khí khi thực tế đặt quá tải.
