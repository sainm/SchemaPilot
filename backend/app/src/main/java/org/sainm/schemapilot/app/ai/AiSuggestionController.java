package org.sainm.schemapilot.app.ai;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.ai.AiContext;
import org.sainm.schemapilot.ai.AiContextBuilder;
import org.sainm.schemapilot.ai.AiSuggestion;
import org.sainm.schemapilot.ai.AiSuggestionRepository;
import org.sainm.schemapilot.ai.AiSuggestionService;
import org.sainm.schemapilot.ai.AiSuggestionType;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.convert.ConversionRepository;
import org.sainm.schemapilot.dependency.DependencyRepository;
import org.sainm.schemapilot.model.AssetRepository;
import org.sainm.schemapilot.risk.RiskRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai")
class AiSuggestionController {

    private final AiContextBuilder aiContextBuilder;
    private final AiSuggestionService aiSuggestionService;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final AssetRepository assetRepository;
    private final DependencyRepository dependencyRepository;
    private final RiskRepository riskRepository;
    private final ConversionRepository conversionRepository;

    AiSuggestionController(
            AiContextBuilder aiContextBuilder,
            AiSuggestionService aiSuggestionService,
            AiSuggestionRepository aiSuggestionRepository,
            AssetRepository assetRepository,
            DependencyRepository dependencyRepository,
            RiskRepository riskRepository,
            ConversionRepository conversionRepository) {
        this.aiContextBuilder = aiContextBuilder;
        this.aiSuggestionService = aiSuggestionService;
        this.aiSuggestionRepository = aiSuggestionRepository;
        this.assetRepository = assetRepository;
        this.dependencyRepository = dependencyRepository;
        this.riskRepository = riskRepository;
        this.conversionRepository = conversionRepository;
    }

    @GetMapping("/suggestions")
    ApiResponse<List<AiSuggestion>> listSuggestions(@PathVariable UUID projectId) {
        return ApiResponse.success(aiSuggestionRepository.findSuggestions(projectId));
    }

    @PostMapping("/suggestions/{type}")
    ApiResponse<AiSuggestion> createSuggestion(@PathVariable UUID projectId, @PathVariable AiSuggestionType type) {
        AiContext context = aiContextBuilder.build(
                projectId,
                assetRepository.findObjects(projectId),
                dependencyRepository.findDependencies(projectId),
                riskRepository.findRisks(projectId),
                conversionRepository.findConversions(projectId));
        return ApiResponse.success(aiSuggestionService.createSuggestion(type, context));
    }
}
