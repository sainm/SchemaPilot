package org.sainm.schemapilot.app.convert;

import org.sainm.schemapilot.convert.ConversionRepository;
import org.sainm.schemapilot.convert.ConversionService;
import org.sainm.schemapilot.convert.InMemoryConversionRepository;
import org.sainm.schemapilot.convert.InMemorySqlVersionRepository;
import org.sainm.schemapilot.convert.ObjectConverter;
import org.sainm.schemapilot.convert.OracleObjectConverter;
import org.sainm.schemapilot.convert.OracleTypeMapper;
import org.sainm.schemapilot.convert.SqlVersionRepository;
import org.sainm.schemapilot.convert.SqlVersionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConversionModuleConfiguration {

    @Bean
    ConversionRepository conversionRepository() {
        return new InMemoryConversionRepository();
    }

    @Bean
    OracleTypeMapper oracleTypeMapper() {
        return new OracleTypeMapper();
    }

    @Bean
    ObjectConverter objectConverter(OracleTypeMapper typeMapper) {
        return new OracleObjectConverter(typeMapper);
    }

    @Bean
    ConversionService conversionService(ObjectConverter objectConverter, ConversionRepository conversionRepository) {
        return new ConversionService(objectConverter, conversionRepository);
    }

    @Bean
    SqlVersionRepository sqlVersionRepository() {
        return new InMemorySqlVersionRepository();
    }

    @Bean
    SqlVersionService sqlVersionService(SqlVersionRepository sqlVersionRepository) {
        return new SqlVersionService(sqlVersionRepository);
    }
}
