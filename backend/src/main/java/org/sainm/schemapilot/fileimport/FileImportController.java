package org.sainm.schemapilot.fileimport;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/file-import")
public class FileImportController {
    private final FileImportService importService;

    public FileImportController(FileImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/sql")
    public ApiResponse<FileImportJob> uploadSql(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "relativePath", required = false) String relativePath,
            @RequestParam(value = "encoding", required = false) String encoding
    ) throws IOException {
        var sourceName = relativePath == null || relativePath.isBlank() ? file.getOriginalFilename() : relativePath;
        return ApiResponse.ok(importService.importSqlFile(sourceName, file.getBytes(), encoding));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<FileImportJob> getJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(importService.getJob(jobId));
    }

    @GetMapping("/jobs/{jobId}/events")
    public SseEmitter jobEvents(@PathVariable UUID jobId) {
        return importService.subscribe(jobId);
    }
}
