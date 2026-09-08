DROP DATABASE IF EXISTS smart_warehouse;

CREATE DATABASE smart_warehouse
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE smart_warehouse;


/* =====================================================
   1. USERS
===================================================== */

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,

    full_name VARCHAR(100) NOT NULL,

    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),

    role ENUM(
        'ADMIN',
        'MANAGER',
        'STAFF'
    ) NOT NULL DEFAULT 'STAFF',

    status TINYINT NOT NULL DEFAULT 1,

    -- Lần đăng nhập gần nhất
    last_login_at DATETIME NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);


/* =====================================================
   2. WAREHOUSE
===================================================== */

CREATE TABLE warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Ví dụ: WH001
    code VARCHAR(50) NOT NULL UNIQUE,

    name VARCHAR(100) NOT NULL,

    address VARCHAR(255),

    description TEXT,

    status TINYINT NOT NULL DEFAULT 1,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);


/* =====================================================
   3. PRODUCT
===================================================== */

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    sku VARCHAR(50) NOT NULL UNIQUE,

    name VARCHAR(150) NOT NULL,

    -- Ví dụ: Điện tử, Thực phẩm, Gia dụng...
    category VARCHAR(100) NULL,

    description TEXT,

    -- Ảnh sản phẩm cho dashboard
    image_url VARCHAR(255) NULL,

    -- Khối lượng 1 sản phẩm, đơn vị kg
    unit_weight DECIMAL(10,3) NOT NULL,

    -- ITEM, BOX, BAG...
    unit VARCHAR(20) NOT NULL DEFAULT 'ITEM',

    status TINYINT NOT NULL DEFAULT 1,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_product_weight
        CHECK (unit_weight > 0)
);


/* =====================================================
   4. SHELF

   Quy ước:
   1 kệ được Load Cell cân = 1 SKU
===================================================== */

CREATE TABLE shelf (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    warehouse_id BIGINT NOT NULL,

    -- Mỗi kệ chứa 1 SKU
    product_id BIGINT NOT NULL,

    name VARCHAR(50) NOT NULL,

    location VARCHAR(100),

    -- Khối lượng kệ khi không có hàng
    static_weight DECIMAL(10,3)
        NOT NULL DEFAULT 0,

    -- Tải trọng tối đa của kệ (kg)
    capacity_weight DECIMAL(10,3) NULL,

    -- Ngưỡng tồn kho tối thiểu
    min_quantity INT NOT NULL DEFAULT 0,

    -- Số lượng tối đa trên kệ
    max_quantity INT NULL,

    status TINYINT NOT NULL DEFAULT 1,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_shelf_warehouse
        FOREIGN KEY (warehouse_id)
        REFERENCES warehouse(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_shelf_product
        FOREIGN KEY (product_id)
        REFERENCES product(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT uq_warehouse_shelf
        UNIQUE (warehouse_id, name),

    /*
       Dùng cho khóa ngoại kép trong IOT_DEVICE.
       Đảm bảo thiết bị gắn đúng kệ của đúng kho.
    */
    CONSTRAINT uq_shelf_id_warehouse
        UNIQUE (id, warehouse_id),

    CONSTRAINT chk_static_weight
        CHECK (static_weight >= 0),

    CONSTRAINT chk_capacity_weight
        CHECK (
            capacity_weight IS NULL
            OR capacity_weight > 0
        ),

    CONSTRAINT chk_min_quantity
        CHECK (min_quantity >= 0),

    CONSTRAINT chk_max_quantity
        CHECK (
            max_quantity IS NULL
            OR max_quantity >= min_quantity
        )
);


/* =====================================================
   5. RFID TAG
===================================================== */

CREATE TABLE rfid_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- UID đọc được từ thẻ RFID
    uid VARCHAR(100) NOT NULL UNIQUE,

    product_id BIGINT NOT NULL,

    status TINYINT NOT NULL DEFAULT 1,

    -- Lần cuối RFID được quét
    last_scanned_at DATETIME NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rfid_product
        FOREIGN KEY (product_id)
        REFERENCES product(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);


/* =====================================================
   6. INVENTORY

   1 SHELF = 1 INVENTORY
===================================================== */

CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    shelf_id BIGINT NOT NULL UNIQUE,

    -- Số lượng theo hệ thống RFID
    system_quantity INT NOT NULL DEFAULT 0,

    -- Số lượng thực tế tính từ Load Cell
    actual_quantity INT NULL,

    -- Khối lượng gần nhất Load Cell đọc được
    last_weight DECIMAL(10,3) NULL,

    -- Lần đối chiếu tồn kho gần nhất
    last_count_at DATETIME NULL,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_shelf
        FOREIGN KEY (shelf_id)
        REFERENCES shelf(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT chk_inventory_system_quantity
        CHECK (system_quantity >= 0),

    CONSTRAINT chk_inventory_actual_quantity
        CHECK (
            actual_quantity IS NULL
            OR actual_quantity >= 0
        ),

    CONSTRAINT chk_inventory_weight
        CHECK (
            last_weight IS NULL
            OR last_weight >= 0
        )
);


/* =====================================================
   7. IOT DEVICE

   Đại diện:
   - ESP32 môi trường
   - RFID nhập
   - RFID xuất
   - Load Cell + HX711
   - OLED
===================================================== */

CREATE TABLE iot_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Ví dụ ESP_ENV_01
    device_code VARCHAR(50) NOT NULL UNIQUE,

    name VARCHAR(100) NOT NULL,

    type ENUM(
        'ENVIRONMENT',
        'RFID_IMPORT',
        'RFID_EXPORT',
        'WEIGHT',
        'DISPLAY'
    ) NOT NULL,

    warehouse_id BIGINT NOT NULL,

    /*
       Có thể NULL.
       Load Cell thường có shelf_id.
       RFID hoặc ESP32 môi trường có thể chỉ thuộc kho.
    */
    shelf_id BIGINT NULL,

    -- IPv4 hoặc IPv6
    ip_address VARCHAR(45) NULL,

    -- Phiên bản firmware ESP32
    firmware_version VARCHAR(50) NULL,

    status TINYINT NOT NULL DEFAULT 1,

    -- Backend dùng để phát hiện thiết bị mất kết nối
    last_seen_at DATETIME NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_iot_warehouse
        FOREIGN KEY (warehouse_id)
        REFERENCES warehouse(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_iot_shelf
        FOREIGN KEY (shelf_id, warehouse_id)
        REFERENCES shelf(id, warehouse_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);


/* =====================================================
   8. SENSOR READING

   Lưu dữ liệu:
   DHT22 / MQ-2 / Load Cell
===================================================== */

CREATE TABLE sensor_reading (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    device_id BIGINT NOT NULL,

    -- °C
    temperature DECIMAL(5,2) NULL,

    -- %
    humidity DECIMAL(5,2) NULL,

    -- Giá trị gas / smoke
    gas_value DECIMAL(10,2) NULL,

    -- kg
    weight DECIMAL(10,3) NULL,

    /*
       Dùng cho Load Cell:
       1 = trọng lượng đã ổn định khoảng 3 giây
       0 = chưa ổn định
    */
    is_stable TINYINT NOT NULL DEFAULT 0,

    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reading_device
        FOREIGN KEY (device_id)
        REFERENCES iot_device(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT chk_temperature
        CHECK (
            temperature IS NULL
            OR temperature BETWEEN -100 AND 200
        ),

    CONSTRAINT chk_humidity
        CHECK (
            humidity IS NULL
            OR humidity BETWEEN 0 AND 100
        ),

    CONSTRAINT chk_gas
        CHECK (
            gas_value IS NULL
            OR gas_value >= 0
        ),

    CONSTRAINT chk_reading_weight
        CHECK (
            weight IS NULL
            OR weight >= 0
        )
);


/* =====================================================
   9. THRESHOLD SETTING

   Ngưỡng cảnh báo môi trường
===================================================== */

CREATE TABLE threshold_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    device_id BIGINT NOT NULL,

    param_type ENUM(
        'TEMPERATURE',
        'HUMIDITY',
        'GAS'
    ) NOT NULL,

    min_value DECIMAL(10,2) NULL,

    max_value DECIMAL(10,2) NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_threshold_device
        FOREIGN KEY (device_id)
        REFERENCES iot_device(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    /*
       Mỗi thiết bị chỉ có 1 cấu hình
       cho mỗi loại thông số
    */
    CONSTRAINT uq_device_threshold
        UNIQUE (device_id, param_type),

    CONSTRAINT chk_threshold_range
        CHECK (
            min_value IS NULL
            OR max_value IS NULL
            OR min_value <= max_value
        )
);


/* =====================================================
   10. INVENTORY TRANSACTION

   Lịch sử nhập / xuất RFID
===================================================== */

CREATE TABLE inventory_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    shelf_id BIGINT NOT NULL,

    /*
       IMPORT / EXPORT bằng RFID thì có tag.
       ADJUST thủ công có thể NULL.
    */
    rfid_tag_id BIGINT NULL,

    /*
       RFID_IMPORT hoặc RFID_EXPORT.
       ADJUST thủ công có thể NULL.
    */
    reader_device_id BIGINT NULL,

    type ENUM(
        'IMPORT',
        'EXPORT',
        'ADJUST'
    ) NOT NULL,

    /*
       IMPORT = số dương
       EXPORT = số âm
       ADJUST = +/- tùy chỉnh
    */
    quantity INT NOT NULL,

    -- Trạng thái xử lý giao dịch
    status ENUM(
        'SUCCESS',
        'FAILED'
    ) NOT NULL DEFAULT 'SUCCESS',

    /*
       NULL nếu ESP32 tự động tạo giao dịch.
       Có giá trị nếu user điều chỉnh thủ công.
    */
    created_by BIGINT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    note VARCHAR(255) NULL,

    CONSTRAINT fk_transaction_shelf
        FOREIGN KEY (shelf_id)
        REFERENCES shelf(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_transaction_rfid
        FOREIGN KEY (rfid_tag_id)
        REFERENCES rfid_tag(id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_transaction_device
        FOREIGN KEY (reader_device_id)
        REFERENCES iot_device(id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_transaction_user
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT chk_transaction_quantity
        CHECK (
            (type = 'IMPORT' AND quantity > 0)
            OR
            (type = 'EXPORT' AND quantity < 0)
            OR
            (type = 'ADJUST' AND quantity <> 0)
        )

    /*
       Lưu ý: quy tắc "IMPORT/EXPORT bắt buộc có rfid_tag_id
       và reader_device_id" KHÔNG thể viết bằng CHECK constraint
       ở đây, vì MySQL cấm CHECK tham chiếu tới cột đang có
       FK với hành động ON UPDATE CASCADE (lỗi 3823).
       Quy tắc này được enforce bằng TRIGGER bên dưới, ngay
       sau khi tạo xong toàn bộ bảng.
    */
);


/* =====================================================
   11. ALERT
===================================================== */

CREATE TABLE alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    type ENUM(
        'TEMP_HIGH',
        'HUMIDITY_HIGH',
        'HUMIDITY_LOW',
        'GAS_DETECTED',
        'FIRE',
        'STOCK_MISMATCH',
        'LOW_STOCK',
        'DEVICE_OFFLINE',
        'SYSTEM'
    ) NOT NULL,

    /*
       Có thể NULL với cảnh báo do backend/system tạo.
       Ví dụ LOW_STOCK hoặc SYSTEM.
    */
    device_id BIGINT NULL,

    /*
       STOCK_MISMATCH / LOW_STOCK thường có shelf_id.
       Cảnh báo môi trường có thể NULL.
    */
    shelf_id BIGINT NULL,

    -- Tiêu đề hiển thị dashboard
    title VARCHAR(150) NOT NULL,

    message TEXT NOT NULL,

    severity ENUM(
        'INFO',
        'WARNING',
        'CRITICAL'
    ) NOT NULL DEFAULT 'WARNING',

    status ENUM(
        'UNREAD',
        'READ',
        'RESOLVED'
    ) NOT NULL DEFAULT 'UNREAD',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    resolved_at DATETIME NULL,

    CONSTRAINT fk_alert_device
        FOREIGN KEY (device_id)
        REFERENCES iot_device(id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_alert_shelf
        FOREIGN KEY (shelf_id)
        REFERENCES shelf(id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
);


/* =====================================================
   INDEX
===================================================== */

CREATE INDEX idx_sensor_device_time
ON sensor_reading(device_id, recorded_at);


CREATE INDEX idx_transaction_time
ON inventory_transaction(created_at);


CREATE INDEX idx_transaction_shelf
ON inventory_transaction(shelf_id);


CREATE INDEX idx_transaction_rfid
ON inventory_transaction(rfid_tag_id);


CREATE INDEX idx_transaction_device
ON inventory_transaction(reader_device_id);


CREATE INDEX idx_alert_time
ON alert(created_at);


CREATE INDEX idx_alert_status
ON alert(status);


CREATE INDEX idx_alert_device
ON alert(device_id);


CREATE INDEX idx_alert_shelf
ON alert(shelf_id);


CREATE INDEX idx_device_last_seen
ON iot_device(last_seen_at);


/* =====================================================
   TRIGGERS

   Thay thế cho 2 CHECK constraint không hợp lệ trên
   inventory_transaction (chk_transaction_rfid,
   chk_transaction_reader).

   MySQL không cho phép CHECK tham chiếu tới 1 cột đang
   là FK có hành động ON UPDATE CASCADE
   (lỗi 3823: "Column ... needed in a foreign key
   constraint referential action"). rfid_tag_id và
   reader_device_id đều rơi vào trường hợp này, nên quy
   tắc nghiệp vụ "IMPORT/EXPORT bắt buộc có rfid_tag_id
   và reader_device_id, ADJUST thì không bắt buộc" được
   chuyển sang trigger.
===================================================== */

DELIMITER $$

CREATE TRIGGER trg_transaction_check_insert
BEFORE INSERT ON inventory_transaction
FOR EACH ROW
BEGIN
    IF NEW.type <> 'ADJUST' AND NEW.rfid_tag_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'IMPORT/EXPORT transactions require rfid_tag_id';
    END IF;

    IF NEW.type <> 'ADJUST' AND NEW.reader_device_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'IMPORT/EXPORT transactions require reader_device_id';
    END IF;
END$$

CREATE TRIGGER trg_transaction_check_update
BEFORE UPDATE ON inventory_transaction
FOR EACH ROW
BEGIN
    IF NEW.type <> 'ADJUST' AND NEW.rfid_tag_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'IMPORT/EXPORT transactions require rfid_tag_id';
    END IF;

    IF NEW.type <> 'ADJUST' AND NEW.reader_device_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'IMPORT/EXPORT transactions require reader_device_id';
    END IF;
END$$

DELIMITER ;