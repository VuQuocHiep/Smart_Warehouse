package com.example.iot.controller;

import com.example.iot.service.CloudDemandBatchService;
import com.example.iot.service.DemandForecastService;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/demand")
public class CloudDemandController {
    private final CloudDemandBatchService batch;
    public CloudDemandController(CloudDemandBatchService batch) { this.batch=batch; }

    @PostMapping("/claim")
    public Map<String,Object> claim(@RequestHeader(value="X-Batch-Token",defaultValue="") String token,
            @RequestHeader("X-Runner-Id") String runner) {
        batch.authorize(token); return batch.claim(runner);
    }
    @GetMapping("/jobs/{id}/model")
    public ResponseEntity<byte[]> model(@PathVariable UUID id,
            @RequestHeader(value="X-Batch-Token",defaultValue="") String token,
            @RequestHeader(value="X-Claim-Token",defaultValue="") String lease) {
        batch.authorize(token);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Cache-Control","no-store").body(batch.artifact(id.toString(),lease));
    }
    @PostMapping(value="/jobs/{id}/complete",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> complete(@PathVariable UUID id,
            @RequestHeader(value="X-Batch-Token",defaultValue="") String token,
            @RequestHeader(value="X-Claim-Token",defaultValue="") String lease,
            @RequestParam("result") String result,@RequestParam("metadata") String metadata,
            @RequestPart(value="model",required=false) MultipartFile model) throws IOException {
        batch.authorize(token);
        return batch.complete(id.toString(),lease,DemandForecastService.parse(result),
                DemandForecastService.parse(metadata),model==null?null:model.getBytes());
    }
    @PostMapping("/jobs/{id}/fail")
    public Map<String,Object> fail(@PathVariable UUID id,
            @RequestHeader(value="X-Batch-Token",defaultValue="") String token,
            @RequestHeader(value="X-Claim-Token",defaultValue="") String lease) {
        batch.authorize(token); return batch.fail(id.toString(),lease);
    }
}
