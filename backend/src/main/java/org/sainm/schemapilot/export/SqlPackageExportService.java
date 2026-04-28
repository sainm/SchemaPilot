package org.sainm.schemapilot.export;

import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.sql.AnalyzedStatement;
import org.sainm.schemapilot.workbench.SavedSqlVersion;
import org.sainm.schemapilot.workbench.WorkbenchService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SqlPackageExportService {
    private final WorkbenchService workbenchService;

    public SqlPackageExportService(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    public SqlPackageResponse export(UUID snapshotId) {
        var snapshot = workbenchService.getSnapshot(snapshotId);
        var baselineVersions = workbenchService.baselineVersions(snapshotId);
        var content = baselineVersions.stream()
                .sorted(Comparator.comparingInt(version -> orderWeight(objectType(snapshot, version.statementIndex()))))
                .map(version -> sectionHeader(snapshot, version) + "\n" + version.sql().strip() + "\n")
                .collect(Collectors.joining("\n"));

        var fileName = "schemapilot-" + snapshotId + ".sql";
        workbenchService.recordExport(snapshotId, fileName);

        return new SqlPackageResponse(
                snapshotId,
                fileName,
                Instant.now(),
                baselineVersions.stream().map(SavedSqlVersion::id).toList(),
                content
        );
    }

    private String sectionHeader(org.sainm.schemapilot.workbench.WorkbenchSnapshot snapshot, SavedSqlVersion version) {
        var statement = statement(snapshot, version.statementIndex());
        return "-- " + statement.objectType() + " " + statement.objectName() + " / " + version.id();
    }

    private ObjectType objectType(org.sainm.schemapilot.workbench.WorkbenchSnapshot snapshot, int statementIndex) {
        return statement(snapshot, statementIndex).objectType();
    }

    private AnalyzedStatement statement(org.sainm.schemapilot.workbench.WorkbenchSnapshot snapshot, int statementIndex) {
        return snapshot.analysis().statements().stream()
                .filter(item -> item.index() == statementIndex)
                .findFirst()
                .orElseThrow();
    }

    private int orderWeight(ObjectType type) {
        return switch (type) {
            case SEQUENCE -> 1;
            case TABLE -> 2;
            case INDEX -> 3;
            case VIEW -> 4;
            case FUNCTION, PROCEDURE, TRIGGER -> 5;
            case PACKAGE, PACKAGE_BODY -> 9;
            default -> 8;
        };
    }
}
