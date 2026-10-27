package com.example.iot.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lịch sử mỗi lần nhập / xuất kho. */
@Entity
@Table(name = "stock_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransactionEntity {
    public enum StockType { IN, OUT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id")
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockType type;

    /** Số lượng nhập hoặc xuất (luôn là số dương). */
    @Column(nullable = false)
    private int quantity;

    /** Tồn kho sau khi thực hiện giao dịch. */
    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    /** Email người thực hiện. */
    @Column(name = "created_by")
    private String createdBy;

    @Column(length = 255)
    private String note;

    @jakarta.persistence.OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_ticket_id", unique = true)
    private ScaleTicket weighing;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
