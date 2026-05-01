package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ConversionLevel;
import org.sainm.schemapilot.model.ObjectType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ManualSqlAnalysisServiceTest {
    private final ManualSqlAnalysisService service = new ManualSqlAnalysisService(
            new SqlStatementSplitter(),
            new SqlObjectClassifier(),
            new OracleToPostgresConverter(),
            new SqlRiskDetector(),
            new RiskScoringService()
    );

    @Test
    void convertsCreateTableAndDetectsRisks() {
        var response = service.analyze("""
                CREATE TABLE users (
                  id NUMBER(19) PRIMARY KEY,
                  score NUMBER,
                  name VARCHAR2(100),
                  created_at DATE DEFAULT SYSDATE,
                  bio CLOB
                );
                """);

        assertThat(response.statementCount()).isEqualTo(1);
        var statement = response.statements().getFirst();
        assertThat(statement.objectType()).isEqualTo(ObjectType.TABLE);
        assertThat(statement.objectName()).isEqualTo("users");
        assertThat(statement.conversionLevel()).isEqualTo(ConversionLevel.AUTO);
        assertThat(statement.riskLevel()).isEqualTo(org.sainm.schemapilot.model.RiskLevel.MEDIUM);
        assertThat(response.compatibilityScore()).isLessThan(100);
        assertThat(statement.postgresSql())
                .contains("id numeric(19)")
                .contains("score numeric")
                .contains("name varchar(100)")
                .contains("created_at timestamp DEFAULT CURRENT_TIMESTAMP")
                .contains("bio text");
        assertThat(statement.risks())
                .extracting(DetectedRisk::type)
                .contains("NUMBER_PRECISION", "DATE_SEMANTICS", "CURRENT_TIME");
    }

    @Test
    void keepsTriggerBlockAsSingleDraftStatement() {
        var response = service.analyze("""
                CREATE OR REPLACE TRIGGER trg_users_bi
                BEFORE INSERT ON users
                FOR EACH ROW
                BEGIN
                  :NEW.created_at := SYSDATE;
                END;
                /
                """);

        assertThat(response.statementCount()).isEqualTo(1);
        var statement = response.statements().getFirst();
        assertThat(statement.objectType()).isEqualTo(ObjectType.TRIGGER);
        assertThat(statement.conversionLevel()).isEqualTo(ConversionLevel.DRAFT);
        assertThat(statement.postgresSql())
                .contains("RETURNS trigger")
                .contains("CREATE TRIGGER trg_users_bi")
                .contains("Oracle :NEW maps to NEW");
        assertThat(statement.risks())
                .extracting(DetectedRisk::type)
                .contains("TRIGGER_BODY", "CURRENT_TIME");
    }

    @Test
    void detectsPackageAsManualRequired() {
        var response = service.analyze("""
                CREATE OR REPLACE PACKAGE pkg_demo AS
                  g_counter NUMBER := 0;
                  PROCEDURE run_it;
                END pkg_demo;
                /
                """);

        assertThat(response.statementCount()).isEqualTo(1);
        var statement = response.statements().getFirst();
        assertThat(statement.objectType()).isEqualTo(ObjectType.PACKAGE);
        assertThat(statement.conversionLevel()).isEqualTo(ConversionLevel.MANUAL_REQUIRED);
        assertThat(statement.postgresSql())
                .contains("Package routines discovered")
                .contains("procedure run_it")
                .contains("Package global state candidates")
                .contains("g_counter");
        assertThat(statement.risks())
                .extracting(DetectedRisk::type)
                .contains("PACKAGE", "PACKAGE_ROUTINE_DECOMPOSITION", "PACKAGE_GLOBAL_STATE");
    }

    @Test
    void detectsOracleSpecificSqlRisks() {
        var response = service.analyze("""
                CREATE OR REPLACE PROCEDURE "SyncUsers" AS
                BEGIN
                  EXECUTE IMMEDIATE 'update users set name = NVL(name, '''') where code = ''''';
                  PRAGMA AUTONOMOUS_TRANSACTION;
                END;
                /
                """);

        assertThat(response.statementCount()).isEqualTo(1);
        var statement = response.statements().getFirst();
        assertThat(statement.risks())
                .extracting(DetectedRisk::type)
                .contains(
                        "NVL",
                        "EMPTY_STRING_NULL",
                        "QUOTED_IDENTIFIER",
                        "DYNAMIC_SQL",
                        "DYNAMIC_SQL_BINDING",
                        "PLSQL_ROUTINE_DRAFT",
                        "AUTONOMOUS_TRANSACTION"
                );
    }

    @Test
    void enhancesPlsqlRoutineAndBuiltinPackageSuggestions() {
        var response = service.analyze("""
                CREATE OR REPLACE FUNCTION next_token(p_seed IN NUMBER)
                RETURN VARCHAR2
                AS
                  v_token VARCHAR2(100);
                BEGIN
                  DBMS_OUTPUT.PUT_LINE('seed=' || p_seed);
                  v_token := DBMS_RANDOM.STRING('x', 12);
                  EXECUTE IMMEDIATE 'select token from tokens where id = :1' INTO v_token USING p_seed;
                  RETURN v_token;
                EXCEPTION
                  WHEN NO_DATA_FOUND THEN
                    RETURN NULL;
                END;
                /
                """);

        var statement = response.statements().getFirst();

        assertThat(statement.objectType()).isEqualTo(ObjectType.FUNCTION);
        assertThat(statement.postgresSql())
                .contains("CREATE OR REPLACE function next_token")
                .contains("LANGUAGE plpgsql")
                .contains("EXECUTE format")
                .contains("EXCEPTION");
        assertThat(statement.risks())
                .extracting(DetectedRisk::type)
                .contains(
                        "ORACLE_BUILTIN_PACKAGE",
                        "DYNAMIC_SQL",
                        "DYNAMIC_SQL_BINDING",
                        "EXCEPTION_SEMANTICS",
                        "PLSQL_ROUTINE_DRAFT"
                );
    }

    @Test
    void analyzesPackageBodyRoutinesAndReplacementHints() {
        var response = service.analyze("""
                CREATE OR REPLACE PACKAGE BODY pkg_file AS
                  PROCEDURE write_it(p_name IN VARCHAR2) IS
                  BEGIN
                    UTL_FILE.PUT_LINE(NULL, p_name);
                    DBMS_LOB.CREATETEMPORARY(NULL, TRUE);
                  END;
                  FUNCTION calc_it RETURN NUMBER IS
                  BEGIN
                    RETURN 1;
                  END;
                END pkg_file;
                /
                """);

        var statement = response.statements().getFirst();

        assertThat(statement.objectType()).isEqualTo(ObjectType.PACKAGE_BODY);
        assertThat(statement.postgresSql())
                .contains("procedure write_it")
                .contains("function calc_it")
                .contains("UTL_FILE -> external file service")
                .contains("DBMS_LOB -> PostgreSQL text/bytea functions");
        assertThat(statement.risks())
                .extracting(DetectedRisk::type)
                .contains("PACKAGE", "PACKAGE_ROUTINE_DECOMPOSITION", "ORACLE_BUILTIN_PACKAGE");
    }

    @Test
    void convertsCommonSchemaObjects() {
        var response = service.analyze(readFixture("fixtures/manual-sql/schema_objects.sql"));

        assertThat(response.statementCount()).isEqualTo(4);
        assertThat(response.statements().get(0).postgresSql())
                .contains("id bigint PRIMARY KEY")
                .contains("email varchar(200) UNIQUE")
                .contains("balance numeric(12,2) CHECK (balance >= 0)");
        assertThat(response.statements().get(1).objectType()).isEqualTo(ObjectType.INDEX);
        assertThat(response.statements().get(1).postgresSql())
                .contains("CREATE INDEX idx_accounts_email ON accounts(email)");
        assertThat(response.statements().get(2).objectType()).isEqualTo(ObjectType.SEQUENCE);
        assertThat(response.statements().get(2).postgresSql())
                .contains("CREATE SEQUENCE account_seq START WITH 1 INCREMENT BY 1");
        assertThat(response.statements().get(3).objectType()).isEqualTo(ObjectType.VIEW);
        assertThat(response.statements().get(3).postgresSql())
                .contains("CREATE VIEW active_accounts AS")
                .contains("COALESCE(email, 'unknown')");
        assertThat(response.statements().get(3).risks())
                .extracting(DetectedRisk::type)
                .contains("NVL", "ROWNUM");
    }

    @Test
    void fixtureKeepsTriggerFunctionAndPackageBlocksTogether() {
        var response = service.analyze(readFixture("fixtures/manual-sql/plsql_risks.sql"));

        assertThat(response.statementCount()).isEqualTo(3);
        assertThat(response.statements())
                .extracting(AnalyzedStatement::objectType)
                .containsExactly(ObjectType.TRIGGER, ObjectType.FUNCTION, ObjectType.PACKAGE);
        assertThat(response.statements().get(1).postgresSql()).contains("PL/pgSQL review skeleton");
        assertThat(response.statements().get(2).risks())
                .extracting(DetectedRisk::type)
                .contains("PACKAGE", "PACKAGE_GLOBAL_STATE", "PACKAGE_ROUTINE_DECOMPOSITION");
    }

    @Test
    void preservesUnrecognizedSqlAsParseIssue() {
        var response = service.analyze("ALTER SESSION SET current_schema = legacy_app;");

        var statement = response.statements().getFirst();
        assertThat(statement.objectType()).isEqualTo(ObjectType.UNKNOWN);
        assertThat(statement.originalSql()).contains("ALTER SESSION");
        assertThat(statement.parseIssues())
                .extracting(ParseIssue::type)
                .contains("UNRECOGNIZED_STATEMENT");
    }

    @Test
    void flagsAnonymousPlsqlBlock() {
        var response = service.analyze("""
                BEGIN
                  DBMS_OUTPUT.PUT_LINE('hello');
                END;
                /
                """);

        var statement = response.statements().getFirst();
        assertThat(statement.objectType()).isEqualTo(ObjectType.UNKNOWN);
        assertThat(statement.originalSql()).contains("DBMS_OUTPUT");
        assertThat(statement.parseIssues())
                .extracting(ParseIssue::type)
                .contains("ANONYMOUS_PLSQL");
        assertThat(statement.risks())
                .extracting(DetectedRisk::type)
                .contains("ANONYMOUS_PLSQL");
    }

    private String readFixture(String path) {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing fixture: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read fixture: " + path, ex);
        }
    }
}
