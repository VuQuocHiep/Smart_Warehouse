# Demand forecasting

Module dự báo lượng xuất độc lập với AI cảnh báo môi trường ở thư mục cha.

Hướng dẫn tích hợp và chạy: [../../docs/demand-forecasting.md](../../docs/demand-forecasting.md).

- `service.py`: FastAPI nội bộ, tái sử dụng joblib, train nền khi đủ 7 ngày và công bố kết quả.
- `demand_model.py`: mô hình, benchmark theo thời gian và dự báo.
- `test_demand_model.py`, `test_service.py`: kiểm thử dữ liệu và API.
- `smoke_test.py`: chạy train thật qua hợp đồng HTTP với danh mục UUID thử nghiệm.
- `runtime/`: dữ liệu/model/kết quả theo run, không commit vào Git.

CSV mô hình dùng `date,warehouse_id,product_id,export_quantity,data_quality,data_source`. ID là chuỗi ổn định theo entity JPA; tên kho và SKU chỉ là thông tin hiển thị. `data_source` bắt buộc là `SIMULATED` hoặc `REAL`; không trộn hai nguồn.

Giới hạn hiện tại: service chỉ chạy demo, tối đa 64 cặp kho/sản phẩm, 1 worker và 1 job một lúc. Mở dashboard tự tạo dự báo từ joblib đã lưu; chưa có mô hình hoặc đủ 7 ngày thì train lại. Chu kỳ được lưu trong `runtime/active.json` và tồn tại qua restart. Huấn luyện đến hạn được kích hoạt khi truy cập, không chạy định kỳ lúc không có truy cập. CLI hỗ trợ CSV REAL đã được xác minh, nhưng chưa có adapter đọc giao dịch thật từ database.

CLI offline từ thư mục này:

```powershell
.\.venv\Scripts\python.exe demand_model.py train --csv runtime/runs/<run-id>/daily.csv --output-dir runtime/offline
.\.venv\Scripts\python.exe demand_model.py predict --csv runtime/runs/<run-id>/daily.csv --model runtime/offline/model.joblib --output runtime/offline/forecast.csv
```

Tham số CSV bắt buộc khi dùng CLI vì mặc định của phiên bản standalone trước đây không còn áp dụng cho danh mục UUID. Chỉ tải artifact joblib tin cậy. Giữ cùng phiên bản dependency khi tải model.
