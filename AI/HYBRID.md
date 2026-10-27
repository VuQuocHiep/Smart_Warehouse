# AI nhiệt độ/độ ẩm và gas theo ngưỡng

Đây là luồng đang sử dụng thay cho model MQ-2 ADC ba đầu vào. Không cần chạy collector USB để model này suy luận. ESP tiếp tục gửi nhiệt độ, độ ẩm và gas ADC lên `/api/sensor/readings`; thiết bị gắn trực tiếp với Kho 1.

Chu kỳ đo/gửi ESP là 60 giây; lần đầu gửi ngay sau khởi động. Kho 2–4 cũng đổi số đo mỗi phút. Nhật ký trên giao diện hiển thị tối đa 20 cảnh báo mới nhất; ngưỡng báo dữ liệu cũ là hơn 2 phút để chừa thời gian truyền và tải lại trang.

`train_temperature_humidity.py` train một RandomForest 200 cây, bốn lớp 0/1/2/3, chỉ với `temperature` và `humidity`. Gas, TVOC, UTC và CNT không được đưa vào hai feature của model mới. Khi chạy, không có ngưỡng nhiệt độ/độ ẩm cứng; AI quyết định cả bốn cấp. Gas ADC được xử lý bằng quy tắc riêng ở backend.

Tập CSV gốc chỉ có nhãn Fire Alarm 0/1. Để tạo bản thử nghiệm bốn lớp, script dùng model gốc `smoke_risk_model.joblib` dự đoán nhãn 0/1/2/3 trên bộ dữ liệu cũ (teacher), rồi model hai đầu vào học mô phỏng các nhãn đó (student). Gas TVOC cũ chỉ tham gia lúc teacher tạo nhãn, không có trong đầu vào student hay trong nhánh AI lúc chạy. Đây là nhãn do model sinh, không phải nhãn mức nguy hiểm đã được đo/kiểm chứng độc lập. SHA-256 teacher và nguồn nhãn được ghi trong báo cáo và artifact. Không dùng 25 mẫu MQ-2 chưa đủ nhãn để giả dữ liệu bốn cấp.

Tách dữ liệu theo các phiên ghi thực, dựa trên khoảng cách UTC trên 60 giây: hai phiên đầu train (30.738 hàng), phiên kế validation (24.994 hàng), hai phiên cuối test (6.898 hàng). Không xáo trộn các hàng gần nhau giữa train/test. Model đã được xuất sang `backend/iot/src/main/resources/ai/temperature-humidity.rf.gz`; Java đối chiếu lớp và xác suất với Python trên 325 mẫu, bao gồm cả bốn lớp.

## Hạn chế đo được

Mức đồng thuận với nhãn teacher, đo bằng balanced accuracy, khoảng 95,4% ở train, **45,7% ở validation và 36,9% ở test**. Đây không phải độ chính xác phát hiện nguy hiểm thực tế: teacher cũng đã được huấn luyện trên bộ dữ liệu này và nhãn không độc lập. Student tổng quát hóa kém sau khi bỏ gas. Không dùng số train hay độ tin cậy từng dự đoán để khẳng định khả năng phát hiện cháy ngoài thực tế. Giao diện ghi rõ AI thử nghiệm. Cần dữ liệu nhiệt độ/độ ẩm có nhãn bốn cấp được xác nhận tại nơi sử dụng và đánh giá lại trước khi tin vào kết quả AI. Không có bảo đảm rằng nhiệt độ ngoài miền training như 100°C sẽ được RandomForest xếp đúng mức, do model không tự ngoại suy quy luật tăng nguy hiểm.

## Kết hợp cấp

- `aiRiskLevel`: 0–3 do model hai đầu vào dự đoán.
- `gasRiskLevel`: 0–3 theo ADC MQ-2.
- `riskLevel = max(aiRiskLevel, gasRiskLevel)`; lệnh đèn/quạt/còi dùng mức này.
- `decisionSource`: AI, GAS_THRESHOLD hoặc BOTH khi hai mức bằng nhau.
- `confidence`: xác suất class AI được chọn; không phải độ tin cậy của mức gas hay độ chính xác đã kiểm chứng.

Các mức gas mặc định kế thừa code ESP ban đầu: dưới 1800 = 0, từ 1800 = 1, từ 2500 = 2, từ 3200 = 3. Các ngưỡng ADC này chưa được hiệu chuẩn/kiểm chứng trên cảm biến thực. Cấu hình ở backend bằng `MQ2_GAS_LEVEL1`, `MQ2_GAS_LEVEL2`, `MQ2_GAS_LEVEL3`; phải tăng dần trong 1–4095. Ví dụ trong CMD trước khi chạy Maven:

```cmd
set "MQ2_GAS_LEVEL1=1800"
set "MQ2_GAS_LEVEL2=2500"
set "MQ2_GAS_LEVEL3=3200"
```

Số đo không hợp lệ hoặc thiếu gas không phát lệnh điều khiển. Cấp 0 bật đèn xanh, 1 đèn đỏ, 2 đèn đỏ/quạt, 3 đèn đỏ/quạt/còi. Backend lưu cả cấp AI, cấp gas và nguồn quyết định cùng mỗi số đo qua migration V7, không tính lại lịch sử bằng model mới.

Firmware hiện tích hợp môi trường và HX711 trong `hardware/warehouse_ai/warehouse_ai.ino`.
Sửa `BACKEND_BASE_URL` để sinh cả URL môi trường và cân, rồi nạp lại theo
[hướng dẫn HX711](../docs/hx711-weighing.md). `riskLevel` là mức kết hợp AI nhiệt/ẩm và ngưỡng gas.
Hai luồng train MQ-2 ADC và model TVOC cũ vẫn giữ để tham khảo, không được gọi trong luồng nhận số đo hiện tại.

Train/xuất lại:

```powershell
.\AI\.venv\Scripts\python.exe AI/train_temperature_humidity.py
```

Khởi động lại backend sau khi train; giữ `IOT_INGEST_KEY` trùng firmware. Thống kê đầy đủ tại `AI/reports/temperature_humidity_metrics.json`.
