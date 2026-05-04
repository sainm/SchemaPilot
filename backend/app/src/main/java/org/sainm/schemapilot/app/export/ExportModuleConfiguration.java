package org.sainm.schemapilot.app.export;

import org.sainm.schemapilot.convert.SqlVersionRepository;
import org.sainm.schemapilot.dependency.DependencyRepository;
import org.sainm.schemapilot.export.SqlPackageExportService;
import org.sainm.schemapilot.report.ReportRepository;
import org.sainm.schemapilot.review.ReviewRepository;
import org.sainm.schemapilot.risk.RiskRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ExportModuleConfiguration {

    @Bean
    SqlPackageExportService sqlPackageExportService(
            SqlVersionRepository sqlVersionRepository,
            ReviewRepository reviewRepository,
            RiskRepository riskRepository,
            ReportRepository reportRepository,
            DependencyRepository dependencyRepository) {
        return new SqlPackageExportService(sqlVersionRepository, reviewRepository, riskRepository, reportRepository, dependencyRepository);
    }
}
