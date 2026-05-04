package org.sainm.schemapilot.convert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;

public class SqlVersionService {

    private final SqlVersionRepository sqlVersionRepository;

    public SqlVersionService(SqlVersionRepository sqlVersionRepository) {
        this.sqlVersionRepository = sqlVersionRepository;
    }

    public List<SqlVersion> createGeneratedVersions(List<ConversionResult> conversions) {
        List<SqlVersion> versions = new ArrayList<>();
        Instant now = Instant.now();
        for (ConversionResult conversion : conversions) {
            versions.add(new SqlVersion(
                    UUID.randomUUID(),
                    conversion.projectId(),
                    conversion.sourceProjectId(),
                    conversion.inputSourceId(),
                    conversion.objectId(),
                    conversion.id(),
                    null,
                    1,
                    SqlVersionSource.AUTO_CONVERSION,
                    SqlVersionStatus.GENERATED,
                    conversion.sourceSql(),
                    conversion.targetSql(),
                    now));
        }
        if (!conversions.isEmpty()) {
            sqlVersionRepository.replaceInputSourceGeneratedVersions(conversions.getFirst().inputSourceId(), versions);
        }
        return List.copyOf(versions);
    }

    public SqlVersion editVersion(UUID projectId, UUID parentVersionId, String targetSql) {
        if (targetSql == null || targetSql.isBlank()) {
            throw new BadRequestException("Target SQL must not be blank");
        }
        SqlVersion parent = sqlVersionRepository.findById(parentVersionId)
                .filter(version -> version.projectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("SQL version not found"));
        SqlVersion edited = new SqlVersion(
                UUID.randomUUID(),
                parent.projectId(),
                parent.sourceProjectId(),
                parent.inputSourceId(),
                parent.objectId(),
                parent.conversionResultId(),
                parent.id(),
                sqlVersionRepository.nextVersionNumber(parent.objectId()),
                SqlVersionSource.USER_EDIT,
                SqlVersionStatus.EDITED,
                parent.sourceSql(),
                targetSql,
                Instant.now());
        sqlVersionRepository.save(edited);
        return edited;
    }

    public List<SqlVersion> freezeProjectBaseline(UUID projectId) {
        return sqlVersionRepository.markLatestVersionsBaseline(projectId);
    }
}
