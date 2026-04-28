package org.sainm.schemapilot.datamove;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/data-moves")
public class DataMoveController {
    private final DataMoveService service;

    public DataMoveController(DataMoveService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<DataMoveJob> start(@Valid @RequestBody DataMoveRequest request) {
        return ApiResponse.ok(service.start(request));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<DataMoveJob> get(@PathVariable UUID jobId) {
        return ApiResponse.ok(service.get(jobId));
    }

    @PostMapping("/{jobId}/retry")
    public ApiResponse<DataMoveJob> retry(@PathVariable UUID jobId) {
        return ApiResponse.ok(service.retry(jobId));
    }

    @GetMapping("/{jobId}/events")
    public SseEmitter events(@PathVariable UUID jobId) {
        return service.subscribe(jobId);
    }
}
