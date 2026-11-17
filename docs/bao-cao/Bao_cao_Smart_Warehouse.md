# BÁO CÁO DỰ ÁN SMART WAREHOUSE

HỆ THỐNG QUẢN LÝ KHO TÍCH HỢP IoT VÀ HỌC MÁY

Báo cáo kỹ thuật tổng hợp từ mã nguồn, tài liệu và bằng chứng kiểm thử của dự án.

Ngày hoàn thiện: 07/11/2026.

Phạm vi phiên bản: mã nguồn tại thời điểm rà soát; nền Git 5a784d2 và các cập nhật cục bộ. Các kết quả lịch sử được ghi rõ nguồn và thời điểm, không xem là phép đo mới.

<!-- ABSTRACT -->
# TÓM TẮT

Smart Warehouse là hệ thống quản lý kho kết hợp nghiệp vụ nhập, xuất và lưu trữ hàng hóa với giám sát môi trường, cân đối chiếu và dự báo lượng xuất. Hệ thống gồm giao diện React, dịch vụ Spring Boot, cơ sở dữ liệu MySQL, thiết bị ESP32 và dịch vụ dự báo Python. DHT22 và MQ-2 cung cấp dữ liệu môi trường; HX711 hỗ trợ cân từng dòng hàng; các mô hình học máy được sử dụng cho phân cấp môi trường thử nghiệm và dự báo lượng xuất theo kho, sản phẩm.

Thiết kế chú trọng tính nhất quán của tồn kho, phân quyền ba nhóm người dùng, truy vết giao dịch và khả năng tái sử dụng mô hình dự báo. Nhánh cảnh báo môi trường kết hợp Random Forest hai đầu vào với ngưỡng gas ADC. Nhánh dự báo sử dụng LightGBM, đánh giá theo thời gian và chuyển kết quả thành đề xuất nhập trong bảy ngày. Tài liệu này mô tả kiến trúc đã triển khai, cơ sở lý thuyết, mô hình dữ liệu, quy trình xử lý, kết quả có bằng chứng và giới hạn hiện tại.

Kết quả rà soát xác nhận các mô-đun nghiệp vụ đã có mã triển khai và bộ kiểm thử. Trong đợt thực hiện báo cáo, 27 kiểm thử Python đạt; các trang rỗng được loại khỏi giao diện và kiểm tra biên dịch. Các số liệu học máy được trình bày như kết quả thử nghiệm: mô hình môi trường có mức đồng thuận thấp trên tập giữ lại; kết quả dự báo hiện có được tạo từ dữ liệu tổng hợp trước migration V16. Chưa có đủ bằng chứng nghiệm thu cloud và thiết bị thật để kết luận hệ thống sẵn sàng vận hành thực tế.

Từ khóa: quản lý kho; Internet vạn vật; ESP32; cân đối chiếu; Random Forest; LightGBM; dự báo chuỗi thời gian.

## Danh mục chữ viết tắt

| Ký hiệu | Diễn giải |
| --- | --- |
| IoT | Internet of Things – Internet vạn vật |
| API / REST | Giao diện lập trình ứng dụng / phong cách trao đổi tài nguyên qua HTTP |
| SKU | Stock Keeping Unit – mã định danh đơn vị hàng hóa |
| ADC | Analog-to-Digital Converter – bộ chuyển đổi tương tự sang số |
| JWT / RBAC | JSON Web Token / phân quyền truy cập theo vai trò |
| ORM / JPA | Ánh xạ đối tượng–quan hệ / chuẩn truy cập dữ liệu Java |
| RF / GBDT | Random Forest / cây quyết định tăng cường gradient |
| MAE / WAPE | Sai số tuyệt đối trung bình / sai số tuyệt đối có trọng số theo tổng lượng |
| TLS / CA | Bảo mật tầng truyền tải / tổ chức hoặc chứng chỉ gốc xác thực |

<!-- TOC -->
# MỤC LỤC

<!-- AUTO_TOC -->

<!-- CHAPTER -->
# CHƯƠNG 1. TỔNG QUAN VÀ YÊU CẦU

## 1.1. Bối cảnh và vấn đề nghiên cứu

Quản lý kho bằng các bảng dữ liệu rời rạc thường khó đồng bộ số lượng, vị trí lưu trữ và điều kiện bảo quản. Khi nhập, xuất được ghi nhận thiếu nhất quán, tồn kho có thể sai lệch; khi thông tin môi trường không được liên kết với hàng hóa, người vận hành khó xác định sản phẩm chịu ảnh hưởng. Bên cạnh đó, lượng xuất biến động theo thời gian khiến việc bổ sung hàng chỉ dựa trên tồn hiện tại dễ thiếu cơ sở định lượng.

Dự án giải quyết các vấn đề trên bằng một nền tảng thống nhất. Nghiệp vụ kho tạo dữ liệu giao dịch; thiết bị cung cấp số đo; dịch vụ phân tích hỗ trợ nhận biết trạng thái và ước lượng lượng xuất tương lai. Mục tiêu nghiên cứu là xây dựng, tích hợp và kiểm chứng các luồng phần mềm cốt lõi, đồng thời xác định rõ phần còn cần đánh giá trong điều kiện thực tế. [T1–T5]

## 1.2. Mục tiêu và phạm vi

Các mục tiêu cụ thể gồm quản lý sản phẩm, kho và kệ; cập nhật tồn theo giao dịch; nhận diện SKU bằng mã quét hoặc nhập tay; đối chiếu khối lượng của dòng hàng; giám sát nhiệt độ, độ ẩm và gas; cung cấp cảnh báo bảo quản; dự báo lượng xuất và lượng nhập bổ sung; bảo vệ chức năng bằng xác thực và phân quyền.

Phạm vi phần cứng hiện tại là một thiết bị ESP32 tích hợp cảm biến và cân. Kho 1 được cấu hình nhận telemetry từ thiết bị; Kho 2–4 sử dụng dữ liệu môi trường mô phỏng có nhãn. Sự xuất hiện của bốn kho trên dashboard không tương đương bốn nút IoT vật lý. Nhận diện hàng hóa hiện thực hiện qua camera QR/barcode trên giao diện, không qua đầu đọc RFID RC522. Kiến trúc đang chạy sử dụng HTTP và REST polling. [T2, T4]

Hệ thống chưa bao quát quản lý mua hàng đầy đủ, đơn hàng đang vận chuyển, dự báo nhu cầu bị mất khi hết hàng hoặc tối ưu mạng lưới cung ứng. Nhánh môi trường là mô hình thử nghiệm, chưa được thẩm định như một hệ thống phát hiện cháy.

## 1.3. Tác nhân và yêu cầu chức năng

| Tác nhân | Trách nhiệm chính trong hệ thống |
| --- | --- |
| Quản trị viên | Quản lý tài khoản; quản lý danh mục và kho; giám sát; nhập, xuất; quản lý cân và dự báo |
| Quản lý | Quản lý sản phẩm, kho, kệ; nhập, xuất; theo dõi IoT, cảnh báo, cân và dự báo |
| Nhân viên | Thực hiện nhập, xuất; đọc tồn và vị trí; xem giám sát theo giao diện được cấp; trừ bì, lấy kết quả cân |
| Thiết bị ESP32 | Gửi số đo, trạng thái cân; nhận lệnh điều khiển và phản hồi xác nhận |
| Bộ xử lý dự báo | Nhận snapshot; huấn luyện hoặc suy luận; công bố artifact và trạng thái tác vụ |

Quyền trên giao diện được xác định từ JWT sau đăng nhập. Các kiểm tra ở backend được áp dụng độc lập với việc ẩn hoặc hiện menu. Chức năng dự báo và cấu hình hiệu chuẩn dành cho Admin/Manager; Staff được đọc cân, trừ bì và lấy kết quả cân vào phiếu. [T2, T8]

## 1.4. Yêu cầu chất lượng và phương pháp đánh giá

Tính đúng đắn nghiệp vụ được thể hiện qua các bất biến: tồn không âm; xuất không vượt tồn tại kho; phân bổ kệ phù hợp; bằng chứng cân không bị sử dụng lại; giao dịch lỗi phải được hoàn tác. Khả năng truy vết yêu cầu lưu người thực hiện, thời gian, SKU, kho, số lượng và thông tin cân khi có.

Đối với IoT, hệ thống cần phân biệt số đo mới, cũ và chưa có dữ liệu; từ chối dữ liệu hoặc khóa thiết bị không hợp lệ. Đối với học máy, dữ liệu đánh giá phải tách theo thời gian, có mô hình đối chứng và thông tin nguồn. Báo cáo sử dụng ba mức bằng chứng: mã triển khai; kết quả kiểm thử/artifact đã lưu; kiểm tra thực hiện trong đợt rà soát. Các mức này không được thay thế cho nhau.

<!-- CHAPTER -->
# CHƯƠNG 2. CƠ SỞ LÝ THUYẾT

## 2.1. IoT, giao tiếp HTTP và giám sát định kỳ

Một hệ thống IoT gồm lớp cảm nhận, lớp truyền thông và lớp xử lý ứng dụng. Trong dự án, ESP32 thu thập tín hiệu; HTTP chuyển số đo lên backend; ứng dụng lưu trữ, phân tích và trả lệnh. Giao diện lấy dữ liệu định kỳ qua REST. Chất lượng giám sát vì vậy phụ thuộc cả chu kỳ đo, thời gian truyền, thời điểm xử lý và chu kỳ cập nhật giao diện. Giá trị gần nhất cần đi kèm dấu thời gian và trạng thái dữ liệu.

HTTPS bảo vệ kênh truyền và hỗ trợ xác minh máy chủ bằng chứng chỉ gốc. Tài liệu Espressif mô tả xác minh máy chủ qua chứng chỉ PEM hoặc gói chứng chỉ. Trong triển khai của dự án, firmware sử dụng lớp HTTP/TLS của Arduino trên ESP32; tài liệu ESP-IDF được viện dẫn cho nguyên lý nền tảng, không đồng nhất API của hai môi trường. [S4]

## 2.2. Quản lý tồn kho và giao dịch cơ sở dữ liệu

Tồn kho tại thời điểm t là kết quả tích lũy các biến động đã xác nhận. Với một sản phẩm tại một kho, quan hệ cơ bản là:

$$ Q(t) = Q(t−1) + Q_nhập(t) − Q_xuất(t). $$

Khi một phiếu đồng thời cập nhật tồn tổng, tồn theo kho, tồn kệ và nhật ký, các thao tác cần có tính nguyên tử. Transaction giúp toàn bộ nhóm thao tác cùng được xác nhận hoặc cùng hoàn tác. Khóa dữ liệu được sử dụng để bảo vệ những bản ghi có thể bị thay đổi bởi nhiều yêu cầu đồng thời. Spring hỗ trợ quản lý transaction khai báo, cho phép mô tả ranh giới và chính sách hoàn tác tại lớp nghiệp vụ. [S3]

Tồn tổng và tồn đã xếp kệ là hai đại lượng có liên hệ nhưng khác phạm vi. Phần tồn legacy chưa xác định kho không được tự xem là số dư theo kho đã đối soát để đề xuất nhập; hàng đã xác định kho có thể chưa được xếp kệ. Việc giữ nhật ký bất biến về ý nghĩa nghiệp vụ hỗ trợ truy vết và tổng hợp chuỗi lượng xuất theo ngày.

## 2.3. Nguyên lý cân đối chiếu

Load cell biến đổi lực thành tín hiệu điện; HX711 cung cấp phép đo số cho vi điều khiển. Giá trị RAW cần được quy đổi bằng mốc không và hệ số hiệu chuẩn xác lập từ vật chuẩn. Công thức khái quát là m = (RAW − offset)/factor. Hệ số phụ thuộc bộ cân và cơ khí thực tế, không thể suy ra chỉ từ sức cân danh định.

Khối lượng kỳ vọng của một dòng hàng là số lượng nhân khối lượng đơn vị. Điều kiện đối chiếu là độ lệch tuyệt đối giữa khối lượng đo và kỳ vọng không vượt dung sai đã cấu hình. Đây là kiểm soát nghiệp vụ; nó không thay thế phép đánh giá độ chính xác đo lường của thiết bị. Trừ bì làm thay đổi mốc hiển thị, không làm tăng tải trọng cơ khí cho phép. [T3]

## 2.4. Random Forest và phân cấp kết hợp quy tắc

Random Forest kết hợp nhiều cây quyết định được xây dựng với yếu tố ngẫu nhiên trong lấy mẫu và lựa chọn đặc trưng. Các cây cung cấp dự đoán thành phần, sau đó được tổng hợp để tạo dự đoán chung. Cách kết hợp này giúp khai thác quan hệ phi tuyến và giảm phụ thuộc vào một cây đơn lẻ. Khả năng tổng quát hóa vẫn phụ thuộc dữ liệu, nhãn và sự khác biệt giữa môi trường huấn luyện với môi trường sử dụng. [S1]

Dự án huấn luyện mô hình hai đầu vào nhiệt độ, độ ẩm để mô phỏng nhãn bốn mức do một mô hình teacher tạo. Vì nhãn không được xác nhận độc lập, các chỉ số đo mức đồng thuận teacher–student. Gas ADC được xử lý riêng bằng quy tắc, rồi kết hợp theo:

$$ riskLevel = max(aiRiskLevel, gasRiskLevel). $$

Phép lấy mức lớn hơn giúp kết quả cuối không thấp hơn nhánh gas đã quy định. Tuy nhiên, phép kết hợp không khắc phục được nhãn AI thiếu xác thực hoặc ngưỡng gas chưa hiệu chuẩn. Xác suất lớp AI cũng không phải độ tin cậy đã kiểm chứng của toàn bộ hệ thống. [T5]

## 2.5. LightGBM và dự báo chuỗi thời gian

Gradient boosting xây dựng tuần tự các cây để cải thiện hàm dự đoán theo một hàm mất mát. LightGBM là một triển khai GBDT hướng đến hiệu quả tính toán. Đối với dự báo, dữ liệu chuỗi được chuyển thành bài toán hồi quy bằng đặc trưng trễ, thống kê cửa sổ và lịch. Các đặc trưng chỉ được sử dụng thông tin đã tồn tại tại thời điểm phát sinh dự báo. [S2]

Dự án dự báo trực tiếp nhiều chân trời từ một đến 14 ngày, đưa chân trời dự báo vào tập đặc trưng. Hai hàm mục tiêu được thử nghiệm là Poisson và sai số tuyệt đối L1. Trung bình 28 ngày, giá trị cùng thứ gần nhất và trung bình cùng thứ được sử dụng làm đối chứng. Việc lựa chọn phương pháp phải dựa trên validation; tập test được giữ riêng để đánh giá sau lựa chọn. [T6, T8]

## 2.6. Chỉ số đánh giá

Với y là lượng xuất quan sát và ŷ là dự báo, các chỉ số triển khai trong dự án là:

$$ MAE = (1/N) Σ |ŷᵢ − yᵢ|. $$

$$ WAPE = 100 × Σ |ŷᵢ − yᵢ| / Σ |yᵢ|. $$

$$ Bias = (1/N) Σ (ŷᵢ − yᵢ). $$

MAE có đơn vị số lượng hàng; WAPE diễn tả tỷ lệ sai số trên tổng lượng và không xác định khi mẫu số bằng không; bias âm thể hiện xu hướng dự báo thấp. Không diễn giải 100% − WAPE thành độ chính xác phân loại. Đối với mô hình môi trường, balanced accuracy là trung bình recall trên các lớp có mẫu thật trong tập tương ứng; nó phù hợp hơn accuracy thô khi phân bố lớp chênh lệch. [T8, T9]

<!-- CHAPTER -->
# CHƯƠNG 3. KIẾN TRÚC VÀ THIẾT KẾ HỆ THỐNG

## 3.1. Kiến trúc tổng thể

Hệ thống chia thành năm thành phần: giao diện, backend, cơ sở dữ liệu, thiết bị và bộ xử lý dự báo. Backend là đầu mối nghiệp vụ, xác thực, truy cập dữ liệu và tiếp nhận thiết bị. Mô hình môi trường đã được xuất để suy luận trực tiếp trong Java; chỉ nhánh dự báo nhu cầu cần thành phần Python tùy chế độ triển khai. [T1, T2]

<!-- FIGURE architecture -->

Hình 3.1. Kiến trúc đang triển khai. Các mũi tên thể hiện luồng trao đổi logic; cloud batch là phương án cấu hình, chưa phải bằng chứng nghiệm thu.

| Thành phần | Công nghệ và vai trò |
| --- | --- |
| Frontend | React 19, Vite, Ant Design, Redux Toolkit; màn hình theo vai trò; REST polling; quét mã và in tem |
| Backend | Java 17, Spring Boot, Spring Security, JPA, Flyway; xử lý nghiệp vụ, giám sát và điều phối tác vụ |
| Cơ sở dữ liệu | MySQL 8.0; danh mục, tồn, giao dịch, cảnh báo, bằng chứng cân và tác vụ dự báo |
| Phân tích nhu cầu | Python, FastAPI, LightGBM, joblib; huấn luyện, suy luận, lưu artifact và chỉ số |
| Thiết bị | ESP32; DHT22, MQ-2, HX711; đèn, quạt, còi; HTTP/HTTPS và lưu cấu hình cân |

Các dependency Redis, AMQP hoặc WebSocket xuất hiện trong POM không đủ để kết luận hệ thống đang sử dụng các hạ tầng đó. Luồng hiện tại được đối chiếu từ controller, service, frontend và firmware; tài liệu kiến trúc xác nhận REST/HTTP thay cho thiết kế RFID/MQTT ban đầu. [T2, T8]

## 3.2. Phân lớp phần mềm

Frontend tổ chức theo layout vai trò, các trang nghiệp vụ, component dùng chung, API client và tiện ích. Những dashboard dùng chung giúp các vai trò hiển thị cùng logic giám sát hoặc dự báo theo quyền. Redux quản lý trạng thái người dùng; router và bộ bảo vệ vai trò điều phối truy cập.

Backend tổ chức theo controller, DTO, service, repository và entity. Controller tiếp nhận hợp đồng HTTP; service thực hiện quy tắc và transaction; repository truy xuất dữ liệu. Cách phân lớp giúp kiểm thử nghiệp vụ độc lập hơn với giao diện. Cấu hình bảo mật kiểm soát endpoint và quyền phương thức, trong khi dữ liệu thiết bị có khóa tiếp nhận riêng. [T8]

## 3.3. Mô hình dữ liệu

<!-- FIGURE data_model -->

Hình 3.2. Mô hình quan hệ nghiệp vụ rút gọn. Sơ đồ biểu diễn quan hệ logic; không khẳng định mọi liên kết đều được khai báo thành khóa ngoại SQL.

| Nhóm dữ liệu | Bảng tiêu biểu và ý nghĩa |
| --- | --- |
| Danh mục | product, warehouse, monitored_shelf; thông tin sản phẩm, ngưỡng bảo quản, kho, kệ và sức chứa |
| Vị trí và tồn | inventory, warehouse_inventory, shelf_stock; tồn tổng, tồn theo kho và lượng đã phân bổ trên kệ |
| Giao dịch | stock_transaction; loại IN/OUT, sản phẩm, kho, số lượng, người thực hiện, thời gian, thông tin cân |
| Môi trường | warehouse_environment_reading, product_environment_alert, environment_alert_receipt; số đo, kết quả phân tích và trạng thái đọc riêng |
| Cân | scale_settings, scale_ticket; dung sai và bằng chứng cân được sử dụng trong phiếu |
| Dự báo | demand_series, demand_policy, demand_job cùng nhóm bảng cloud; chuỗi, chính sách tồn an toàn, trạng thái tác vụ và artifact |
| Định danh | Nhóm người dùng, vai trò, quyền; product_sku_alias duy trì khả năng tra cứu mã hàng cũ |

Schema được quản lý bằng Flyway. Đường khởi tạo mới dùng B1 rồi V2–V16; V1 legacy còn lưu để tham chiếu, không được dùng thay cho quy trình hiện hành. Các migration quan trọng gồm V8/V9 cho nghiệp vụ tồn và kệ; V10/V11 cho cân; V12/V13 cho dự báo và dữ liệu khởi tạo; V15 cho cloud batch; V16 cho lịch sử thống nhất. [T2, T6, T8]

V16 gộp lịch sử nhập ban đầu và giao dịch mới vào ledger có cờ REAL, giữ khả năng nối tiếp chuỗi. Việc chuẩn hóa cờ nguồn chỉ thay đổi cách phần mềm quản lý ledger. Nó không làm thay đổi bản chất dữ liệu tổng hợp đã được tạo bởi V13. Đây là điều kiện cần khi diễn giải mọi kết quả dự báo.

## 3.4. Luồng nhập, xuất và gợi ý kệ

Người dùng chọn kho, quét mã hoặc nhập SKU, xác nhận sản phẩm và số lượng. Với dòng cần cân, người dùng lấy bằng chứng cân trước khi gửi phiếu. Backend kiểm tra quyền, dữ liệu đầu vào, tồn, phân bổ và bằng chứng; sau đó cập nhật các bảng trong cùng transaction. Phiếu lỗi không được để lại một phần cập nhật tồn hoặc tiêu thụ bằng chứng cân. [T3, T8]

Nhập hàng có thể chỉ định kệ. Gợi ý vị trí sử dụng chấm điểm theo môi trường bảo quản, sức chứa, gom nhóm hàng, lịch sử xuất và cảnh báo; kết quả đi kèm lý do. Đây là thuật toán quy tắc Java, tách biệt với LightGBM dự báo. Khi xuất, hệ thống trừ lượng đã xếp trên các kệ có hàng nhiều trước theo logic hiện tại; không nên mô tả là FIFO hoặc FEFO nếu chưa có quản lý lô/hạn dùng tương ứng.

## 3.5. Thiết kế cân và bằng chứng giao dịch

<!-- FIGURE weighing -->

Hình 3.3. Trình tự cân một dòng hàng và sử dụng bằng chứng trong giao dịch nhập/xuất.

Phiếu cân gắn với người dùng, SKU, số lượng và loại IN/OUT; chỉ được dùng một lần, trong thời hạn mười phút. Thay đổi dòng hàng làm mất hiệu lực kết quả cân trên giao diện. Backend khóa bằng chứng khi tiêu thụ cùng giao dịch; rollback khôi phục trạng thái nếu giao dịch thất bại.

Dung sai mặc định là ±50 g tuyệt đối; phần trăm mặc định bằng 0. Ví dụ dòng hai hộp, mỗi hộp 250 g, có khối lượng kỳ vọng 500 g; khoảng chấp nhận mặc định là 450–550 g. Ví dụ này minh họa quy tắc, không phải kết quả đo phần cứng. Số lượng tồn tiếp tục là số nguyên; phần mềm không suy ra số lượng từ gram.

Số đo và lệnh cân đang chờ được giữ trong RAM backend; cấu hình và bằng chứng được lưu MySQL. Thiết kế phục vụ một cân cấu hình và một backend instance. Khi mở rộng nhiều instance, cần trạng thái dùng chung cùng cơ chế định tuyến hoặc hàng đợi lệnh phù hợp. [T3]

## 3.6. Xác thực, phân quyền và truy vết

Luồng người dùng sử dụng Bearer JWT; backend suy ra quyền từ token. Thiết bị gửi X-Device-Key; worker HTTP nội bộ dùng X-Service-Token; runner cloud batch dùng X-Batch-Token và X-Claim-Token gắn với lease. Các cơ chế xác thực có vai trò khác nhau và không thay thế cho nhau. Dự báo dành cho Admin/Manager; endpoint nhận telemetry không dùng cơ chế đăng nhập giao diện.

Các khóa, mật khẩu và cấu hình riêng nằm ngoài tài liệu báo cáo. Cấu hình mẫu dùng biến môi trường; thiết bị dùng config.local.h được loại khỏi Git. Thông tin phiên bản mô hình, ngày huấn luyện, nguồn và checksum giúp truy vết kết quả phân tích. [T1, T6–T8]

<!-- CHAPTER -->
# CHƯƠNG 4. TRIỂN KHAI CÁC PHÂN HỆ

## 4.1. Giao diện và hoàn thiện phạm vi chức năng

Giao diện cung cấp tổng quan, quản lý sản phẩm và kho, kệ, nhập/xuất, cân, giám sát IoT, cảnh báo, dự báo và quản lý người dùng theo vai trò. Các trang nhập/xuất tích hợp tra cứu SKU, quét camera và đối chiếu khối lượng. Kết quả dự báo hiển thị lượng xuất, lượng nhập đề xuất và tình trạng dữ liệu; cảnh báo bảo quản được liên kết với sản phẩm.

Trong đợt rà soát, tám component chỉ có nội dung rỗng được loại bỏ: RFID và Lịch sử ở cả ba vai trò; Cài đặt của quản trị viên; Báo cáo của quản lý. Menu, import và route tương ứng được gỡ đồng bộ. Các thành phần Quên mật khẩu, Trợ giúp, chọn ngôn ngữ, Ghi nhớ đăng nhập và ô tìm kiếm header được giữ nguyên theo phạm vi yêu cầu, dù một số thành phần chưa có xử lý đầy đủ. [T10]

Việc loại trang Lịch sử rỗng không xóa nhật ký giao dịch trong backend hoặc các luồng hiển thị giao dịch đã triển khai. Placeholder gợi ý nhập liệu và trạng thái chưa có dữ liệu của chức năng hoạt động được giữ vì mang ý nghĩa hướng dẫn, phản hồi trạng thái. Phạm vi làm sạch giới hạn ở các trang chưa sử dụng và điều hướng liên quan.

## 4.2. Thiết bị và giám sát môi trường

Firmware tích hợp DHT22, MQ-2 và HX711 trong cùng sketch. DHT22 dùng GPIO4; MQ-2 dùng GPIO34; HX711 dùng DT GPIO16 và SCK GPIO21. Dữ liệu môi trường gửi theo chu kỳ khoảng 60 giây; cân được gửi và lấy hiển thị khoảng một giây. Các tác vụ HTTP trao đổi bản sao dữ liệu, giúp hạn chế việc chờ mạng ảnh hưởng đến đọc cân. Chi tiết tham số vẫn cần đối chiếu với phần cứng thực tế. [T3–T5, T8]

Kho 1 không tự thay bằng số liệu giả lập khi chưa nhận thiết bị. Kho 2–4 có nguồn mô phỏng riêng. Backend kiểm tra sản phẩm gán trên kệ và ngưỡng bảo quản để sinh cảnh báo; một đợt vượt ngưỡng được gộp, trở về bình thường rồi vượt tiếp mới tạo đợt khác. Người dùng có trạng thái đọc riêng. Luồng này là kiểm tra bảo quản theo sản phẩm, khác với phân cấp AI môi trường chung.

| Gas ADC mặc định | Cấp gas | Điều khiển theo cấp kết hợp |
| --- | --- | --- |
| Dưới 1.800 | 0 | Đèn xanh khi cấp kết hợp bằng 0 |
| 1.800 đến dưới 2.500 | 1 | Đèn đỏ từ cấp kết hợp 1 |
| 2.500 đến dưới 3.200 | 2 | Bật thêm quạt từ cấp kết hợp 2 |
| Từ 3.200 | 3 | Bật thêm còi ở cấp kết hợp 3 |

Các mức trên là ngưỡng ADC cấu hình, chưa phải nồng độ khí hoặc ngưỡng an toàn đã hiệu chuẩn. Không quy đổi ADC thành TVOC/ppm bằng hệ số tùy ý. Khi AI có cấp cao hơn gas, điều khiển đi theo cấp kết hợp tương ứng. Đầu vào thiếu hoặc không hợp lệ không tạo lệnh điều khiển hợp lệ. [T5]

## 4.3. Mô hình môi trường thử nghiệm

Mô hình hiện hành gồm 200 cây RF với hai đặc trưng temperature và humidity; teacher trước đó dùng thêm thông tin gas/TVOC để tạo nhãn. Dữ liệu chia theo phiên ghi, nhận diện bằng khoảng cách UTC lớn hơn 60 giây: 30.738 dòng train, 24.994 dòng validation và 6.898 dòng test. Không xáo trộn các dòng lân cận giữa các tập. [T5, T9]

Mô hình đã được xuất sang tài nguyên temperature-humidity.rf.gz để Java suy luận, giúp luồng môi trường không phụ thuộc việc chạy Python. Báo cáo artifact ghi nhận 325 mẫu kiểm tra tính tương đương đầu ra giữa Java và Python. Kiểm tra tương đương xác nhận tính nhất quán triển khai, không xác nhận tính đúng của nhãn hoặc khả năng phát hiện nguy hiểm.

## 4.4. Dự báo lượng xuất và gợi ý nhập

Backend tổng hợp giao dịch OUT theo ngày, kho và sản phẩm, chốt đến hết hôm qua theo giờ Việt Nam. Snapshot chứa định danh dataset, nguồn, ngày chốt và checksum. Bộ dữ liệu khởi tạo V13 có seed 42, gồm 12 sản phẩm, 24 chuỗi kho–sản phẩm, 24 kệ và 30.920 giao dịch trên 730 ngày. Bộ mẫu được nạp một lần; khởi động lại không tự kéo dài dữ liệu tổng hợp. V16 nối tiếp ledger tới ngày chốt từ các giao dịch đã ghi nhận. [T6]

Mô hình sử dụng mã kho/sản phẩm, lượng mới nhất, độ trễ 7/14/28 ngày, trung bình và độ lệch chuẩn 7/28 ngày, tỷ lệ ngày bằng 0, thứ, tháng, chu kỳ năm, chân trời dự báo và trung bình cùng thứ. Hai mô hình LightGBM có 200 cây, learning rate 0,05, tối đa 15 lá, random state 42. Phương án Poisson hoặc L1 được chọn theo MAE validation cho từng chuỗi; các baseline được báo cáo để so sánh nhưng hiện không nằm trong tập phương án được chọn. [T8]

Quy trình đánh giá dùng khoảng 18 tháng đầu cho huấn luyện ban đầu, ba tháng validation với các cửa sổ mở rộng, sau đó test giữ riêng. Chân trời 14 ngày phải đầy đủ. Mô hình cuối phục vụ triển khai được huấn luyện lại sau đánh giá nên không có điểm held-out riêng cho chính artifact cuối. Chuỗi mới cần tối thiểu 90 ngày cho suy luận bằng mô hình chung; benchmark cần khoảng 22 tháng lịch sử.

Lịch điều phối gồm train lúc 05:00 thứ Hai và cập nhật dự báo lúc 00:10 hằng ngày, theo Asia/Ho_Chi_Minh. Khóa lease trong DB và trạng thái job bền vững giúp chống trùng, chạy bù và thử lại. PREDICT sử dụng mô hình đã lưu; thao tác mở dashboard không tự huấn luyện. [T6]

Đề xuất nhập bảy ngày được tính từ tổng dự báo xuất còn lại, tồn an toàn và tồn hiện tại:

$$ R_lũy_kế(d) = ceil(max(0, Σ E_còn_lại(k) + S_an_toàn − I_hiện_tại)). $$

$$ R_riêng(d) = R_lũy_kế(d) − R_lũy_kế(d−1). $$

Riêng hôm nay, lượng đã xuất được trừ khỏi dự báo cả ngày trước khi cộng nhu cầu còn lại. Mặc định tồn an toàn bằng 0; chưa xét đơn mua đang về và thời gian giao hàng. Hệ thống tính lại đề xuất từ tồn mới khi tải dữ liệu; thiếu dữ liệu hoặc tồn chưa đối soát phải có trạng thái riêng, không được biểu diễn như đề xuất bằng 0. [T6]

## 4.5. Triển khai và quản lý vận hành

Môi trường local dùng frontend 5173, backend 8080, MySQL 3306 và Python 8091 trên loopback. Docker Compose cung cấp Nginx tại 8088, chuyển tiếp /backend/ tới Spring; MySQL và dịch vụ ML ở mạng nội bộ. Volume mysql_data và ml_data duy trì dữ liệu và artifact. [T1, T2]

Phương án cloud đã có cấu hình Render cho frontend/backend, Aiven MySQL và GitHub Actions cho Python batch. Model, job và kết quả của chế độ batch được lưu qua backend vào MySQL. Báo cáo nghiệm thu còn ghi lỗi V16 cũ khi Aiven yêu cầu primary key ở bảng tạm; bản sửa và script bảo trì có kiểm thử riêng. Chưa có xác nhận backend cloud hoạt động thành công sau bản sửa. [T7]

Khi triển khai cần đối chiếu phiên bản schema, origin giao diện, biến môi trường, CA/TLS, khóa thiết bị và cơ chế sao lưu. Với một backend instance, trạng thái cân trong RAM là giới hạn vận hành cụ thể. Quy trình mở rộng và phục hồi cần được kiểm chứng trước khi dùng hệ thống trong kho thực tế.

<!-- CHAPTER -->
# CHƯƠNG 5. KIỂM THỬ VÀ KẾT QUẢ

## 5.1. Phương pháp và mức độ bằng chứng

Kết quả được tổng hợp từ kiểm tra trực tiếp trong đợt 07/11/2026, báo cáo kiểm thử lưu tại máy và artifact mô hình. Các kết quả đã lưu chỉ chứng minh lần chạy tương ứng; không khẳng định môi trường ứng dụng hiện đang hoạt động. Không chạy lại toàn bộ backend với database ứng dụng hoặc nạp firmware trong đợt biên soạn này.

| Hạng mục | Bằng chứng | Diễn giải |
| --- | --- | --- |
| Frontend sau làm sạch | Biên dịch production đạt; đối chiếu route/import/menu | Xác nhận mã giao diện đóng gói được, không thay cho kiểm thử toàn hệ thống |
| Python dự báo | 27 unittest đạt trong 6,855 giây ở đợt hiện tại | Chạy bằng môi trường AI/demand/.venv; không huấn luyện lại mô hình |
| Backend đã lưu | 20 tệp TEST-*.xml: 75 test, 0 lỗi, 0 thất bại, 1 bỏ qua | 74 đạt; báo cáo nằm tại target/surefire-reports, lần ghi 31/10/2026 |
| Biên bản local trước đó | Ngày 31/10 ghi 73 test backend và 27 test Python | Là mốc kiểm tra trước các báo cáo bổ sung; không cộng gộp hai mốc |
| Sửa V16 primary key | Biên bản ghi 17 kiểm thử tích hợp MySQL đạt | Là nhóm kiểm tra tập trung; không cộng thêm vào tổng XML |
| Cloud và thiết bị | Biên bản ghi chưa nghiệm thu | Chưa đủ bằng chứng nghiệm thu đầu cuối |

Bộ unittest Python bao quát dữ liệu đầu vào, mô hình, vòng đời tác vụ và cloud runner. Bộ Java hiện có kiểm tra phân quyền, tiếp nhận cảm biến, dịch vụ cân, nhập/xuất, migration và cloud batch. Các cảnh báo thư viện hoặc log lỗi cố ý trong negative tests không đồng nghĩa test thất bại. [T7, T11]

## 5.2. Kết quả chức năng và tính nhất quán

Mã triển khai và kiểm thử hiện có bao quát xác thực theo vai trò, quản lý danh mục, nhập/xuất có transaction, phân bổ tồn, bằng chứng cân một lần và giám sát sản phẩm. Những trường hợp đáng chú ý gồm xuất vượt tồn, sai chủ thể hoặc dòng hàng của phiếu cân, dữ liệu cảm biến sai khóa, truy cập dự báo sai vai trò và migration trên database mới/nâng cấp. [T8, T11]

Biên bản ngày 29/10 ghi nhận smoke test local tạo model joblib 679.761 byte và 336 dòng dự báo cho 24 cặp kho–sản phẩm; lần PREDICT giữ modelVersion/trainedAt. Thay container backend nhưng giữ DB vẫn giữ model và dự báo trong chế độ cloud batch thử tại máy. Các kết quả này chứng minh một luồng tích hợp local, chưa chứng minh đường truyền cloud hoặc kết nối thiết bị thật. [T7]

Biên bản cũng ghi working set khoảng 418 MiB và đỉnh cgroup khoảng 447 MiB trong container giới hạn 512 MiB, không có OOM. Đây là phép đo local với cấu hình JVM cụ thể; không suy rộng thành cam kết bộ nhớ, độ trễ hay khả năng phục vụ đồng thời trên Render.

## 5.3. Kết quả mô hình môi trường

<!-- FIGURE rf_metrics -->

Hình 5.1. Balanced accuracy đo mức đồng thuận với nhãn teacher theo các tập dữ liệu. Nguồn: temperature_humidity_metrics.json; đây là chỉ số của mô hình AI hai đầu vào, chưa đánh giá toàn bộ nhánh kết hợp gas.

| Tập dữ liệu | Số dòng | Balanced accuracy đồng thuận teacher | Accuracy thô |
| --- | --- | --- | --- |
| Train | 30.738 | 95,43% | 91,28% |
| Validation | 24.994 | 45,72% | 47,17% |
| Test | 6.898 | 36,94% | 62,25% |

Chênh lệch lớn giữa train và các tập giữ lại cho thấy khả năng tổng quát hóa kém của student sau khi bỏ gas. Validation chỉ có mẫu teacher thuộc lớp 0 và 2; test có cả bốn lớp. Trong ma trận test, student không dự đoán đúng mẫu nào ở lớp 1 và 3. Vì vậy accuracy thô 62,25% không phản ánh đầy đủ sự thất bại ở các lớp này. [T9]

Teacher từng được huấn luyện trên chính bộ dữ liệu nguồn; nhãn bốn mức không được kiểm chứng độc lập. Các điểm số trên chỉ đo khả năng bắt chước teacher, không đo độ chính xác phát hiện cháy. Chỉ số khoảng 99% trong metrics.json của mô hình TVOC cũ không đại diện nhánh đang dùng và không được sử dụng làm kết quả chính của báo cáo. Nhánh gas vẫn cần hiệu chuẩn và đánh giá riêng tại nơi lắp đặt. [T5, T9]

## 5.4. Kết quả dự báo trên artifact lịch sử

Các số liệu dưới đây trích từ artifact demand-20261027-4017802f93, lưu trong runtime/integration-live. Artifact dùng data_source=SIMULATED, được tạo ngày 28/10/2026 từ lịch sử tổng hợp 28/10/2024–27/10/2026. Đây là bằng chứng lịch sử trước V16. Qua rà soát các tệp metrics/metadata hiện có, chưa tìm thấy artifact đánh giá nguồn REAL sau V16; các kết quả này không được coi là phép đo mới cho pipeline hiện tại. [T12]

Validation bắt đầu 28/04/2026, test bắt đầu 28/07/2026. Phương án selected là chọn giữa hai LightGBM theo từng chuỗi bằng MAE validation. Trên 26.544 dự báo ngày của test, kết quả như sau:

| Phương pháp | MAE | WAPE (%) | Bias |
| --- | --- | --- | --- |
| Trung bình 28 ngày | 8,150 | 42,187 | −0,369 |
| Seasonal naive | 8,850 | 45,810 | −0,234 |
| Trung bình cùng thứ | 8,118 | 42,023 | −0,369 |
| LightGBM Poisson | 7,813 | 40,444 | 0,061 |
| LightGBM L1 | 6,394 | 33,097 | −3,261 |
| LightGBM chọn theo chuỗi | 6,386 | 33,054 | −3,220 |

<!-- FIGURE demand_metrics -->

Hình 5.2. So sánh MAE ngày và MAE tổng bảy ngày trên artifact tổng hợp trước V16. Đơn vị và số điểm đánh giá khác nhau giữa hai biểu đồ; chỉ so sánh các phương pháp trong cùng biểu đồ.

Phương án selected giảm MAE ngày so với các baseline trong artifact này nhưng có bias âm đáng kể. Với tổng bảy ngày, selected có MAE 31,820 và WAPE 23,557%, trong khi trung bình 28 ngày đạt MAE 22,241 và WAPE 16,466%. Trên tổng 14 ngày, selected có WAPE 22,248%, cao hơn Poisson 12,924%. [T12]

Kết quả cho thấy tối ưu MAE ngày không đồng thời tối ưu lượng nhập cộng dồn. Nếu mục tiêu ứng dụng là bổ sung hàng trong tuần, cần đánh giá lựa chọn mô hình theo chi phí thiếu hàng hoặc sai số tổng nhiều ngày. Hiện tại không thể kết luận LightGBM luôn tốt hơn baseline. Bộ mẫu tổng hợp không phản ánh đủ biến động nhu cầu, mất hàng bán do hết tồn và sai sót ghi nhận của kho thực tế.

## 5.5. Kết quả làm sạch giao diện

Các trang rỗng và mục điều hướng tương ứng được loại khỏi frontend. Các điều khiển trên trang đăng nhập và ô tìm kiếm header được giữ nguyên, kể cả các thành phần chưa được triển khai đầy đủ. Những tính năng có dữ liệu thật hoặc trạng thái chờ hợp lệ được giữ. Kiểm tra biên dịch xác nhận không còn lỗi import do xóa component; menu và route liên quan được cập nhật đồng bộ. Phạm vi chỉnh sửa không thay đổi schema hay quy tắc nghiệp vụ backend. [T10]

Đợt này chưa thực hiện nghiệm thu thao tác qua trình duyệt với backend, camera và thiết bị đang chạy. Do đó kết quả frontend được giới hạn ở rà soát mã, kiểm tra tham chiếu và build, không được mô tả thành kiểm thử đầu cuối đạt.

<!-- CHAPTER -->
# CHƯƠNG 6. ĐÁNH GIÁ, HƯỚNG PHÁT TRIỂN VÀ KẾT LUẬN

## 6.1. Đóng góp của dự án

Đóng góp chính là tích hợp các quy trình kho, số đo thiết bị và phân tích trong một mô hình dữ liệu có truy vết. Nhập/xuất được bảo vệ bằng transaction; cân tạo bằng chứng gắn với dòng hàng; giám sát gắn điều kiện môi trường với sản phẩm; dự báo được điều phối và lưu phiên bản. Kiến trúc phân biệt suy luận môi trường trong Java với dự báo Python, giúp làm rõ phụ thuộc vận hành.

Dự án cũng xây dựng các cơ chế cần thiết cho tính tái lập: migration có phiên bản, dữ liệu khởi tạo xác định, thông tin dataset và checksum, đánh giá theo thời gian và kiểm thử nghiệp vụ. Việc ghi rõ nguồn mô phỏng và phạm vi nghiệm thu giúp các kết quả có thể được thẩm tra thay vì chỉ dựa trên màn hình trình diễn.

## 6.2. Hạn chế và nguy cơ sai lệch

| Hạn chế | Hệ quả | Ưu tiên cải thiện |
| --- | --- | --- |
| Nhãn môi trường do teacher tạo; student tổng quát hóa kém | Chưa đủ cơ sở tin cậy cho cảnh báo nguy hiểm thực tế | Thu thập nhãn độc lập; đánh giá recall từng lớp và ngưỡng gas |
| Dự báo dựa trên lịch sử tổng hợp và lượng xuất quan sát | Chưa chứng minh hiệu quả với nhu cầu kinh doanh thực | Thu thập ledger thực, tình trạng hết hàng và các biến liên quan |
| Chọn mô hình theo MAE ngày | Có thể kém ở tổng tuần và dự toán bổ sung | Chọn tiêu chí gắn với tổng nhiều ngày, mức phục vụ và chi phí |
| Một ESP32, một cân và trạng thái trực tiếp trong RAM | Khả năng mở rộng hoặc chịu lỗi còn giới hạn | Định danh nhiều thiết bị, trạng thái dùng chung và theo dõi kết nối |
| Chưa nghiệm thu cloud/thiết bị | Chưa đánh giá đầy đủ TLS, mạng, cảm biến và điều khiển | Kiểm tra đầu cuối có log, mốc thời gian và tiêu chí đạt |
| Chưa có lead time, đơn mua đang về, lô và hạn dùng | Đề xuất nhập và xuất còn đơn giản | Bổ sung mô hình nghiệp vụ trước khi tối ưu thuật toán |

Tên nguồn REAL trong ledger sau V16 cần được hiểu theo hợp đồng phần mềm. Chất lượng thực tế phải được xác nhận bằng xuất xứ dữ liệu và quy trình ghi nhận, không chỉ bằng một giá trị enum. Tương tự, test đạt không thay cho hiệu chuẩn cân hoặc thử nghiệm cảm biến tại kho.

## 6.3. Hướng phát triển

Giai đoạn gần nên ưu tiên một luồng đầu cuối được kiểm chứng: đăng nhập, nhập hàng có cân, nhận telemetry qua HTTPS, xử lý cảnh báo, xuất hàng, tạo snapshot và công bố dự báo. Mỗi bước cần bằng chứng về thiết bị, phiên bản, thời điểm và dữ liệu, cùng kịch bản mất mạng, dữ liệu cũ, sai khóa và khởi động lại.

Với mô hình môi trường, cần thu thập dữ liệu có nhãn đáng tin cậy, đánh giá theo phiên/địa điểm và đối chiếu các quy tắc an toàn phù hợp. Với dự báo, cần chạy lại benchmark trên ledger sau V16, so sánh baseline ở cấp ngày và tuần, kiểm tra bias và đánh giá chi phí thiếu/dư hàng. Các tính năng như khoảng dự báo, tồn an toàn theo biến động và thời gian giao hàng nên được bổ sung khi dữ liệu nghiệp vụ đáp ứng.

Về kiến trúc, có thể mở rộng nhiều thiết bị, lưu trạng thái trực tiếp bền vững, giám sát vận hành và sao lưu phục hồi. Những trang chức năng đã loại bỏ chỉ nên được đưa trở lại khi có nội dung, API, quyền và tiêu chí kiểm tra rõ ràng.

## 6.4. Kết luận

Smart Warehouse đã hình thành nền tảng phần mềm liên kết nghiệp vụ quản lý kho với IoT, cân đối chiếu và dự báo. Các thành phần chính có mã triển khai và bằng chứng kiểm thử; giao diện sau rà soát phản ánh sát hơn phạm vi chức năng thực tế. Tuy nhiên, kết quả học máy còn giới hạn và nghiệm thu cloud/thiết bị chưa hoàn tất. Giá trị hiện tại của dự án nằm ở kiến trúc tích hợp, cơ chế đảm bảo tính nhất quán và khả năng tiếp tục kiểm chứng. Việc triển khai trong kho thực tế cần được quyết định sau khi hoàn thành đánh giá dữ liệu, thiết bị và vận hành đầu cuối.

<!-- CHAPTER -->
# TÀI LIỆU THAM KHẢO

## Nguồn học thuật và tài liệu chính thức

[S1] Breiman, L. (2001). Random Forests. Machine Learning, 45, 5–32. Bản tác giả: https://www.stat.berkeley.edu/users/breiman/randomforest2001.pdf. DOI: 10.1023/A:1010933404324. Truy cập 07/11/2026.

[S2] Ke, G., Meng, Q., Finley, T. và cộng sự (2017). LightGBM: A Highly Efficient Gradient Boosting Decision Tree. Advances in Neural Information Processing Systems 30. https://proceedings.neurips.cc/paper_files/paper/2017/hash/6449f44a102fde848669bdd9eb6b76fa-Abstract.html. Truy cập 07/11/2026.

[S3] Spring. Declarative Transaction Management. Tài liệu Spring Framework. https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html. Truy cập 07/11/2026.

[S4] Espressif Systems. ESP HTTP Client, ESP-IDF Programming Guide v5.5.6. https://docs.espressif.com/projects/esp-idf/en/v5.5.6/esp32/api-reference/protocols/esp_http_client.html. Truy cập 07/11/2026.

## Nguồn nội bộ của dự án

[T1] README.md; frontend/package.json; backend/iot/pom.xml; AI/demand/requirements.txt. Phạm vi chức năng, công nghệ và cách khởi chạy.

[T2] docs/architecture.md. Kiến trúc hiện hành, giới hạn thiết bị và trạng thái triển khai.

[T3] docs/inventory.md; docs/hx711-weighing.md. Nhập/xuất, mã quét và cân đối chiếu.

[T4] docs/product-environment-monitoring.md. Luồng giám sát bảo quản và phân biệt nguồn mô phỏng.

[T5] AI/HYBRID.md; AI/train_temperature_humidity.py. Mô hình môi trường hiện hành, nhãn và hạn chế.

[T6] docs/demand-forecasting.md; AI/demand/demand_model.py; AI/demand/service.py. Hợp đồng dữ liệu và quy trình dự báo.

[T7] docs/cloud-acceptance.md; docs/cloud-render.md; compose.yaml; render.yaml. Các mốc kiểm tra local, cấu hình cloud và hạng mục chưa nghiệm thu.

[T8] backend/iot/src/main/java/com/example/iot/; backend/iot/src/main/resources/db/migration/; frontend/src/; hardware/warehouse_ai/warehouse_ai.ino. Mã triển khai được đối chiếu.

[T9] AI/reports/temperature_humidity_metrics.json. Chỉ số mô hình hai đầu vào, cách chia phiên và ma trận nhầm lẫn.

[T10] frontend/src/router/router.jsx; frontend/src/layout/; frontend/src/pages/. Thay đổi làm sạch giao diện tại đợt báo cáo.

[T11] backend/iot/target/surefire-reports/TEST-*.xml; bộ unittest trong AI/demand/. Báo cáo Java đã lưu tại máy và 27 unittest Python chạy ngày 07/11/2026. Thư mục target bị Git bỏ qua.

[T12] AI/demand/runtime/integration-live/runs/f065d185-a601-46f3-9ba9-6ca7939dc647/artifacts/metrics.json và metadata.json. Artifact lịch sử demand-20261027-4017802f93; dữ liệu SIMULATED, trước V16. Runtime bị Git bỏ qua; số liệu chính đã được chép vào báo cáo để thẩm tra.

<!-- CHAPTER -->
# PHỤ LỤC

## A. Ma trận truy vết chức năng và mã nguồn

| Chức năng | Thành phần mã tiêu biểu |
| --- | --- |
| Điều hướng và phân quyền web | frontend/src/router/router.jsx; RequireRole.jsx; utils/auth.js |
| Nhập, xuất | InventoryController; InventoryService; WarehouseStockService; frontend/src/pages/staff/StockPage.jsx |
| Gợi ý kệ | PlacementService; frontend/src/components/ShelfDashboard.jsx |
| Cân và bằng chứng | ScaleController; ScaleService; ScaleTicket; frontend/src/components/ScaleLive.jsx |
| IoT và cảnh báo | WarehouseMonitoringService; HybridRiskService; TemperatureHumidityModel; ShelfEnvironmentService |
| Dự báo và bổ sung | DemandDataService; DemandForecastService; DemandJobService; ReplenishmentService |
| Cloud batch | CloudDemandBatchService; GitHubDemandDispatcher; AI/demand/cloud_runner.py |
| Học máy | AI/train_temperature_humidity.py; AI/demand/demand_model.py |
| Thiết bị | hardware/warehouse_ai/warehouse_ai.ino; config.example.h |

Các tên Java trong bảng thuộc backend/iot/src/main/java/com/example/iot/. Đây là chỉ dẫn tra cứu, không phải danh sách đầy đủ của mã nguồn.

## B. Quy trình tái lập kiểm tra

Để chạy frontend, cài dependency theo lockfile bằng npm ci tại frontend, sau đó npm run build; npm run dev dùng cho môi trường phát triển. Backend cần Java 17, Maven hoặc Maven Wrapper và MySQL cấu hình hợp lệ. Chi tiết biến môi trường và các lệnh khởi chạy nằm tại README.md; không sử dụng thông tin xác thực thật trong báo cáo.

Bộ kiểm thử dự báo có thể chạy tại thư mục AI/demand bằng môi trường .venv đã cài requirements: .venv/Scripts/python.exe -m unittest test_demand_model test_service test_cloud_runner. Kiểm thử backend có tương tác DB phải dùng datasource kiểm tra riêng; không suy ra kết quả chỉ từ việc biên dịch frontend.

Với phần cứng, cần xác nhận đúng board, chân tín hiệu, nguồn, cấu hình CA và khóa trước khi nạp. Hiệu chuẩn cân bằng vật chuẩn; sau đó kiểm tra ở nhiều mức tải hợp lệ. Cần lưu log telemetry, trạng thái lệnh và bằng chứng nhập/xuất để đánh giá đầu cuối. Tài liệu này không ghi nhận thao tác nạp hoặc đo phần cứng mới trong đợt biên soạn.

## C. Tiêu chí nghiệm thu đề xuất

| Nhóm kiểm tra | Điều kiện cần có bằng chứng |
| --- | --- |
| Nghiệp vụ | Nhập, xuất đúng tồn; từ chối vượt tồn; rollback; quyền hợp lệ và quyền bị từ chối |
| Cân | Đã hiệu chuẩn, ổn định, đúng dung sai; từ chối số đo lỗi/cũ và bằng chứng dùng lại |
| Môi trường | Đúng thiết bị, nguồn và dấu thời gian; phản hồi điều khiển hợp lệ; phân biệt mô phỏng |
| Dự báo | Snapshot đúng nguồn; train/predict tách biệt; checksum và phiên bản; đề xuất đổi theo tồn mới |
| Cloud | Frontend/backend truy cập được qua HTTPS; Flyway thành công; ESP xác minh CA; kiểm tra khác mạng |
| Phục hồi | Restart vẫn giữ dữ liệu cần lưu; sao lưu/khôi phục thử; trạng thái mất mạng và job lỗi rõ ràng |

Các tiêu chí này là kế hoạch đánh giá tiếp theo, không phải các kết quả đã được xác nhận đạt.
