INSERT INTO stock_transaction
    (transaction_id, product_id, type, quantity, stock_after, created_by, note, created_at)
VALUES
    ('TX-0001', 'SP001', 'IN',  50,  50, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ A1 · Kho 1', '2026-10-01 08:30:00.000000'),
    ('TX-0002', 'SP002', 'IN', 100, 100, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ A2 · Kho 1', '2026-10-01 08:45:00.000000'),
    ('TX-0003', 'SP003', 'IN',  40,  40, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ B1 · Kho 2', '2026-10-02 09:00:00.000000'),
    ('TX-0004', 'SP004', 'IN',  80,  80, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ B2 · Kho 2', '2026-10-02 09:20:00.000000'),
    ('TX-0005', 'SP005', 'IN',  25,  25, 'STAFFPTIT@gmail.com',   'Xếp vào Kệ C1 · Kho 2', '2026-10-03 08:50:00.000000'),
    ('TX-0006', 'SP001', 'OUT', 12,  38, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A1 (-12)',    '2026-10-03 10:15:00.000000'),
    ('TX-0007', 'SP002', 'OUT', 30,  70, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A2 (-30)',    '2026-10-04 14:05:00.000000'),
    ('TX-0008', 'SP003', 'OUT', 10,  30, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B1 (-10)',    '2026-10-05 11:30:00.000000'),
    ('TX-0009', 'SP001', 'OUT',  8,  30, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A1 (-8)',     '2026-10-06 15:20:00.000000'),
    ('TX-0010', 'SP004', 'OUT', 22,  58, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B2 (-22)',    '2026-10-07 09:40:00.000000'),
    ('TX-0011', 'SP002', 'OUT', 25,  45, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A2 (-25)',    '2026-10-08 13:10:00.000000'),
    ('TX-0012', 'SP001', 'IN',  20,  50, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ A1 · Kho 1', '2026-10-09 08:35:00.000000'),
    ('TX-0013', 'SP005', 'OUT',  5,  20, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ C1 (-5)',     '2026-10-10 16:00:00.000000'),
    ('TX-0014', 'SP003', 'IN',  15,  45, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ B1 · Kho 2', '2026-10-11 08:55:00.000000'),
    ('TX-0015', 'SP002', 'IN',  60, 105, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ A2 · Kho 1', '2026-10-12 09:25:00.000000'),
    ('TX-0016', 'SP004', 'OUT', 18,  40, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B2 (-18)',    '2026-10-13 10:45:00.000000'),
    ('TX-0017', 'SP001', 'OUT', 15,  35, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A1 (-15)',    '2026-10-14 14:30:00.000000'),
    ('TX-0018', 'SP005', 'IN',  10,  30, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ C1 · Kho 2', '2026-10-15 08:40:00.000000'),
    ('TX-0019', 'SP002', 'OUT', 40,  65, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A2 (-40)',    '2026-10-17 11:15:00.000000'),
    ('TX-0020', 'SP004', 'IN',  30,  70, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ B2 · Kho 2', '2026-10-18 09:05:00.000000'),
    ('TX-0021', 'SP003', 'OUT', 20,  25, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B1 (-20)',    '2026-10-19 15:50:00.000000'),
    ('TX-0022', 'SP001', 'OUT',  5,  30, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A1 (-5)',     '2026-10-21 10:25:00.000000'),
    ('TX-0023', 'SP005', 'OUT', 12,  18, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ C1 (-12)',    '2026-10-22 13:45:00.000000'),
    ('TX-0024', 'SP004', 'OUT', 25,  45, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B2 (-25)',    '2026-10-24 14:10:00.000000'),
    ('TX-0025', 'SP003', 'OUT',  5,  20, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B1 (-5)',     '2026-10-26 09:30:00.000000'),
    ('TX-0026', 'SP005', 'IN',   6,  24, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ C1 · Kho 2', '2026-10-28 12:24:00.000000');

-- Đồng bộ tồn kho hiện tại với "stock_after" cuối cùng của mỗi SKU
INSERT INTO inventory (inventory_id, product_id, quantity, updated_at)
VALUES
    ('INV-SP001', 'SP001', 30, '2026-10-21 10:25:00.000000'),
    ('INV-SP002', 'SP002', 65, '2026-10-17 11:15:00.000000'),
    ('INV-SP003', 'SP003', 20, '2026-10-26 09:30:00.000000'),
    ('INV-SP004', 'SP004', 45, '2026-10-24 14:10:00.000000'),
    ('INV-SP005', 'SP005', 24, '2026-10-28 12:24:00.000000')
ON DUPLICATE KEY UPDATE
    quantity   = VALUES(quantity),
    updated_at = VALUES(updated_at);