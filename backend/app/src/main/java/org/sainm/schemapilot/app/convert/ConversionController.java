package org.sainm.schemapilot.app.convert;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.ApiResponse;
import org.sainm.schemapilot.convert.ConversionRepository;
import org.sainm.schemapilot.convert.ConversionResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/conversions")
class ConversionController {

    private final ConversionRepository conversionRepository;

    ConversionController(ConversionRepository conversionRepository) {
        this.conversionRepository = conversionRepository;
    }

    @GetMapping
    ApiResponse<List<ConversionResult>> listConversions(@PathVariable UUID projectId) {
        return ApiResponse.success(conversionRepository.findConversions(projectId));
    }
}
