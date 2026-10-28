package com.example.iot.service;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReplenishmentService {
    private final DemandForecastService forecast;
    private final DemandDataService data;

    public static List<Map<String,Object>> calculate(List<Map<String,Object>> predictions,int stock,int safety,long exportedToday,LocalDate today) {
        List<Map<String,Object>> result=new ArrayList<>();
        double cumulative=0; long previous=0;
        for (int d=0; d<7; d++) {
            String target=today.plusDays(d).toString();
            var row=predictions.stream().filter(p -> target.equals(p.get("target_date"))).findFirst().orElse(null);
            if (row==null || !"OK".equals(row.get("status")) || !(row.get("predicted_quantity") instanceof Number)) return List.of();
            double expected=((Number)row.get("predicted_quantity")).doubleValue();
            if (!Double.isFinite(expected) || expected<0) return List.of();
            if (d==0) expected=Math.max(0,expected-exportedToday);
            cumulative+=expected;
            long needed=(long)Math.ceil(Math.max(0,cumulative+safety-stock));
            result.add(Map.of("target_date",target,"remaining_export",expected,"required_quantity",needed-previous,"cumulative_required",needed));
            previous=needed;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> latest() {
        var prediction=DemandForecastService.parse(forecast.latest());
        var all=(List<Map<String,Object>>)prediction.getOrDefault("forecasts",List.of());
        Map<String,Map<String,Object>> balances=new HashMap<>();
        for(var row:data.balances()) balances.put(row.get("warehouse_id")+":"+row.get("product_id"),row);
        List<Map<String,Object>> items=new ArrayList<>(),daily=new ArrayList<>(),weekly=new ArrayList<>();
        LocalDate today=LocalDate.now(DemandDataService.ZONE);
        boolean fresh=today.toString().equals(prediction.get("forecastDate")) && data.dataSource().equals(prediction.get("dataSource"));
        for(var pair:data.catalog()) {
            var balance=balances.get(pair.warehouseId()+":"+pair.productId());
            var item=new LinkedHashMap<String,Object>();
            item.put("warehouse_id",pair.warehouseId()); item.put("product_id",pair.productId());
            item.put("warehouseName",pair.warehouseName()); item.put("sku",pair.sku()); item.put("productName",pair.productName());
            boolean reconciled=balance!=null && ((Number)balance.get("reconciled")).intValue()==1;
            int stock=balance==null?0:((Number)balance.get("stock")).intValue();
            int safety=balance==null?0:((Number)balance.get("safety_stock")).intValue();
            var rows=all.stream().filter(r -> pair.warehouseId().equals(r.get("warehouse_id")) && pair.productId().equals(r.get("product_id"))).toList();
            var days=reconciled&&fresh?calculate(rows,stock,safety,((Number)balance.get("exported_today")).longValue(),today):List.<Map<String,Object>>of();
            item.put("stock",balance==null?null:stock); item.put("safetyStock",safety); item.put("days",days);
            item.put("status",!reconciled?"UNALLOCATED_STOCK":days.isEmpty()?"INSUFFICIENT_OR_STALE_DATA":"OK");
            if(!days.isEmpty()) {
                long todayQty=((Number)days.get(0).get("required_quantity")).longValue();
                long weekQty=((Number)days.get(6).get("cumulative_required")).longValue();
                item.put("requiredToday",todayQty); item.put("requiredWeek",weekQty);
                item.put("exportWeek",rows.stream().filter(r -> { LocalDate d=LocalDate.parse(r.get("target_date").toString()); return !d.isBefore(today)&&d.isBefore(today.plusDays(7)); }).mapToDouble(r -> ((Number)r.get("predicted_quantity")).doubleValue()).sum());
                item.put("firstRequiredDate",days.stream().filter(d -> ((Number)d.get("required_quantity")).longValue()>0).map(d -> d.get("target_date")).findFirst().orElse(null));
                if(todayQty>0) daily.add(item);
                if(weekQty>0) weekly.add(item);
            }
            items.add(item);
        }
        weekly.sort(Comparator.comparing(r -> r.get("firstRequiredDate").toString()));
        var result=new LinkedHashMap<String,Object>();
        result.put("runId",prediction.get("runId")); result.put("modelVersion",prediction.get("modelVersion"));
        result.put("dataSource",prediction.get("dataSource")); result.put("forecastDate",prediction.get("forecastDate"));
        result.put("stockAsOf",Instant.now().toString()); result.put("items",items);
        result.put("todayAlerts",daily); result.put("weekAlerts",weekly);
        result.put("forecast",prediction);
        return result;
    }
}
