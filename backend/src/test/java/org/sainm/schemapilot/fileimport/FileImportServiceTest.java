package org.sainm.schemapilot.fileimport;

import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.sainm.schemapilot.sql.OracleToPostgresConverter;
import org.sainm.schemapilot.sql.RiskScoringService;
import org.sainm.schemapilot.sql.SqlObjectClassifier;
import org.sainm.schemapilot.sql.SqlRiskDetector;
import org.sainm.schemapilot.sql.SqlStatementSplitter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileImportServiceTest {
    private final FileImportService service = new FileImportService(new ManualSqlAnalysisService(
            new SqlStatementSplitter(),
            new SqlObjectClassifier(),
            new OracleToPostgresConverter(),
            new SqlRiskDetector(),
            new RiskScoringService()
    ));

    @Test
    void importsSqlFileWithChecksumEncodingProgressAndObjectList() throws Exception {
        var job = service.importSqlFile(
                "datapump.sql",
                """
                        CREATE TABLE users (id NUMBER, name VARCHAR2(100));
                        CREATE INDEX idx_users_name ON users(name);
                        """.getBytes(StandardCharsets.UTF_8),
                "UTF-8"
        );

        assertThat(job.fileName()).isEqualTo("datapump.sql");
        assertThat(job.checksumSha256()).hasSize(64);
        assertThat(job.encoding()).isEqualTo("UTF-8");

        var completed = awaitCompleted(job);
        assertThat(completed.status()).isEqualTo(FileImportStatus.COMPLETED);
        assertThat(completed.progressPercent()).isEqualTo(100);
        assertThat(completed.analysis().statementCount()).isEqualTo(2);
        assertThat(completed.analysis().statements())
                .extracting(statement -> statement.objectName())
                .contains("users", "idx_users_name");
        assertThat(service.sourceSql(completed.id())).contains("CREATE TABLE users");
    }

    @Test
    void importsAnonymousPlsqlRiskFromFile() throws Exception {
        var job = service.importSqlFile(
                "anonymous.sql",
                """
                        BEGIN
                          DBMS_OUTPUT.PUT_LINE('hello');
                        END;
                        /
                        """.getBytes(StandardCharsets.UTF_8),
                null
        );

        var completed = awaitCompleted(job);

        assertThat(completed.analysis().statements().getFirst().risks())
                .extracting(risk -> risk.type())
                .contains("ANONYMOUS_PLSQL");
    }

    @Test
    void ignoresUtf8BomAtBeginningOfSqlFile() throws Exception {
        var sql = "CREATE TABLE users (id NUMBER);";
        var sqlBytes = sql.getBytes(StandardCharsets.UTF_8);
        var bytes = new byte[sqlBytes.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(sqlBytes, 0, bytes, 3, sqlBytes.length);

        var completed = awaitCompleted(service.importSqlFile("bom.sql", bytes, "UTF-8"));

        assertThat(completed.analysis().statements().getFirst().objectName()).isEqualTo("users");
        assertThat(completed.analysis().statements().getFirst().parseIssues()).isEmpty();
    }

    @Test
    void combinesCompletedFileImportJobsIntoOneSourceBatch() throws Exception {
        var tableJob = awaitCompleted(service.importSqlFile(
                "tables.sql",
                "CREATE TABLE users (id NUMBER);".getBytes(StandardCharsets.UTF_8),
                null
        ));
        var viewJob = awaitCompleted(service.importSqlFile(
                "views.sql",
                "CREATE VIEW active_users AS SELECT id FROM users;".getBytes(StandardCharsets.UTF_8),
                null
        ));

        var combined = service.combinedSourceSql(List.of(tableJob.id(), viewJob.id()));

        assertThat(combined).contains("-- source file: tables.sql");
        assertThat(combined).contains("-- source file: views.sql");
        assertThat(combined).contains("CREATE TABLE users");
        assertThat(combined).contains("CREATE VIEW active_users");
        assertThat(service.sourceFileSummary(List.of(tableJob.id(), viewJob.id()))).isEqualTo("tables.sql, views.sql");
    }

    @Test
    void importsZipProjectPackageWithRelativeSqlPaths() throws Exception {
        var completed = awaitCompleted(service.importSqlFile(
                "oracle-project.zip",
                zip(
                        "schema/tables.sql", "CREATE TABLE users (id NUMBER);",
                        "schema/views/active_users.sql", "CREATE VIEW active_users AS SELECT id FROM users;",
                        "README.md", "ignored"
                ),
                "UTF-8"
        ));

        assertThat(completed.status()).isEqualTo(FileImportStatus.COMPLETED);
        assertThat(completed.analysis().statementCount()).isEqualTo(2);
        assertThat(service.sourceSql(completed.id())).contains("-- source file: schema/tables.sql");
        assertThat(service.sourceSql(completed.id())).contains("-- source file: schema/views/active_users.sql");
        assertThat(service.sourceSql(completed.id())).doesNotContain("ignored");
    }

    private byte[] zip(String firstName, String firstContent, String secondName, String secondContent, String thirdName, String thirdContent) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            addZipEntry(zip, firstName, firstContent);
            addZipEntry(zip, secondName, secondContent);
            addZipEntry(zip, thirdName, thirdContent);
        }
        return bytes.toByteArray();
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private FileImportJob awaitCompleted(FileImportJob job) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            var current = service.getJob(job.id());
            if (current.status() == FileImportStatus.COMPLETED || current.status() == FileImportStatus.FAILED) {
                return current;
            }
            Thread.sleep(20);
        }
        return service.getJob(job.id());
    }
}
