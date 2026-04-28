package org.sainm.schemapilot.datasource;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDataSourceConfigRepository implements DataSourceConfigRepository {
    private final ConcurrentHashMap<UUID, DataSourceConfig> configs = new ConcurrentHashMap<>();

    @Override
    public DataSourceConfig save(DataSourceConfig config) {
        configs.put(config.id(), config);
        return config;
    }

    @Override
    public List<DataSourceConfig> findAll() {
        return configs.values().stream()
                .sorted(Comparator.comparing(DataSourceConfig::createdAt))
                .toList();
    }

    @Override
    public Optional<DataSourceConfig> findById(UUID id) {
        return Optional.ofNullable(configs.get(id));
    }
}
