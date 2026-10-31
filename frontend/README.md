# Smart Warehouse frontend

React 19 + Vite 8 + Ant Design 6 + Redux Toolkit.
Camera dùng html5-qrcode; tem dùng jsbarcode, định dạng Code128.

## Chạy

Dùng Node.js 22.12+ và backend đã cấu hình:

```powershell
npm ci
npm run dev
```

Web http://localhost:5173; API mặc định http://localhost:8080.
Đặt VITE_API_BASE_URL trong frontend/.env.local để dùng backend khác.
Đây là biến lúc build: thay đổi cần restart dev server hoặc build lại.

```powershell
npm run build
npm run preview
npm run lint
```

## Giao diện

- Login không có chọn vai trò; JWT quyết định trang đích, ưu tiên admin → manager → staff.
- SMART / WAREHOUSE hai dòng cạnh logo.
- Sidebar ba vai trò: mở 248 px, thu gọn 86 px.
- Nhập/xuất và Cân hàng dùng component chung trong pages/staff.
- Admin/manager có dự báo và quyền hiệu chuẩn/chỉnh sai số.
- Camera cần localhost/HTTPS và quyền truy cập; có nhập SKU thủ công dự phòng.
- Cân không tự đổi gram thành số lượng; nhập số lượng rồi cân đối chiếu.

## Docker

Dockerfile build React rồi phục vụ bằng Nginx. VITE_API_BASE_URL mặc định /backend.
Nginx chuyển prefix sang service backend trong Compose, giữ request cùng origin.
Không dùng hostname backend của Compose làm URL public trên Render.
Xem [Docker](../docs/docker.md), [nhập/xuất](../docs/inventory.md), [HX711](../docs/hx711-weighing.md).
