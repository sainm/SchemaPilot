package org.sainm.schemapilot.convert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.model.DbColumn;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.DbObjectType;
import org.sainm.schemapilot.model.ObjectStatus;
import org.sainm.schemapilot.model.SourceLocation;
import org.sainm.schemapilot.rule.RuleHit;

class OracleObjectConverterTest {

    private final OracleObjectConverter converter = new OracleObjectConverter(new OracleTypeMapper());

    @Test
    void convertsTableColumnsWithoutNarrowingNumber() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "users",
                ObjectStatus.PARSED,
                new SourceLocation("users.sql", 1, 1, 0, 1),
                """
                        create table users (
                          id number(10,0),
                          name varchar2(20),
                          constraint users_pk primary key (id),
                          unique (name),
                          check (id > 0)
                        )""",
                List.of(
                        new DbColumn("id", "number(10,0)", null, false, 1),
                        new DbColumn("name", "varchar2(20)", null, true, 2)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.AUTO);
        assertThat(result.targetSql()).contains("\"id\" numeric(10,0) not null");
        assertThat(result.targetSql()).contains("\"name\" varchar(20)");
        assertThat(result.targetSql()).contains("constraint users_pk primary key (id)");
        assertThat(result.targetSql()).contains("unique (name)");
        assertThat(result.targetSql()).contains("check (id > 0)");
        assertThat(result.notes()).contains("Do not narrow NUMBER(p,0) to integer or bigint without value profile evidence.");
    }

    @Test
    void preservesInlineColumnDefaultsAndConstraints() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "accounts",
                ObjectStatus.PARSED,
                new SourceLocation("accounts.sql", 1, 1, 0, 1),
                """
                        create table accounts (
                          id number(8,0) primary key,
                          status varchar2(20) default 'ACTIVE' not null,
                          score number(8,0) check (score > 0)
                        )""",
                List.of(
                        new DbColumn("id", "number(8,0)", null, true, 1),
                        new DbColumn("status", "varchar2(20)", null, false, 2),
                        new DbColumn("score", "number(8,0)", null, true, 3)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) primary key");
        assertThat(result.targetSql()).contains("\"status\" varchar(20) default 'ACTIVE' not null");
        assertThat(result.targetSql()).contains("\"score\" numeric(8,0) check (score > 0)");
    }

    @Test
    void preservesOracleQQuotedColumnDefaultsWhenSplittingColumns() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "notes",
                ObjectStatus.PARSED,
                new SourceLocation("notes.sql", 1, 1, 0, 1),
                """
                        create table notes (
                          id number(8,0) primary key,
                          body varchar2(100) default q'[Bob's, note]' not null
                        )""",
                List.of(
                        new DbColumn("id", "number(8,0)", null, true, 1),
                        new DbColumn("body", "varchar2(100)", null, false, 2)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) primary key");
        assertThat(result.targetSql()).contains("\"body\" varchar(100) default q'[Bob's, note]' not null");
        assertThat(result.targetSql()).doesNotContain("\"note]'");
    }

    @Test
    void rewritesOracleInlineDefaultsAndRequiresReview() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "accounts",
                ObjectStatus.PARSED,
                new SourceLocation("accounts.sql", 1, 1, 0, 1),
                """
                        create table accounts (
                          id number(8,0) default accounts_seq.nextval primary key,
                          created_at timestamp default sysdate
                        )""",
                List.of(
                        new DbColumn("id", "number(8,0)", null, true, 1),
                        new DbColumn("created_at", "timestamp", null, true, 2)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.REVIEW_REQUIRED);
        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) default nextval('accounts_seq'::regclass) primary key");
        assertThat(result.targetSql()).contains("\"created_at\" timestamp default clock_timestamp()");
        assertThat(result.ruleHits()).extracting(RuleHit::ruleCode).contains("ORACLE_INLINE_COLUMN_CLAUSE_REVIEW");
    }

    @Test
    void doesNotRewriteOracleTokensInsideColumnSuffixLiterals() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "notes",
                ObjectStatus.PARSED,
                new SourceLocation("notes.sql", 1, 1, 0, 1),
                """
                        create table notes (
                          label varchar2(40) default 'sysdate' not null,
                          detail varchar2(100) default q'[users_seq.nextval sysdate]' not null
                        )""",
                List.of(
                        new DbColumn("label", "varchar2(40)", null, false, 1),
                        new DbColumn("detail", "varchar2(100)", null, false, 2)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("\"label\" varchar(40) default 'sysdate' not null");
        assertThat(result.targetSql()).contains("\"detail\" varchar(100) default q'[users_seq.nextval sysdate]' not null");
        assertThat(result.targetSql()).doesNotContain("clock_timestamp()");
        assertThat(result.targetSql()).doesNotContain("nextval('users_seq'::regclass)");
    }

    @Test
    void preservesQuotedSequenceNamesInNextvalDefaults() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "accounts",
                ObjectStatus.PARSED,
                new SourceLocation("accounts.sql", 1, 1, 0, 1),
                """
                        create table accounts (
                          id number(8,0) default "UserSeq".nextval primary key
                        )""",
                List.of(new DbColumn("id", "number(8,0)", null, true, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) default nextval('\"UserSeq\"'::regclass) primary key");
    }

    @Test
    void preservesTableClausesAfterLeadingCommentWithParentheses() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "users",
                ObjectStatus.PARSED,
                new SourceLocation("users.sql", 1, 1, 0, 1),
                """
                        /* export metadata (schema only); */
                        create table users (
                          id number(8,0) not null,
                          constraint users_pk primary key (id)
                        )""",
                List.of(new DbColumn("id", "number(8,0)", null, false, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) not null");
        assertThat(result.targetSql()).contains("constraint users_pk primary key (id)");
    }

    @Test
    void preservesColumnsWhenInlineCommentContainsParenthesesBeforeColumnList() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "users",
                ObjectStatus.PARSED,
                new SourceLocation("users.sql", 1, 1, 0, 1),
                """
                        create table users -- legacy name (do not parse)
                        (
                          id number(8,0) not null,
                          constraint users_pk primary key (id)
                        )""",
                List.of(new DbColumn("id", "number(8,0)", null, false, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) not null");
        assertThat(result.targetSql()).contains("constraint users_pk primary key (id)");
    }

    @Test
    void normalizesSimpleUppercaseOracleIdentifiersToLowercase() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                "HR",
                "USERS",
                ObjectStatus.PARSED,
                new SourceLocation("users.sql", 1, 1, 0, 1),
                "create table HR.USERS (ID number(8,0) not null)",
                List.of(new DbColumn("ID", "number(8,0)", null, false, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("create table \"hr\".\"users\"");
        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) not null");
    }

    @Test
    void preservesQuotedUppercaseOracleIdentifiers() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                "HR",
                "USERS",
                ObjectStatus.PARSED,
                new SourceLocation("users.sql", 1, 1, 0, 1),
                "create table \"HR\".\"USERS\" (\"ID\" number(8,0) not null)",
                List.of(new DbColumn("ID", "number(8,0)", null, false, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("create table \"HR\".\"USERS\"");
        assertThat(result.targetSql()).contains("\"ID\" numeric(8,0) not null");
        assertThat(result.targetSql()).doesNotContain("\"hr\".\"users\"");
        assertThat(result.targetSql()).doesNotContain("\"id\" numeric");
    }

    @Test
    void normalizesUnquotedMixedCaseOracleIdentifiersToLowercase() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "Users",
                ObjectStatus.PARSED,
                new SourceLocation("users.sql", 1, 1, 0, 1),
                "create table Users (Id number(8,0) not null)",
                List.of(new DbColumn("Id", "number(8,0)", null, false, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("create table \"users\"");
        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) not null");
        assertThat(result.targetSql()).doesNotContain("\"Users\"");
        assertThat(result.targetSql()).doesNotContain("\"Id\" numeric");
    }

    @Test
    void rewritesOracleOnlyTableConstraintClausesAndRequiresReview() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "accounts",
                ObjectStatus.PARSED,
                new SourceLocation("accounts.sql", 1, 1, 0, 1),
                """
                        create table accounts (
                          id number(8,0),
                          constraint accounts_pk primary key (id) using index enable
                        )""",
                List.of(new DbColumn("id", "number(8,0)", null, true, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.REVIEW_REQUIRED);
        assertThat(result.targetSql()).contains("constraint accounts_pk primary key (id)");
        assertThat(result.targetSql()).doesNotContain("using index");
        assertThat(result.targetSql()).doesNotContain("enable");
        assertThat(result.ruleHits()).extracting(RuleHit::ruleCode).contains("ORACLE_TABLE_CONSTRAINT_REVIEW");
    }

    @Test
    void convertsOracleTemporaryTablesAsReviewRequiredTemporaryTables() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "temp_orders",
                ObjectStatus.PARSED,
                new SourceLocation("temp_orders.sql", 1, 1, 0, 1),
                "create global temporary table temp_orders (id number(8,0)) on commit delete rows",
                List.of(new DbColumn("id", "number(8,0)", null, true, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.REVIEW_REQUIRED);
        assertThat(result.targetSql()).contains("create temporary table \"temp_orders\"");
        assertThat(result.ruleHits()).extracting(RuleHit::ruleCode).contains("ORACLE_TEMPORARY_TABLE_REVIEW");
    }

    @Test
    void emitsOracleTemporaryTableDefaultOnCommitDeleteRows() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "temp_orders",
                ObjectStatus.PARSED,
                new SourceLocation("temp_orders.sql", 1, 1, 0, 1),
                "create global temporary table temp_orders (id number(8,0))",
                List.of(new DbColumn("id", "number(8,0)", null, true, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.REVIEW_REQUIRED);
        assertThat(result.targetSql()).contains(") on commit delete rows;");
    }

    @Test
    void preservesEnableIdentifierInsideInlineColumnCheckConstraint() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "feature_flags",
                ObjectStatus.PARSED,
                new SourceLocation("feature_flags.sql", 1, 1, 0, 1),
                """
                        create table feature_flags (
                          enable number(8,0) check ( enable in (0, 1))
                        )""",
                List.of(new DbColumn("enable", "number(8,0)", null, true, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.targetSql()).contains("\"enable\" numeric(8,0) check ( enable in (0, 1))");
    }

    @Test
    void rewritesOracleOnlyInlineConstraintClausesAndRequiresReview() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "accounts",
                ObjectStatus.PARSED,
                new SourceLocation("accounts.sql", 1, 1, 0, 1),
                """
                        create table accounts (
                          id number(8,0) primary key using index enable novalidate
                        )""",
                List.of(new DbColumn("id", "number(8,0)", null, true, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.REVIEW_REQUIRED);
        assertThat(result.targetSql()).contains("\"id\" numeric(8,0) primary key");
        assertThat(result.targetSql()).doesNotContain("using index");
        assertThat(result.targetSql()).doesNotContain("novalidate");
        assertThat(result.ruleHits()).extracting(RuleHit::ruleCode).contains("ORACLE_INLINE_COLUMN_CLAUSE_REVIEW");
    }

    @Test
    void stripsOracleConstraintValidationClausesAndRequiresReview() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "accounts",
                ObjectStatus.PARSED,
                new SourceLocation("accounts.sql", 1, 1, 0, 1),
                """
                        create table accounts (
                          id number(8,0),
                          constraint accounts_id_chk check (id > 0) enable novalidate rely
                        )""",
                List.of(new DbColumn("id", "number(8,0)", null, true, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.REVIEW_REQUIRED);
        assertThat(result.targetSql()).contains("constraint accounts_id_chk check (id > 0)");
        assertThat(result.targetSql()).doesNotContain("enable");
        assertThat(result.targetSql()).doesNotContain("novalidate");
        assertThat(result.targetSql()).doesNotContain("rely");
        assertThat(result.ruleHits()).extracting(RuleHit::ruleCode).contains("ORACLE_TABLE_CONSTRAINT_REVIEW");
    }

    @Test
    void preservesEnableIdentifierInsideTableCheckConstraint() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject object = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.TABLE,
                null,
                "feature_flags",
                ObjectStatus.PARSED,
                new SourceLocation("feature_flags.sql", 1, 1, 0, 1),
                """
                        create table feature_flags (
                          enable number(8,0),
                          constraint feature_flags_enable_chk check ( enable in (0, 1))
                        )""",
                List.of(new DbColumn("enable", "number(8,0)", null, true, 1)));

        ConversionResult result = converter.convert(object, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.AUTO);
        assertThat(result.targetSql()).contains("constraint feature_flags_enable_chk check ( enable in (0, 1))");
    }

    @Test
    void createsDraftForCompatibleViewIndexAndSequenceObjects() {
        UUID projectId = UUID.randomUUID();
        UUID sourceProjectId = UUID.randomUUID();
        UUID inputSourceId = UUID.randomUUID();
        DbObject view = new DbObject(
                UUID.randomUUID(),
                projectId,
                sourceProjectId,
                inputSourceId,
                DbObjectType.VIEW,
                null,
                "v_users",
                ObjectStatus.PARSED,
                new SourceLocation("view.sql", 1, 1, 0, 1),
                "create view v_users as select id from users",
                List.of());

        ConversionResult result = converter.convert(view, new ConversionContext(projectId, sourceProjectId, inputSourceId));

        assertThat(result.level()).isEqualTo(ConversionLevel.DRAFT);
        assertThat(result.targetSql()).startsWith("-- DRAFT:");
        assertThat(result.targetSql()).contains("create view v_users");
        assertThat(result.ruleHits()).extracting("ruleCode").contains("CONSERVATIVE_VIEW_DRAFT");
    }
}
