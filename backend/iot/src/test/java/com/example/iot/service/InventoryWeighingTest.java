package com.example.iot.service;

import java.util.List;
import java.util.Optional;
import com.example.iot.dto.request.StockRequest;
import com.example.iot.entity.*;
import com.example.iot.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryWeighingTest {
    InventoryRepository inventory = mock(InventoryRepository.class);
    StockTransactionRepository transactions = mock(StockTransactionRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    ShelfStockRepository shelves = mock(ShelfStockRepository.class);
    ScaleService scale = mock(ScaleService.class);
    InventoryService service = new InventoryService(inventory, transactions, products, mock(MonitoredShelfRepository.class),
            shelves, mock(WarehouseRepository.class), scale);
    ProductEntity product = ProductEntity.builder().productId("product").sku("SKU").name("Test").unit_weight(100).build();
    @BeforeEach void setup() {
        when(products.findBySkuAndDeletedFalse("SKU")).thenReturn(Optional.of(product));
        when(inventory.findByProduct(product)).thenReturn(Optional.of(InventoryEntity.builder().product(product).quantity(10).build()));
        when(shelves.findByProductIdOrderByQuantityDesc("product")).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operator", null, List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    StockRequest request(String id, boolean required) {
        var item = new StockRequest.Item(); item.setSku("SKU"); item.setQuantity(2); item.setScaleTicketId(id); item.setWeighRequired(required);
        var request = new StockRequest(); request.setItems(List.of(item)); return request;
    }
    @Test void requiredWeighingCannotBeOmitted() {
        assertThrows(ResponseStatusException.class, () -> service.stockIn(request(null, true)));
        verify(inventory, never()).save(any()); verifyNoInteractions(transactions);
    }
    @Test void acceptedEvidenceIsAttachedForBothInAndOut() {
        var ticket = new ScaleTicket();
        for (String type : List.of("IN", "OUT")) {
            when(scale.consume("ticket", "SKU", 2, type, "operator", 100)).thenReturn(ticket);
            if (type.equals("IN")) service.stockIn(request("ticket", true)); else service.stockOut(request("ticket", true));
            verify(scale).consume("ticket", "SKU", 2, type, "operator", 100);
        }
        verify(transactions, times(2)).save(argThat(t -> t.getWeighing() == ticket));
    }
    @Test void invalidEvidenceStopsStockMutation() {
        when(scale.consume(anyString(), anyString(), anyInt(), anyString(), anyString(), anyDouble()))
            .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "expired"));
        assertThrows(ResponseStatusException.class, () -> service.stockOut(request("expired", true)));
        verify(inventory, never()).save(any()); verifyNoInteractions(transactions);
    }
}
