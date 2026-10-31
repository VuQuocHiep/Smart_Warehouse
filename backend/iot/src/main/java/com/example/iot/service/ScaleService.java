package com.example.iot.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.example.iot.entity.*;
import com.example.iot.repository.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScaleService {
    public static final double CAPACITY_GRAMS = 5000;
    private final ProductRepository products;
    private final ScaleTicketRepository tickets;
    private final ScaleSettingsRepository settings;
    @Value("${iot.scale.device-code:ESP_ENV_01}")
    private String deviceCode = "ESP_ENV_01";

    public record Reading(String deviceCode, Double weightGrams, Double grossGrams, boolean stable,
            boolean calibrated, boolean tared, boolean overloaded, String status,
            String commandId, String commandResult) {}
    public record Command(String id, String type, double referenceGrams, Instant expiresAt) {}
    public record Status(Reading reading, Instant receivedAt, boolean online, Command pending,
            ScaleSettings settings, double capacityGrams) {}
    private Reading latest;
    private Instant receivedAt;
    private Command pending;

    public synchronized Command receive(Reading reading) {
        require(reading != null && deviceCode.equals(reading.deviceCode()), "Mã thiết bị cân không hợp lệ");
        require(finiteOrNull(reading.weightGrams()) && finiteOrNull(reading.grossGrams()), "Số cân không hợp lệ");
        latest = reading;
        receivedAt = Instant.now();
        if (pending != null && (pending.id().equals(reading.commandId()) || !pending.expiresAt().isAfter(receivedAt))) pending = null;
        return pending;
    }

    public synchronized Status status() {
        if (pending != null && !pending.expiresAt().isAfter(Instant.now())) pending = null;
        return new Status(latest, receivedAt, online(), pending, getSettings(), CAPACITY_GRAMS);
    }

    public ScaleSettings getSettings() { return settings.findById(1).orElseGet(ScaleSettings::new); }

    @Transactional
    public ScaleSettings saveSettings(double absolute, double percent) {
        require(Double.isFinite(absolute) && absolute >= 0 && absolute <= 500, "Sai số tuyệt đối phải từ 0 đến 500 g");
        require(Double.isFinite(percent) && percent >= 0 && percent <= 20, "Sai số phần trăm phải từ 0 đến 20%");
        ScaleSettings value = getSettings();
        value.setAbsoluteGrams(absolute); value.setPercent(percent);
        return settings.save(value);
    }

    public synchronized Command command(String type, double reference) {
        require(online(), "Cân mất kết nối");
        require(pending == null || !pending.expiresAt().isAfter(Instant.now()), "Đang chờ cân xử lý lệnh trước");
        require("TARE".equals(type) || "CALIBRATE".equals(type), "Lệnh không hợp lệ");
        if ("CALIBRATE".equals(type)) require(Double.isFinite(reference) && reference >= 10 && reference <= CAPACITY_GRAMS,
                "Quả cân chuẩn phải từ 10 đến 5000 g");
        pending = new Command(UUID.randomUUID().toString(), type, reference, Instant.now().plusSeconds(30));
        // A command invalidates the last sample until the device reports its result.
        latest = new Reading(deviceCode, null, null, false, false, false, false, "COMMAND_PENDING", null, null);
        return pending;
    }

    @Transactional
    public synchronized ScaleTicket capture(String sku, int quantity, String type, String user) {
        require(quantity > 0 && ("IN".equals(type) || "OUT".equals(type)), "Thông tin phiếu không hợp lệ");
        require(online() && pending == null, "Cân mất kết nối hoặc đang xử lý lệnh");
        require(latest != null && "READY".equals(latest.status()) && latest.calibrated() && latest.tared()
                && latest.stable(), "Cân chưa hiệu chuẩn, trừ bì hoặc số đo chưa ổn định");
        require(latest.weightGrams() != null && latest.grossGrams() != null && !latest.overloaded()
                && latest.grossGrams() >= 0 && latest.grossGrams() <= CAPACITY_GRAMS
                && latest.weightGrams() > 0 && latest.weightGrams() <= CAPACITY_GRAMS, "Cân quá tải hoặc khối lượng không hợp lệ");
        var product = products.findBySkuAndDeletedFalse(sku).orElseThrow(() -> bad("SKU không tồn tại"));
        double expected = product.getUnit_weight() * quantity;
        require(Double.isFinite(expected) && expected > 0 && expected <= CAPACITY_GRAMS,
                "Khối lượng dự kiến không hợp lệ hoặc vượt 5 kg; hãy chia nhỏ phiếu");
        var config = getSettings();
        double tolerance = tolerance(expected, config.getAbsoluteGrams(), config.getPercent());
        require(withinTolerance(latest.weightGrams(), expected, tolerance),
                "Sai lệch khối lượng quá lớn: cân " + latest.weightGrams() + " g, dự kiến " + expected + " g, cho phép ±" + tolerance + " g");
        ScaleTicket ticket = new ScaleTicket();
        ticket.setSku(sku); ticket.setQuantity(quantity); ticket.setStockType(type);
        ticket.setCapturedBy(user); ticket.setCapturedAt(Instant.now());
        ticket.setWeightGrams(latest.weightGrams()); ticket.setGrossGrams(latest.grossGrams());
        ticket.setExpectedGrams(expected); ticket.setToleranceGrams(tolerance);
        return tickets.save(ticket);
    }

    /** Called inside the stock transaction; the lock prevents reusing a weighing concurrently. */
    public ScaleTicket consume(String id, String sku, int quantity, String type, String user, double unitWeight) {
        ScaleTicket ticket = tickets.lockById(id).orElseThrow(() -> bad("Không tìm thấy lần cân"));
        require(!ticket.isConsumed() && ticket.getCapturedAt().isAfter(Instant.now().minusSeconds(600)), "Lần cân đã dùng hoặc quá 10 phút; hãy cân lại");
        require(ticket.getSku().equals(sku) && ticket.getQuantity() == quantity && ticket.getStockType().equals(type)
                && ticket.getCapturedBy().equals(user), "Lần cân không khớp SKU, số lượng, loại phiếu hoặc người thực hiện");
        require(Math.abs(ticket.getExpectedGrams() - unitWeight * quantity) < 0.001, "Trọng lượng chuẩn đã thay đổi; hãy cân lại");
        ticket.setConsumed(true);
        tickets.save(ticket);
        return ticket;
    }

    public static double tolerance(double expected, double absolute, double percent) { return Math.max(absolute, expected * percent / 100); }
    public static boolean withinTolerance(double actual, double expected, double tolerance) {
        return Double.isFinite(actual) && Double.isFinite(expected) && Double.isFinite(tolerance)
                && tolerance >= 0 && Math.abs(actual - expected) <= tolerance + 1e-9;
    }
    private boolean online() { return receivedAt != null && receivedAt.isAfter(Instant.now().minusSeconds(8)); }
    private static boolean finiteOrNull(Double value) { return value == null || Double.isFinite(value); }
    private static void require(boolean ok, String message) { if (!ok) throw bad(message); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
