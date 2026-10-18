package com.example.iot.dto.response;

import java.time.LocalDateTime;

/** Một dòng lịch sử nhập/xuất kho. */
public record StockHistoryItem(String sku, String name, String type, int quantity, int stockAfter,
        String createdBy, LocalDateTime createdAt) {
}