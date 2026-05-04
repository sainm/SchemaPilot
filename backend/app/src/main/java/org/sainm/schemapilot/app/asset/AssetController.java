package org.sainm.schemapilot.app.asset;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.model.AssetRepository;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.ParseIssue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/assets")
class AssetController {

    private final AssetRepository assetRepository;

    AssetController(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @GetMapping("/objects")
    ApiResponse<List<DbObject>> listObjects(@PathVariable UUID projectId) {
        return ApiResponse.success(assetRepository.findObjects(projectId));
    }

    @GetMapping("/parse-issues")
    ApiResponse<List<ParseIssue>> listParseIssues(@PathVariable UUID projectId) {
        return ApiResponse.success(assetRepository.findParseIssues(projectId));
    }
}
