package org.sainm.schemapilot.convert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SqlVersionServiceTest {

    private final InMemorySqlVersionRepository repository = new InMemorySqlVersionRepository();
    private final SqlVersionService service = new SqlVersionService(repository);

    @Test
    void createsGeneratedVersionAndEditedChildVersion() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        ConversionResult conversion = new ConversionResult(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                objectId,
                "create table users (id number)",
                "create table \"users\" (\"id\" numeric)",
                ConversionLevel.REVIEW_REQUIRED,
                List.of(),
                List.of());

        SqlVersion generated = service.createGeneratedVersions(List.of(conversion)).getFirst();
        service.freezeProjectBaseline(projectId);
        assertThat(repository.findById(generated.id()).orElseThrow().status()).isEqualTo(SqlVersionStatus.BASELINE);

        SqlVersion edited = service.editVersion(projectId, generated.id(), "create table \"users\" (\"id\" numeric not null)");

        assertThat(edited.parentVersionId()).isEqualTo(generated.id());
        assertThat(edited.versionNumber()).isEqualTo(2);
        assertThat(repository.findVersions(projectId)).hasSize(2);

        service.freezeProjectBaseline(projectId);

        assertThat(repository.findById(edited.id()).orElseThrow().status()).isEqualTo(SqlVersionStatus.BASELINE);
        assertThat(repository.findById(generated.id()).orElseThrow().status()).isEqualTo(SqlVersionStatus.STALE);
    }
}
