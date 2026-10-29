package com.example.iot.service;

import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DemandForecastService {
    private CloudDemandBatchService batch;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void configureBatch(CloudDemandBatchService batch) { this.batch=batch; }
    private boolean batchMode() { return batch!=null && batch.enabled(); }
    private final DemandDataService data;
    private final RestClient client;
    private final boolean enabled;
    private final String token;
    public DemandForecastService(DemandDataService data,
            @Value("${demand.service.url:http://127.0.0.1:8091}") String url,
            @Value("${demand.service.token:}") String token,
            @Value("${demand.service.enabled:false}") boolean enabled) {
        this.data=data; this.token=token; this.enabled=enabled;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public boolean enabled() { return batchMode() || (enabled && !token.isBlank()); }
    public List<DemandDataService.Pair> catalog() { return data.catalog(); }
    public String latest() { return batchMode()?batch.latest():exchange("/forecasts/latest",null); }
    public String job(UUID id) { return batchMode()?batch.job(id):exchange("/jobs/"+id,null); }
    public Map<String,Object> health() { return parse(exchange("/health",null)); }
    public Map<String,Object> submit(String id,String action,DemandDataService.Snapshot snapshot) {
        return parse(exchange("/jobs",Map.of("jobId",id,"action",action,"snapshot",snapshot)));
    }
    public static Map<String,Object> parse(String value) { return JsonParserFactory.getJsonParser().parseMap(value); }
    private String exchange(String path,Object body) {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Dịch vụ dự báo chưa bật hoặc chưa cấu hình token.");
        try {
            var request=body==null?client.get().uri(path):client.post().uri(path).body(body);
            return request.header("X-Service-Token",token).retrieve().body(String.class);
        } catch (RestClientResponseException ex) {
            int code=ex.getStatusCode().value();
            throw new ResponseStatusException(code==409?HttpStatus.CONFLICT:code==404?HttpStatus.NOT_FOUND:HttpStatus.BAD_GATEWAY,
                    "Dịch vụ dự báo trả lỗi ("+code+"). Kiểm tra trạng thái job và log ML.");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không kết nối được dịch vụ dự báo.");
        }
    }
}
