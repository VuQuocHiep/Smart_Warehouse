# Demand forecasting

Module dự báo xuất kho độc lập với AI cảnh báo môi trường ở thư mục cha.

Hướng dẫn chạy và hợp đồng: [../../docs/demand-forecasting.md](../../docs/demand-forecasting.md).

- `service.py`: worker FastAPI nhận snapshot lịch sử DB và job TRAIN/PREDICT từ backend; một Uvicorn worker, tối đa 64 cặp kho/sản phẩm.
- `demand_model.py`: LightGBM, benchmark theo thời gian, lưu joblib và suy luận 14 ngày.
- `test_demand_model.py`, `test_service.py`: kiểm tra mô hình, snapshot, job bền vững và tái sử dụng artifact.
- `smoke_test.py --snapshot <file.json>`: train thật từ snapshot xuất bởi backend test, sau đó kiểm chứng PREDICT không train lại.
- `runtime/`: artifact và kết quả bền vững theo run, không commit vào Git.

Spring Boot chịu trách nhiệm train khởi tạo, train 05:00 thứ Hai theo giờ Việt Nam, suy luận hằng ngày và chạy bù. Python không tự sinh lịch sử hoặc train theo tuổi model khi truy cập. Nguồn lịch sử là `SIMULATED` hoặc `REAL`, được kiểm tra tách biệt.

CSV dùng `date,warehouse_id,product_id,export_quantity,data_quality,data_source`. CLI offline vẫn có thể train/predict từ CSV đã xác minh:

```powershell
.\.venv\Scripts\python.exe demand_model.py train --csv <daily.csv> --output-dir runtime/offline
.\.venv\Scripts\python.exe demand_model.py predict --csv <daily.csv> --model runtime/offline/model.joblib --output runtime/offline/forecast.csv
```

CLI offline không tự công bố model cho service. Chỉ nạp joblib tin cậy, giữ cùng phiên bản thư viện khi chuyển artifact.
