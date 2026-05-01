package org.sainm.schemapilot.workbench;

import org.sainm.schemapilot.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.sainm.schemapilot.fileimport.FileImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {
    private final WorkbenchService service;
    private final FileImportService fileImportService;

    public WorkbenchController(WorkbenchService service, FileImportService fileImportService) {
        this.service = service;
        this.fileImportService = fileImportService;
    }

    @PostMapping("/manual-sql")
    public ApiResponse<WorkbenchSnapshot> saveManualSql(@Valid @RequestBody SaveManualSqlRequest request) {
        return ApiResponse.ok(service.saveManualSql(request.sql()));
    }

    @PostMapping("/file-import-jobs/{jobId}")
    public ApiResponse<WorkbenchSnapshot> saveFileImportJob(@PathVariable UUID jobId) {
        var job = fileImportService.getJob(jobId);
        return ApiResponse.ok(service.saveImportedSql(fileImportService.sourceSql(jobId), job.fileName()));
    }

    @PostMapping("/file-import-jobs")
    public ApiResponse<WorkbenchSnapshot> saveFileImportJobs(@Valid @RequestBody SaveFileImportJobsRequest request) {
        return ApiResponse.ok(service.saveImportedSql(
                fileImportService.combinedSourceSql(request.jobIds()),
                fileImportService.sourceFileSummary(request.jobIds())
        ));
    }

    @GetMapping("/snapshots/{snapshotId}")
    public ApiResponse<WorkbenchSnapshot> getSnapshot(@PathVariable UUID snapshotId) {
        return ApiResponse.ok(service.getSnapshot(snapshotId));
    }

    @PostMapping("/snapshots/{snapshotId}/source-sql")
    public ApiResponse<WorkbenchSnapshot> replaceSourceSql(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody SaveManualSqlRequest request
    ) {
        return ApiResponse.ok(service.replaceSourceSql(snapshotId, request.sql()));
    }

    @PostMapping("/ai-suggestions")
    public ApiResponse<WorkbenchSnapshot> saveAiSuggestion(@Valid @RequestBody SaveAiSuggestionRequest request) {
        return ApiResponse.ok(service.saveAiSuggestion(request));
    }

    @PostMapping("/ai-suggestions/{suggestionId}/accept")
    public ApiResponse<WorkbenchSnapshot> acceptAiSuggestion(@PathVariable UUID suggestionId) {
        return ApiResponse.ok(service.acceptAiSuggestion(suggestionId));
    }

    @PostMapping("/ai-suggestions/{suggestionId}/ignore")
    public ApiResponse<WorkbenchSnapshot> ignoreAiSuggestion(@PathVariable UUID suggestionId) {
        return ApiResponse.ok(service.ignoreAiSuggestion(suggestionId));
    }

    @PostMapping("/ai-suggestions/{suggestionId}/apply-edited")
    public ApiResponse<WorkbenchSnapshot> applyEditedAiSuggestion(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody ApplyEditedAiSuggestionRequest request
    ) {
        return ApiResponse.ok(service.applyEditedAiSuggestion(suggestionId, request.targetSql()));
    }

    @PostMapping("/snapshots/{snapshotId}/target-sql")
    public ApiResponse<WorkbenchSnapshot> editTargetSql(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody EditSqlVersionRequest request
    ) {
        return ApiResponse.ok(service.editTargetSql(snapshotId, request));
    }

    @PostMapping("/snapshots/{snapshotId}/statements/{statementIndex}/restore-generated")
    public ApiResponse<WorkbenchSnapshot> restoreGeneratedVersion(
            @PathVariable UUID snapshotId,
            @PathVariable int statementIndex
    ) {
        return ApiResponse.ok(service.restoreGeneratedVersion(snapshotId, statementIndex));
    }

    @PostMapping("/snapshots/{snapshotId}/review/submit")
    public ApiResponse<WorkbenchSnapshot> submitReview(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ApiResponse.ok(service.submitReview(snapshotId, request));
    }

    @PostMapping("/snapshots/{snapshotId}/review/approve")
    public ApiResponse<WorkbenchSnapshot> approveReview(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ApiResponse.ok(service.approveReview(snapshotId, request));
    }

    @PostMapping("/snapshots/{snapshotId}/review/conditional-approve")
    public ApiResponse<WorkbenchSnapshot> conditionallyApproveReview(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ApiResponse.ok(service.conditionallyApproveReview(snapshotId, request));
    }

    @PostMapping("/snapshots/{snapshotId}/review/reject")
    public ApiResponse<WorkbenchSnapshot> rejectReview(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ApiResponse.ok(service.rejectReview(snapshotId, request));
    }

    @PostMapping("/snapshots/{snapshotId}/review/request-changes")
    public ApiResponse<WorkbenchSnapshot> requestChanges(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ApiResponse.ok(service.requestChanges(snapshotId, request));
    }
}
