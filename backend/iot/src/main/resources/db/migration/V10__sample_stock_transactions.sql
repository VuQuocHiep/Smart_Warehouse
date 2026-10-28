INSERT INTO stock_transaction
    (transaction_id, product_id, type, quantity, stock_after, created_by, note, created_at)
WITH m (tx_id, rn, tx_type, qty, stock_after, created_by, note, created_at) AS (
    VALUES
    ROW('TX-0001', 1, 'IN',  50,  50, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ A1 · Kho 1', '2026-10-01 08:30:00'),
    ROW('TX-0002', 2, 'IN', 100, 100, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ A2 · Kho 1', '2026-10-01 08:45:00'),
    ROW('TX-0003', 3, 'IN',  40,  40, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ B1 · Kho 2', '2026-10-02 09:00:00'),
    ROW('TX-0004', 4, 'IN',  80,  80, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ B2 · Kho 2', '2026-10-02 09:20:00'),
    ROW('TX-0005', 5, 'IN',  25,  25, 'STAFFPTIT@gmail.com',   'Xếp vào Kệ C1 · Kho 2', '2026-10-03 08:50:00'),
    ROW('TX-0006', 1, 'OUT', 12,  38, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A1 (-12)',    '2026-10-03 10:15:00'),
    ROW('TX-0007', 2, 'OUT', 30,  70, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A2 (-30)',    '2026-10-04 14:05:00'),
    ROW('TX-0008', 3, 'OUT', 10,  30, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B1 (-10)',    '2026-10-05 11:30:00'),
    ROW('TX-0009', 1, 'OUT',  8,  30, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A1 (-8)',     '2026-10-06 15:20:00'),
    ROW('TX-0010', 4, 'OUT', 22,  58, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B2 (-22)',    '2026-10-07 09:40:00'),
    ROW('TX-0011', 2, 'OUT', 25,  45, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A2 (-25)',    '2026-10-08 13:10:00'),
    ROW('TX-0012', 1, 'IN',  20,  50, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ A1 · Kho 1', '2026-10-09 08:35:00'),
    ROW('TX-0013', 5, 'OUT',  5,  20, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ C1 (-5)',     '2026-10-10 16:00:00'),
    ROW('TX-0014', 3, 'IN',  15,  45, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ B1 · Kho 2', '2026-10-11 08:55:00'),
    ROW('TX-0015', 2, 'IN',  60, 105, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ A2 · Kho 1', '2026-10-12 09:25:00'),
    ROW('TX-0016', 4, 'OUT', 18,  40, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B2 (-18)',    '2026-10-13 10:45:00'),
    ROW('TX-0017', 1, 'OUT', 15,  35, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A1 (-15)',    '2026-10-14 14:30:00'),
    ROW('TX-0018', 5, 'IN',  10,  30, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ C1 · Kho 2', '2026-10-15 08:40:00'),
    ROW('TX-0019', 2, 'OUT', 40,  65, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A2 (-40)',    '2026-10-17 11:15:00'),
    ROW('TX-0020', 4, 'IN',  30,  70, 'ADMINPTIT@gmail.com',   'Xếp vào Kệ B2 · Kho 2', '2026-10-18 09:05:00'),
    ROW('TX-0021', 3, 'OUT', 20,  25, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B1 (-20)',    '2026-10-19 15:50:00'),
    ROW('TX-0022', 1, 'OUT',  5,  30, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ A1 (-5)',     '2026-10-21 10:25:00'),
    ROW('TX-0023', 5, 'OUT', 12,  18, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ C1 (-12)',    '2026-10-22 13:45:00'),
    ROW('TX-0024', 4, 'OUT', 25,  45, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B2 (-25)',    '2026-10-24 14:10:00'),
    ROW('TX-0025', 3, 'OUT',  5,  20, 'STAFFPTIT@gmail.com',   'Lấy từ Kệ B1 (-5)',     '2026-10-26 09:30:00'),
    ROW('TX-0026', 5, 'IN',   6,  24, 'MANAGERPTIT@gmail.com', 'Xếp vào Kệ C1 · Kho 2', '2026-10-28 12:24:00')
)
SELECT m.tx_id, p.product_id, m.tx_type, m.qty, m.stock_after, m.created_by, m.note, m.created_at
FROM m
JOIN (
    SELECT product_id, ROW_NUMBER() OVER (ORDER BY product_id) AS rn
    FROM product
) p ON p.rn = m.rn;


-- Đồng bộ tồn kho hiện tại với stock_after cuối cùng của mỗi sản phẩm
INSERT INTO inventory (inventory_id, product_id, quantity, updated_at)
WITH m (rn, qty, updated_at) AS (
    VALUES
    ROW(1, 30, '2026-10-21 10:25:00'),
    ROW(2, 65, '2026-10-17 11:15:00'),
    ROW(3, 20, '2026-10-26 09:30:00'),
    ROW(4, 45, '2026-10-24 14:10:00'),
    ROW(5, 24, '2026-10-28 12:24:00')
)
SELECT CONCAT('INV-', p.product_id), p.product_id, m.qty, m.updated_at
FROM m
JOIN (
    SELECT product_id, ROW_NUMBER() OVER (ORDER BY product_id) AS rn
    FROM product
) p ON p.rn = m.rn
ON DUPLICATE KEY UPDATE
    quantity   = VALUES(quantity),
    updated_at = VALUES(updated_at);