package com.example.iot.dto.response;

/** Kết quả cho từng dòng của phiếu: số lượng đã nhập/xuất và tồn kho mới. */
public record StockItemResult(String sku, String name, int quantity, int stock) {
}