package org.sainm.schemapilot.oracle;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.model.ObjectType;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class OracleSchemaScanServiceTest {
    @Test
    void scansOracleSchemaObjectsColumnsConstraintsIndexesSourceAndRisks() throws Exception {
        var service = new OracleSchemaScanService(new FakeExtractor());
        var job = service.start(new OracleScanRequest(UUID.randomUUID(), "legacy_app"));

        var completed = awaitCompleted(service, job.id());

        assertThat(completed.status()).isEqualTo(OracleScanStatus.COMPLETED);
        assertThat(completed.progressPercent()).isEqualTo(100);
        assertThat(completed.snapshot().schemas()).contains("LEGACY_APP");
        assertThat(completed.snapshot().objects())
                .extracting(OracleObjectMetadata::type)
                .contains(ObjectType.TABLE, ObjectType.VIEW, ObjectType.TRIGGER, ObjectType.FUNCTION, ObjectType.PROCEDURE, ObjectType.PACKAGE, ObjectType.SYNONYM, ObjectType.SEQUENCE);
        assertThat(completed.snapshot().columns()).extracting(OracleColumnMetadata::columnName).contains("ID", "EMAIL");
        assertThat(completed.snapshot().constraints()).extracting(OracleConstraintMetadata::type).contains("P", "R", "U", "C");
        assertThat(completed.snapshot().indexes()).extracting(OracleIndexMetadata::name).contains("IDX_USERS_EMAIL");
        assertThat(completed.snapshot().objects()).anyMatch(object -> "USERS".equals(object.name()) && object.ddl().contains("CREATE TABLE"));
        assertThat(completed.snapshot().objects()).anyMatch(object -> "TRG_USERS_BI".equals(object.name()) && object.source().contains(":NEW"));
        assertThat(completed.snapshot().risks()).contains("DBMS_METADATA_PERMISSION_MISSING");
    }

    private OracleScanJob awaitCompleted(OracleSchemaScanService service, UUID jobId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            var current = service.get(jobId);
            if (current.status() == OracleScanStatus.COMPLETED || current.status() == OracleScanStatus.FAILED) {
                return current;
            }
            Thread.sleep(20);
        }
        return service.get(jobId);
    }

    private static class FakeExtractor implements OracleMetadataExtractor {
        @Override
        public OracleScanSnapshot extract(UUID dataSourceId, String schemaName, Consumer<OracleScanProgress> progressConsumer) {
            progressConsumer.accept(new OracleScanProgress(10, "SCHEMA", "schemas"));
            progressConsumer.accept(new OracleScanProgress(30, "OBJECT", "objects"));
            progressConsumer.accept(new OracleScanProgress(70, "SOURCE", "source"));
            return new OracleScanSnapshot(
                    schemaName,
                    List.of("LEGACY_APP"),
                    List.of(
                            new OracleObjectMetadata(ObjectType.TABLE, "USERS", "VALID", "CREATE TABLE USERS (ID NUMBER)", null, "users table", "RANGE"),
                            new OracleObjectMetadata(ObjectType.VIEW, "ACTIVE_USERS", "VALID", "CREATE VIEW ACTIVE_USERS AS SELECT * FROM USERS", null, null, null),
                            new OracleObjectMetadata(ObjectType.SEQUENCE, "USER_SEQ", "VALID", "CREATE SEQUENCE USER_SEQ", null, null, null),
                            new OracleObjectMetadata(ObjectType.TRIGGER, "TRG_USERS_BI", "VALID", null, "BEGIN :NEW.ID := USER_SEQ.NEXTVAL; END;", null, null),
                            new OracleObjectMetadata(ObjectType.FUNCTION, "FN_USER", "VALID", null, "FUNCTION FN_USER RETURN NUMBER", null, null),
                            new OracleObjectMetadata(ObjectType.PROCEDURE, "SYNC_USERS", "VALID", null, "PROCEDURE SYNC_USERS", null, null),
                            new OracleObjectMetadata(ObjectType.PACKAGE, "PKG_USERS", "VALID", null, "PACKAGE PKG_USERS AS END;", null, null),
                            new OracleObjectMetadata(ObjectType.SYNONYM, "USR", "VALID", null, null, null, null)
                    ),
                    List.of(
                            new OracleColumnMetadata("USERS", "ID", "NUMBER", 19, 0, false, null, "pk"),
                            new OracleColumnMetadata("USERS", "EMAIL", "VARCHAR2", 200, null, true, null, "email")
                    ),
                    List.of(
                            new OracleConstraintMetadata("PK_USERS", "USERS", "P", List.of("ID"), null, List.of(), null),
                            new OracleConstraintMetadata("FK_USERS_ORG", "USERS", "R", List.of("ORG_ID"), "ORG", List.of("ID"), null),
                            new OracleConstraintMetadata("UK_USERS_EMAIL", "USERS", "U", List.of("EMAIL"), null, List.of(), null),
                            new OracleConstraintMetadata("CK_USERS_EMAIL", "USERS", "C", List.of("EMAIL"), null, List.of(), "EMAIL IS NOT NULL")
                    ),
                    List.of(new OracleIndexMetadata("IDX_USERS_EMAIL", "USERS", false, List.of("EMAIL"))),
                    List.of("DBMS_METADATA_PERMISSION_MISSING")
            );
        }
    }
}
