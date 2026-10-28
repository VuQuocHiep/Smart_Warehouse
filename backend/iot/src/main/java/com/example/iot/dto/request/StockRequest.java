package com.example.iot.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Body: { "items": [ { "sku": "SP001", "quantity": 2, "shelfId": "..." } ] } (shelfId chỉ dùng khi nhập kho) */
@Getter
@Setter
public class StockRequest {
    @NotEmpty(message = "Phiếu không có sản phẩm nào")
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        @NotBlank(message = "SKU không được để trống")
        private String sku;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải lớn hơn 0")
        private Integer quantity;

        /** Kệ được chọn để xếp hàng khi nhập kho (tùy chọn). */
        private String shelfId;
    }
}