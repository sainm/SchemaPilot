package org.sainm.schemapilot.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlRiskDetectorTest {

    private final SqlRiskDetector detector = new SqlRiskDetector();

    @Test
    void detectsNumberAndSysdateSemanticRisks() {
        assertThat(detector.detect("""
                create table "Users" (
                  id number(10,0),
                  name varchar2(20) default '',
                  created_at date default sysdate
                );
                create or replace trigger users_bi
                before insert on users
                begin
                  :new.created_at := sysdate;
                end;
                /"""))
                .extracting(RiskIssue::code)
                .contains(
                        "NUMBER_INTEGER_NARROWING_REVIEW",
                        "SYSDATE_TIME_SEMANTICS",
                        "ORACLE_DATE_TIME_SEMANTICS",
                        "ORACLE_EMPTY_STRING_AS_NULL",
                        "QUOTED_IDENTIFIER_CASE_SENSITIVE");
    }

    @Test
    void detectsOracleSpecificSqlAndPlsqlRisks() {
        assertThat(detector.detect("""
                select /*+ index(t ix_t) */ decode(status, null, 'N', status)
                from tree t
                where rownum < 10
                connect by prior id = parent_id;

                create or replace package demo_pkg as
                  g_count number;
                  procedure run;
                end;
                /

                begin
                  execute immediate 'delete from t';
                end;
                /

                pragma autonomous_transaction;
                """))
                .extracting(RiskIssue::code)
                .contains(
                        "ROWNUM_REWRITE_REQUIRED",
                        "CONNECT_BY_REWRITE_REQUIRED",
                        "DECODE_REWRITE_REQUIRED",
                        "ORACLE_HINT_IGNORED",
                        "DYNAMIC_SQL_REVIEW_REQUIRED",
                        "AUTONOMOUS_TRANSACTION_UNSUPPORTED",
                        "PACKAGE_GLOBAL_STATE_REVIEW_REQUIRED",
                        "NUMBER_WITHOUT_PRECISION");
    }

    @Test
    void detectsPackageGlobalsInEditionablePackageSpecs() {
        assertThat(detector.detect("""
                create or replace editionable package demo_pkg as
                  g_count number;
                end;
                /
                """))
                .extracting(RiskIssue::code)
                .contains("PACKAGE_GLOBAL_STATE_REVIEW_REQUIRED");
    }

    @Test
    void ignoresRiskTokensInsideCommentsAndStringLiterals() {
        assertThat(detector.detect("""
                -- sysdate number date rownum connect by execute immediate pragma autonomous_transaction
                create table notes (
                  label varchar2(40) default 'sysdate number date rownum'
                );
                """))
                .extracting(RiskIssue::code)
                .doesNotContain(
                        "SYSDATE_TIME_SEMANTICS",
                        "NUMBER_WITHOUT_PRECISION",
                        "ORACLE_DATE_TIME_SEMANTICS",
                        "ROWNUM_REWRITE_REQUIRED",
                        "CONNECT_BY_REWRITE_REQUIRED",
                        "DYNAMIC_SQL_REVIEW_REQUIRED",
                        "AUTONOMOUS_TRANSACTION_UNSUPPORTED");
    }

    @Test
    void ignoresRiskTokensInsideOracleQQuotedLiterals() {
        assertThat(detector.detect("""
                create table notes (
                  label varchar2(100) default q'[Bob's sysdate number date rownum connect by]'
                );
                """))
                .extracting(RiskIssue::code)
                .doesNotContain(
                        "SYSDATE_TIME_SEMANTICS",
                        "NUMBER_WITHOUT_PRECISION",
                        "ORACLE_DATE_TIME_SEMANTICS",
                        "ROWNUM_REWRITE_REQUIRED",
                        "CONNECT_BY_REWRITE_REQUIRED");
    }

    @Test
    void ignoresRiskTokensEmbeddedInsideIdentifiers() {
        assertThat(detector.detect("""
                create table metrics (
                  sysdate_col varchar2(40),
                  rownum_value varchar2(40),
                  decode_status varchar2(40),
                  nvl_flag varchar2(40)
                );
                """))
                .extracting(RiskIssue::code)
                .doesNotContain(
                        "SYSDATE_TIME_SEMANTICS",
                        "ROWNUM_REWRITE_REQUIRED",
                        "DECODE_REWRITE_REQUIRED",
                        "NVL_REWRITE_REQUIRED");
    }

    @Test
    void ignoresQuotedIdentifierMarkersInsideStringLiterals() {
        assertThat(detector.detect("""
                create table notes (
                  label varchar2(40) default '"not an identifier"'
                );
                """))
                .extracting(RiskIssue::code)
                .doesNotContain("QUOTED_IDENTIFIER_CASE_SENSITIVE");
    }

    @Test
    void doesNotTreatEscapedApostrophesAsEmptyStrings() {
        assertThat(detector.detect("""
                create table authors (
                  display_name varchar2(80) default 'O''Reilly'
                );
                """))
                .extracting(RiskIssue::code)
                .doesNotContain("ORACLE_EMPTY_STRING_AS_NULL");
    }
}
