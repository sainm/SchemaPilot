package org.sainm.schemapilot.app.risk;

import org.sainm.schemapilot.risk.InMemoryRiskRepository;
import org.sainm.schemapilot.risk.RiskAssessmentService;
import org.sainm.schemapilot.risk.RiskRepository;
import org.sainm.schemapilot.risk.SqlRiskDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RiskModuleConfiguration {

    @Bean
    RiskRepository riskRepository() {
        return new InMemoryRiskRepository();
    }

    @Bean
    SqlRiskDetector sqlRiskDetector() {
        return new SqlRiskDetector();
    }

    @Bean
    RiskAssessmentService riskAssessmentService(SqlRiskDetector detector, RiskRepository riskRepository) {
        return new RiskAssessmentService(detector, riskRepository);
    }
}
