package org.sainm.schemapilot.app.input;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.input.InputImportResult;
import org.sainm.schemapilot.input.FileInputCommand;
import org.sainm.schemapilot.input.InputBatchImportResult;
import org.sainm.schemapilot.input.InputFile;
import org.sainm.schemapilot.input.InputSource;
import org.sainm.schemapilot.input.InputSourceType;
import org.sainm.schemapilot.input.InputImportService;
import org.sainm.schemapilot.input.ManualSqlInputCommand;
import org.sainm.schemapilot.parser.AssetModelingResult;
import org.sainm.schemapilot.parser.AssetModelingCommand;
import org.sainm.schemapilot.parser.AssetModelingService;
import org.sainm.schemapilot.convert.ConversionService;
import org.sainm.schemapilot.convert.ConversionResult;
import org.sainm.schemapilot.convert.SqlVersionService;
import org.sainm.schemapilot.dependency.DependencyGraphService;
import org.sainm.schemapilot.risk.RiskAssessmentService;
import org.sainm.schemapilot.model.AssetRepository;
import org.sainm.schemapilot.report.ReportRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/inputs")
class InputController {

    private final InputImportService inputImportService;
    private final AssetModelingService assetModelingService;
    private final RiskAssessmentService riskAssessmentService;
    private final ConversionService conversionService;
    private final SqlVersionService sqlVersionService;
    private final DependencyGraphService dependencyGraphService;
    private final AssetRepository assetRepository;
    private final ReportRepository reportRepository;

    InputController(
            InputImportService inputImportService,
            AssetModelingService assetModelingService,
            RiskAssessmentService riskAssessmentService,
            ConversionService conversionService,
            SqlVersionService sqlVersionService,
            DependencyGraphService dependencyGraphService,
            AssetRepository assetRepository,
            ReportRepository reportRepository) {
        this.inputImportService = inputImportService;
        this.assetModelingService = assetModelingService;
        this.riskAssessmentService = riskAssessmentService;
        this.conversionService = conversionService;
        this.sqlVersionService = sqlVersionService;
        this.dependencyGraphService = dependencyGraphService;
        this.assetRepository = assetRepository;
        this.reportRepository = reportRepository;
    }

    @PostMapping("/manual-sql")
    ApiResponse<InputImportResult> importManualSql(
            @PathVariable UUID projectId,
            @RequestBody ManualSqlInputRequest request) {
        InputImportResult result = inputImportService.importManualSql(new ManualSqlInputCommand(
                projectId,
                request.sourceProjectId(),
                request.name(),
                request.sql()));
        processImportedSources(projectId, List.of(result.source()));
        return ApiResponse.success(result);
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<InputBatchImportResult> importFiles(
            @PathVariable UUID projectId,
            @RequestParam UUID sourceProjectId,
            @RequestParam("files") List<MultipartFile> files) {
        InputBatchImportResult result = inputImportService.importFiles(new FileInputCommand(
                projectId,
                sourceProjectId,
                files.size() == 1 ? InputSourceType.SQL_FILE : InputSourceType.FOLDER,
                toInputFiles(files)));
        processImportedSources(projectId, result.sources());
        return ApiResponse.success(result);
    }

    @PostMapping(value = "/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<InputBatchImportResult> importZip(
            @PathVariable UUID projectId,
            @RequestParam UUID sourceProjectId,
            @RequestParam("file") MultipartFile file) {
        InputBatchImportResult result = inputImportService.importFiles(new FileInputCommand(
                projectId,
                sourceProjectId,
                InputSourceType.ZIP,
                toInputFiles(List.of(file))));
        processImportedSources(projectId, result.sources());
        return ApiResponse.success(result);
    }

    private void processImportedSources(UUID projectId, List<InputSource> sources) {
        List<ConversionResult> conversions = new ArrayList<>();
        for (InputSource source : sources) {
            AssetModelingResult assetResult = assetModelingService.modelInputSource(new AssetModelingCommand(
                    projectId,
                    source.sourceProjectId(),
                    source.id(),
                    source.relativePath(),
                    source.originalText()));
            riskAssessmentService.assessObjects(assetResult.objects());
            conversions.addAll(conversionService.convertObjects(assetResult.objects()));
        }
        sqlVersionService.createGeneratedVersions(conversions);
        dependencyGraphService.rebuildProjectDependencies(projectId, assetRepository.findObjects(projectId));
        reportRepository.markProjectReportsStale(projectId);
    }

    private List<InputFile> toInputFiles(List<MultipartFile> files) {
        return files.stream()
                .map(this::toInputFile)
                .toList();
    }

    private InputFile toInputFile(MultipartFile file) {
        try {
            return new InputFile(
                    file.getOriginalFilename() == null ? file.getName() : file.getOriginalFilename(),
                    file.getBytes());
        } catch (IOException exception) {
            throw new BadRequestException("Failed to read uploaded file");
        }
    }
}
