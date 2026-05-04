package org.sainm.schemapilot.app.report;

import org.sainm.schemapilot.report.InMemoryReportRepository;
import org.sainm.schemapilot.report.ReportRepository;
import org.sainm.schemapilot.report.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ReportModuleConfiguration {

    @Bean
    ReportRepository reportRepository() {
        return new InMemoryReportRepository();
    }

    @Bean
    ReportService reportService(ReportRepository reportRepository) {
        return new ReportService(reportRepository);
    }
}
