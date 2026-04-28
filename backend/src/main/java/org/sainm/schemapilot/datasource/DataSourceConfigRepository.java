package org.sainm.schemapilot.datasource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DataSourceConfigRepository {
    DataSourceConfig save(DataSourceConfig config);

    List<DataSourceConfig> findAll();

    Optional<DataSourceConfig> findById(UUID id);
}
