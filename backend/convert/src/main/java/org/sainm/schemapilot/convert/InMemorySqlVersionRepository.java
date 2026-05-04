package org.sainm.schemapilot.convert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class InMemorySqlVersionRepository implements SqlVersionRepository {

    private final ConcurrentMap<UUID, SqlVersion> versions = new ConcurrentHashMap<>();

    @Override
    public void replaceInputSourceGeneratedVersions(UUID inputSourceId, List<SqlVersion> newVersions) {
        versions.entrySet().removeIf(entry -> entry.getValue().inputSourceId().equals(inputSourceId)
                && entry.getValue().source() == SqlVersionSource.AUTO_CONVERSION);
        newVersions.forEach(version -> versions.put(version.id(), version));
    }

    @Override
    public void save(SqlVersion version) {
        versions.put(version.id(), version);
    }

    @Override
    public Optional<SqlVersion> findById(UUID id) {
        return Optional.ofNullable(versions.get(id));
    }

    @Override
    public List<SqlVersion> findVersions(UUID projectId) {
        return versions.values().stream()
                .filter(version -> version.projectId().equals(projectId))
                .sorted(Comparator.comparing((SqlVersion version) -> version.objectId().toString())
                        .thenComparing(SqlVersion::versionNumber))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public int nextVersionNumber(UUID objectId) {
        return versions.values().stream()
                .filter(version -> version.objectId().equals(objectId))
                .mapToInt(SqlVersion::versionNumber)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public List<SqlVersion> markLatestVersionsBaseline(UUID projectId) {
        List<SqlVersion> latestVersions = versions.values().stream()
                .filter(version -> version.projectId().equals(projectId))
                .collect(Collectors.groupingBy(SqlVersion::objectId))
                .values()
                .stream()
                .map(group -> group.stream()
                        .max(Comparator.comparingInt(SqlVersion::versionNumber))
                        .orElseThrow())
                .map(this::toBaseline)
                .toList();
        Set<UUID> latestVersionIds = latestVersions.stream()
                .map(SqlVersion::id)
                .collect(Collectors.toSet());
        versions.values().stream()
                .filter(version -> version.projectId().equals(projectId))
                .filter(version -> version.status() == SqlVersionStatus.BASELINE)
                .filter(version -> !latestVersionIds.contains(version.id()))
                .map(version -> withStatus(version, SqlVersionStatus.STALE))
                .forEach(version -> versions.put(version.id(), version));
        latestVersions.forEach(version -> versions.put(version.id(), version));
        return latestVersions;
    }

    private SqlVersion toBaseline(SqlVersion version) {
        return withStatus(version, SqlVersionStatus.BASELINE);
    }

    private SqlVersion withStatus(SqlVersion version, SqlVersionStatus status) {
        return new SqlVersion(
                version.id(),
                version.projectId(),
                version.sourceProjectId(),
                version.inputSourceId(),
                version.objectId(),
                version.conversionResultId(),
                version.parentVersionId(),
                version.versionNumber(),
                version.source(),
                status,
                version.sourceSql(),
                version.targetSql(),
                version.createdAt());
    }
}
