package com.example.iot.service;

import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DemandDataService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final String source;
    public DemandDataService(JdbcTemplate jdbc, @Value("${demand.data-source:SIMULATED}") String source) {
        this.jdbc = jdbc;
        if (!Set.of("REAL", "SIMULATED").contains(source)) throw new IllegalArgumentException("Invalid demand.data-source");
        this.source = source;
    }
    public record Pair(String warehouseId, String warehouseName, String productId, String sku, String productName) {}
    public record Snapshot(List<Pair> pairs, List<Map<String,Object>> rows, String dataCutoff, String dataSource, String datasetId) {}
    public String dataSource() { return source; }

    public List<Pair> catalog() {
        return jdbc.query("""
            SELECT DISTINCT w.warehouse_id,w.name,p.product_id,p.sku,p.name
            FROM warehouse w JOIN (
                SELECT s.warehouse_id,sp.product_id FROM monitored_shelf s JOIN monitored_shelf_product sp ON sp.shelf_id=s.id
                UNION SELECT warehouse_id,product_id FROM warehouse_inventory
                UNION SELECT warehouse_id,product_id FROM demand_series
            ) c ON c.warehouse_id=w.warehouse_id JOIN product p ON p.product_id=c.product_id
            WHERE w.deleted=0 AND p.deleted=0 ORDER BY w.name,p.sku
            """, (rs,n) -> new Pair(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Snapshot snapshot() {
        LocalDate cutoff = LocalDate.now(ZONE).minusDays(1);
        var series = jdbc.queryForList("SELECT ds.* FROM demand_series ds JOIN warehouse w ON w.warehouse_id=ds.warehouse_id JOIN product p ON p.product_id=ds.product_id WHERE ds.data_source=? AND w.deleted=0 AND p.deleted=0 ORDER BY ds.warehouse_id,ds.product_id", source);
        Map<String,Long> quantities = new HashMap<>();
        jdbc.query("""
            SELECT warehouse_id,product_id,DATE(created_at+INTERVAL 7 HOUR) AS day,SUM(quantity) AS qty
            FROM stock_transaction WHERE type='OUT' AND warehouse_id IS NOT NULL AND data_source=?
              AND created_at < ? GROUP BY warehouse_id,product_id,DATE(created_at+INTERVAL 7 HOUR)
            """, rs -> { quantities.put(rs.getString(1)+":"+rs.getString(2)+":"+rs.getDate(3).toLocalDate(),rs.getLong(4)); },
            source, java.sql.Timestamp.valueOf(cutoff.plusDays(1).atStartOfDay().minusHours(7)));
        List<Map<String,Object>> rows = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        StringBuilder identity = new StringBuilder(source);
        for (var s : series) {
            String wh = s.get("warehouse_id").toString(), pid = s.get("product_id").toString();
            LocalDate start = ((java.sql.Date)s.get("history_start")).toLocalDate();
            LocalDate end = s.get("complete_through") == null ? cutoff : ((java.sql.Date)s.get("complete_through")).toLocalDate();
            if (end.isAfter(cutoff)) end = cutoff;
            identity.append('|').append(wh).append(':').append(pid).append(':').append(start).append(':').append(s.get("seed_batch_id")).append(':').append(s.get("complete_through"));
            if (start.isAfter(end)) continue;
            keys.add(wh+":"+pid);
            for (LocalDate day=start; !day.isAfter(end); day=day.plusDays(1)) {
                rows.add(Map.of("date",day.toString(),"warehouse_id",wh,"product_id",pid,
                        "export_quantity",quantities.getOrDefault(wh+":"+pid+":"+day,0L),
                        "data_quality","COMPLETE","data_source",source));
            }
        }
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chưa có lịch sử đầy đủ cho nguồn dữ liệu "+source+".");
        var pairs = catalog().stream().filter(p -> keys.contains(p.warehouseId()+":"+p.productId())).toList();
        if (pairs.size()>64) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hỗ trợ tối đa 64 cặp kho/sản phẩm mỗi model.");
        try {
            String id = source+"-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.toString().getBytes(StandardCharsets.UTF_8))).substring(0,32);
            return new Snapshot(pairs,rows,cutoff.toString(),source,id);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> balances() {
        return jdbc.queryForList("""
            SELECT wi.warehouse_id,wi.product_id,wi.quantity AS stock,COALESCE(dp.safety_stock,0) AS safety_stock,
              (SELECT COALESCE(SUM(t.quantity),0) FROM stock_transaction t WHERE t.warehouse_id=wi.warehouse_id
               AND t.product_id=wi.product_id AND t.type='OUT' AND t.created_at>=?
               AND t.created_at<=UTC_TIMESTAMP(6)) AS exported_today,
              CASE WHEN i.quantity=(SELECT COALESCE(SUM(x.quantity),0) FROM warehouse_inventory x WHERE x.product_id=wi.product_id)
                   THEN 1 ELSE 0 END AS reconciled
            FROM warehouse_inventory wi JOIN inventory i ON i.product_id=wi.product_id
            LEFT JOIN demand_policy dp ON dp.warehouse_id=wi.warehouse_id AND dp.product_id=wi.product_id
            """, java.sql.Timestamp.valueOf(LocalDate.now(ZONE).atStartOfDay().minusHours(7)));
    }
}
