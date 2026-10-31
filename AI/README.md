# AI trong Smart Warehouse

## Cảnh báo môi trường đang sử dụng

[HYBRID.md](HYBRID.md) mô tả luồng hiện tại:

- RandomForest chỉ nhận nhiệt độ/độ ẩm, xuất sang resources Java tại
  `backend/iot/src/main/resources/ai/temperature-humidity.rf.gz`.
- ADC MQ-2 được phân cấp bằng ngưỡng riêng; mức cuối là mức cao hơn của AI và gas.
- Suy luận chạy trong Spring Boot, không cần Python service khi vận hành.
- Mô hình là thử nghiệm, có hạn chế tổng quát hóa được ghi trong HYBRID.md;
  confidence không phải độ chính xác phát hiện cháy thực tế.

ESP gửi POST `/api/sensor/readings` với `X-Device-Key`, deviceCode, temperature,
humidity, gas. Firmware dùng chung cho môi trường và cân; xem
[HX711](../docs/hx711-weighing.md) và [giám sát bảo quản](../docs/product-environment-monitoring.md).

## Dự báo nhu cầu độc lập

`demand/` chứa FastAPI + LightGBM. Đây là service Python tùy chọn, dùng lịch sử
mô phỏng để dự báo 14 ngày; chưa đọc giao dịch thật từ database.
Lưu joblib và metadata, tự train khi chưa có model hoặc đến hạn.
Chưa có chế độ đóng băng model demo. Xem [hướng dẫn](../docs/demand-forecasting.md).

## Luồng nghiên cứu cũ

[MQ2.md](MQ2.md), train_mq2.py, export_mq2.py và model TVOC/smoke_risk_model.joblib
được giữ để nghiên cứu hoặc đối chiếu. Các mô tả yêu cầu MQ2_MODEL_REQUIRED hay
đầu vào gasTvocPpb thuộc luồng cũ, không mô tả endpoint suy luận hybrid hiện tại.
ADC MQ-2 không phải TVOC ppb; không thay thế hai đại lượng bằng quy đổi tùy ý.
Không tải artifact joblib từ nguồn không tin cậy.
