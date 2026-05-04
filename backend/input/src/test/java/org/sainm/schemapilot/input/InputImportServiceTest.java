package org.sainm.schemapilot.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.project.CreateProjectCommand;
import org.sainm.schemapilot.project.InMemoryProjectRepository;
import org.sainm.schemapilot.project.ProjectService;
import org.sainm.schemapilot.project.ProjectSnapshot;

class InputImportServiceTest {

    private final InMemoryProjectRepository projectRepository = new InMemoryProjectRepository();
    private final ProjectService projectService = new ProjectService(projectRepository);
    private final InputImportService inputImportService = new InputImportService(projectRepository, new InMemoryInputRepository());

    @Test
    void importManualSqlCreatesBatchAndSourceWithHash() {
        ProjectSnapshot project = projectService.createProject(new CreateProjectCommand("Pilot", ""));

        InputImportResult result = inputImportService.importManualSql(new ManualSqlInputCommand(
                project.project().id(),
                project.sourceProjects().getFirst().id(),
                "table.sql",
                "create table users (id number(10,0));"));

        assertThat(result.batch().status()).isEqualTo(InputBatchStatus.IMPORTED);
        assertThat(result.source().type()).isEqualTo(InputSourceType.MANUAL_SQL);
        assertThat(result.source().contentHash()).hasSize(64);
        assertThat(result.source().originalText()).contains("create table users");
    }

    @Test
    void importManualSqlRejectsBlankSql() {
        ProjectSnapshot project = projectService.createProject(new CreateProjectCommand("Pilot", ""));

        assertThatThrownBy(() -> inputImportService.importManualSql(new ManualSqlInputCommand(
                project.project().id(),
                project.sourceProjects().getFirst().id(),
                "blank.sql",
                " ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SQL");
    }

    @Test
    void importFilesCreatesOneBatchWithMultipleSources() {
        ProjectSnapshot project = projectService.createProject(new CreateProjectCommand("Pilot", ""));

        InputBatchImportResult result = inputImportService.importFiles(new FileInputCommand(
                project.project().id(),
                project.sourceProjects().getFirst().id(),
                InputSourceType.FOLDER,
                List.of(
                        new InputFile("schema/users.sql", "create table users (id number);".getBytes(StandardCharsets.UTF_8)),
                        new InputFile("views/v_users.sql", "create view v_users as select * from users;".getBytes(StandardCharsets.UTF_8)))));

        assertThat(result.sources()).hasSize(2);
        assertThat(result.sources()).extracting(InputSource::relativePath)
                .containsExactly("schema/users.sql", "views/v_users.sql");
    }

    @Test
    void importZipRejectsIllegalPath() throws Exception {
        ProjectSnapshot project = projectService.createProject(new CreateProjectCommand("Pilot", ""));

        assertThatThrownBy(() -> inputImportService.importFiles(new FileInputCommand(
                project.project().id(),
                project.sourceProjects().getFirst().id(),
                InputSourceType.ZIP,
                List.of(new InputFile("bad.zip", zip("../evil.sql", "select 1;"))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Illegal file path");
    }

    @Test
    void importZipRejectsNonZipPayload() {
        ProjectSnapshot project = projectService.createProject(new CreateProjectCommand("Pilot", ""));

        assertThatThrownBy(() -> inputImportService.importFiles(new FileInputCommand(
                project.project().id(),
                project.sourceProjects().getFirst().id(),
                InputSourceType.ZIP,
                List.of(new InputFile("bad.zip", "not a zip".getBytes(StandardCharsets.UTF_8))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Zip contains no SQL files");
    }

    @Test
    void importZipRejectsEmptyArchive() throws Exception {
        ProjectSnapshot project = projectService.createProject(new CreateProjectCommand("Pilot", ""));

        assertThatThrownBy(() -> inputImportService.importFiles(new FileInputCommand(
                project.project().id(),
                project.sourceProjects().getFirst().id(),
                InputSourceType.ZIP,
                List.of(new InputFile("empty.zip", emptyZip())))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Zip contains no SQL files");
    }

    private byte[] zip(String path, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private byte[] emptyZip() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream ignored = new ZipOutputStream(bytes)) {
            return bytes.toByteArray();
        }
    }
}
