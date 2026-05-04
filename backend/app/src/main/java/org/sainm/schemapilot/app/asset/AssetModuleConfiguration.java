package org.sainm.schemapilot.app.asset;

import org.sainm.schemapilot.model.AssetRepository;
import org.sainm.schemapilot.model.InMemoryAssetRepository;
import org.sainm.schemapilot.parser.AssetModelingService;
import org.sainm.schemapilot.parser.SqlObjectClassifier;
import org.sainm.schemapilot.parser.SqlStatementSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AssetModuleConfiguration {

    @Bean
    AssetRepository assetRepository() {
        return new InMemoryAssetRepository();
    }

    @Bean
    SqlStatementSplitter sqlStatementSplitter() {
        return new SqlStatementSplitter();
    }

    @Bean
    SqlObjectClassifier sqlObjectClassifier() {
        return new SqlObjectClassifier();
    }

    @Bean
    AssetModelingService assetModelingService(
            SqlStatementSplitter splitter,
            SqlObjectClassifier classifier,
            AssetRepository assetRepository) {
        return new AssetModelingService(splitter, classifier, assetRepository);
    }
}
