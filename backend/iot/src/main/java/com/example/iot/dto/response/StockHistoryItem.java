package com.example.iot.dto.response;

import java.time.LocalDateTime;

/** Một dòng lịch sử nhập/xuất kho (note ghi vị trí xếp hoặc lấy hàng). */
public record StockHistoryItem(String sku, String name, String type, int quantity, int stockAfter,
        String createdBy, LocalDateTime createdAt, String note) {
}