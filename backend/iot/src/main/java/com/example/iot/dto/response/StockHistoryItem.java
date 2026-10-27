package com.example.iot.dto.response;

import java.time.LocalDateTime;

/** Một dòng lịch sử nhập/xuất kho (note ghi vị trí xếp hoặc lấy hàng). */
public record StockHistoryItem(String sku, String name, String type, int quantity, int stockAfter,
        String createdBy, LocalDateTime createdAt, String note, WeightEvidence weighing) {
    public record WeightEvidence(String id, double weightGrams, double grossGrams, double expectedGrams,
            double toleranceGrams, java.time.Instant capturedAt) {
        public static WeightEvidence from(com.example.iot.entity.ScaleTicket ticket) {
            return ticket == null ? null : new WeightEvidence(ticket.getId(), ticket.getWeightGrams(), ticket.getGrossGrams(),
                    ticket.getExpectedGrams(), ticket.getToleranceGrams(), ticket.getCapturedAt());
        }
    }
}
