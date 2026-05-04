package org.sainm.schemapilot.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.model.DbObjectType;
import org.sainm.schemapilot.model.InMemoryAssetRepository;

class AssetModelingServiceTest {

    private final InMemoryAssetRepository repository = new InMemoryAssetRepository();
    private final AssetModelingService service = new AssetModelingService(
            new SqlStatementSplitter(),
            new SqlObjectClassifier(),
            repository);

    @Test
    void modelsObjectsAndKeepsUnsupportedStatementsAsParseIssues() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();

        AssetModelingResult result = service.modelInputSource(new AssetModelingCommand(
                projectId,
                sourceProjectId,
                inputSourceId,
                "schema/table.sql",
                """
                        create table app_users (
                          id number(10,0) not null,
                          name varchar2(100),
                          created_at date default sysdate,
                          constraint app_users_pk primary key (id)
                        );

                        select * from dual;
                        """));

        assertThat(result.objects()).hasSize(1);
        assertThat(result.objects().getFirst().type()).isEqualTo(DbObjectType.TABLE);
        assertThat(result.objects().getFirst().columns())
                .extracting("name")
                .containsExactly("id", "name", "created_at");
        assertThat(result.parseIssues()).hasSize(1);
        assertThat(repository.findObjects(projectId)).hasSize(1);
        assertThat(repository.findParseIssues(projectId)).hasSize(1);
    }

    @Test
    void extractsTableColumnsAfterLeadingCommentWithParentheses() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();

        AssetModelingResult result = service.modelInputSource(new AssetModelingCommand(
                projectId,
                sourceProjectId,
                inputSourceId,
                "schema/users.sql",
                """
                        /* export metadata (schema only); */
                        create table users (
                          id number(8,0) not null
                        );
                        """));

        assertThat(result.parseIssues()).isEmpty();
        assertThat(result.objects()).singleElement()
                .satisfies(object -> assertThat(object.columns())
                        .extracting("name", "sourceType")
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("id", "number(8,0)")));
    }

    @Test
    void extractsTableColumnsWhenInlineCommentContainsParenthesesBeforeColumnList() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();

        AssetModelingResult result = service.modelInputSource(new AssetModelingCommand(
                projectId,
                sourceProjectId,
                inputSourceId,
                "schema/users.sql",
                """
                        create table users -- legacy name (do not parse)
                        (
                          id number(8,0) not null
                        );
                        """));

        assertThat(result.parseIssues()).isEmpty();
        assertThat(result.objects()).singleElement()
                .satisfies(object -> assertThat(object.columns())
                        .extracting("name", "sourceType")
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("id", "number(8,0)")));
    }

    @Test
    void extractsTableColumnsWhenQQuotedDefaultContainsApostropheAndComma() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();

        AssetModelingResult result = service.modelInputSource(new AssetModelingCommand(
                projectId,
                sourceProjectId,
                inputSourceId,
                "schema/notes.sql",
                """
                        create table notes (
                          id number(8,0) primary key,
                          body varchar2(100) default q'[Bob's, note]' not null
                        );
                        """));

        assertThat(result.parseIssues()).isEmpty();
        assertThat(result.objects()).singleElement()
                .satisfies(object -> assertThat(object.columns())
                        .extracting("name", "sourceType")
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple("id", "number(8,0)"),
                        org.assertj.core.groups.Tuple.tuple("body", "varchar2(100)")));
    }

    @Test
    void splitsInlineReferencesOutOfColumnTypes() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();

        AssetModelingResult result = service.modelInputSource(new AssetModelingCommand(
                projectId,
                sourceProjectId,
                inputSourceId,
                "schema/employees.sql",
                """
                        create table employees (
                          id number(10,0) primary key,
                          department_id number(10,0) references departments(id)
                        );
                        """));

        assertThat(result.parseIssues()).isEmpty();
        assertThat(result.objects()).singleElement()
                .satisfies(object -> assertThat(object.columns())
                        .extracting("name", "sourceType")
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple("id", "number(10,0)"),
                                org.assertj.core.groups.Tuple.tuple("department_id", "number(10,0)")));
    }

    @Test
    void splitsGeneratedIdentityOutOfColumnTypes() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();

        AssetModelingResult result = service.modelInputSource(new AssetModelingCommand(
                projectId,
                sourceProjectId,
                inputSourceId,
                "schema/users.sql",
                """
                        create table users (
                          id number generated by default as identity primary key,
                          name varchar2(100)
                        );
                        """));

        assertThat(result.parseIssues()).isEmpty();
        assertThat(result.objects()).singleElement()
                .satisfies(object -> assertThat(object.columns())
                        .extracting("name", "sourceType")
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple("id", "number"),
                                org.assertj.core.groups.Tuple.tuple("name", "varchar2(100)")));
    }

    @Test
    void ignoresCommentOnlyInput() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();

        AssetModelingResult result = service.modelInputSource(new AssetModelingCommand(
                projectId,
                sourceProjectId,
                inputSourceId,
                "schema/comments.sql",
                """
                        -- generated by export tool;
                        /* schema only */
                        """));

        assertThat(result.objects()).isEmpty();
        assertThat(result.parseIssues()).isEmpty();
    }
}
