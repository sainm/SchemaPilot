package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ObjectType;
import org.sainm.schemapilot.model.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SqlRiskDetector {
    private static final Pattern NUMBER_WITHOUT_PRECISION = Pattern.compile("(?i)\\bNUMBER\\b(?!\\s*\\()");
    private static final Pattern EMPTY_STRING = Pattern.compile("(?is)(=|<>|!=)\\s*''|''\\s*(=|<>|!=)");
    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("\"[^\"]+\"");
    private static final Pattern PACKAGE_GLOBAL = Pattern.compile(
            "(?im)^\\s*(?!procedure\\b|function\\b|type\\b|subtype\\b|cursor\\b)([a-z][\\w$#]*)\\s+(constant\\s+)?(number|varchar2|nvarchar2|date|boolean|clob|blob|raw)\\b"
    );
    private final PlsqlRewriteAdvisor plsqlRewriteAdvisor = new PlsqlRewriteAdvisor();

    List<DetectedRisk> detect(ClassifiedStatement statement, String sql) {
        var risks = new ArrayList<DetectedRisk>();

        if (NUMBER_WITHOUT_PRECISION.matcher(sql).find()) {
            risks.add(new DetectedRisk(
                    "NUMBER_PRECISION",
                    RiskLevel.MEDIUM,
                    "Oracle NUMBER without precision was mapped to numeric and needs confirmation.",
                    "Confirm integer, bigint, or numeric precision based on source data profile."
            ));
        }
        if (contains(sql, "\\bDATE\\b")) {
            risks.add(new DetectedRisk(
                    "DATE_SEMANTICS",
                    RiskLevel.MEDIUM,
                    "Oracle DATE includes time information, unlike a pure date-only business meaning.",
                    "Use PostgreSQL timestamp by default, then verify business semantics."
            ));
        }
        if (contains(sql, "\\bSYSDATE\\b|\\bSYSTIMESTAMP\\b")) {
            risks.add(new DetectedRisk(
                    "CURRENT_TIME",
                    RiskLevel.LOW,
                    "Oracle current time function was detected.",
                    "Use CURRENT_TIMESTAMP and confirm timezone behavior."
            ));
        }
        if (contains(sql, "\\bROWNUM\\b")) {
            risks.add(new DetectedRisk("ROWNUM", RiskLevel.HIGH, "ROWNUM pagination or filtering needs PostgreSQL rewrite.", "Use LIMIT/OFFSET or window functions."));
        }
        if (contains(sql, "\\bCONNECT\\s+BY\\b")) {
            risks.add(new DetectedRisk("CONNECT_BY", RiskLevel.HIGH, "Oracle hierarchical query needs recursive CTE rewrite.", "Rewrite with WITH RECURSIVE."));
        }
        if (contains(sql, "\\bDECODE\\s*\\(")) {
            risks.add(new DetectedRisk("DECODE", RiskLevel.MEDIUM, "Oracle DECODE function needs CASE expression rewrite.", "Rewrite to CASE WHEN expressions."));
        }
        if (contains(sql, "\\bNVL\\s*\\(")) {
            risks.add(new DetectedRisk("NVL", RiskLevel.LOW, "Oracle NVL was rewritten to COALESCE, but evaluation and type resolution can differ.", "Confirm result types and side effects before approval."));
        }
        if (EMPTY_STRING.matcher(sql).find()) {
            risks.add(new DetectedRisk(
                    "EMPTY_STRING_NULL",
                    RiskLevel.HIGH,
                    "Oracle treats empty string as NULL, while PostgreSQL keeps empty string distinct.",
                    "Review predicates and defaults that compare against ''."
            ));
        }
        if (QUOTED_IDENTIFIER.matcher(sql).find()) {
            risks.add(new DetectedRisk(
                    "QUOTED_IDENTIFIER",
                    RiskLevel.MEDIUM,
                    "Quoted identifiers preserve case and can make PostgreSQL object references fragile.",
                    "Prefer normalized lowercase identifiers unless the application requires exact case."
            ));
        }
        if (contains(sql, "/\\*\\+")) {
            risks.add(new DetectedRisk("ORACLE_HINT", RiskLevel.MEDIUM, "Oracle optimizer hint was detected.", "Remove or replace with PostgreSQL tuning strategy."));
        }
        if (contains(sql, "\\bEXECUTE\\s+IMMEDIATE\\b")) {
            risks.add(new DetectedRisk("DYNAMIC_SQL", RiskLevel.HIGH, "Oracle dynamic SQL cannot be safely converted by static rules alone.", "Extract generated SQL patterns and review bind variable behavior manually."));
            risks.add(new DetectedRisk("DYNAMIC_SQL_BINDING", RiskLevel.HIGH, "Oracle EXECUTE IMMEDIATE bind semantics can differ from PL/pgSQL EXECUTE.", "Rewrite with EXECUTE format(...) USING ... and verify identifier/value quoting."));
        }
        if (contains(sql, "\\bPRAGMA\\s+AUTONOMOUS_TRANSACTION\\b")) {
            risks.add(new DetectedRisk(
                    "AUTONOMOUS_TRANSACTION",
                    RiskLevel.BLOCKER,
                    "Oracle autonomous transactions have no direct PostgreSQL transaction equivalent inside a function.",
                    "Redesign transaction boundaries or isolate the side effect outside the routine."
            ));
        }
        if (statement.objectType() == ObjectType.TRIGGER) {
            risks.add(new DetectedRisk("TRIGGER_BODY", RiskLevel.HIGH, "Oracle trigger requires PostgreSQL trigger function conversion.", "Review :NEW/:OLD usage and generated draft."));
        }
        if (statement.objectType() == ObjectType.UNKNOWN && contains(sql, "^\\s*(DECLARE|BEGIN)\\b")) {
            risks.add(new DetectedRisk(
                    "ANONYMOUS_PLSQL",
                    RiskLevel.HIGH,
                    "Anonymous Oracle PL/SQL block cannot be executed directly as a PostgreSQL schema object.",
                    "Convert it into a reviewed migration step or an explicit PostgreSQL function/procedure draft."
                ));
        }
        var builtinSuggestions = plsqlRewriteAdvisor.builtinPackageSuggestions(sql);
        if (!builtinSuggestions.isEmpty()) {
            risks.add(new DetectedRisk(
                    "ORACLE_BUILTIN_PACKAGE",
                    RiskLevel.HIGH,
                    "Oracle built-in package usage was detected: " + String.join("; ", builtinSuggestions),
                    "Replace each package call with a reviewed PostgreSQL, extension, or external-service equivalent."
            ));
        }
        if (contains(sql, "\\bEXCEPTION\\b|\\bNO_DATA_FOUND\\b|\\bTOO_MANY_ROWS\\b|\\bSQLCODE\\b|\\bSQLERRM\\b|\\bWHEN\\s+OTHERS\\b")) {
            risks.add(new DetectedRisk(
                    "EXCEPTION_SEMANTICS",
                    RiskLevel.MEDIUM,
                    "Oracle exception names, SQLCODE/SQLERRM behavior, and WHEN OTHERS handling need PostgreSQL review.",
                    "Map exceptions to PL/pgSQL condition names and decide whether to rethrow, translate, or log."
            ));
        }
        if (statement.objectType() == ObjectType.FUNCTION || statement.objectType() == ObjectType.PROCEDURE) {
            risks.add(new DetectedRisk(
                    "PLSQL_ROUTINE_DRAFT",
                    RiskLevel.HIGH,
                    "Oracle routine was converted to a PL/pgSQL skeleton and requires semantic review.",
                    "Review parameters, return type, DML behavior, dynamic SQL, exceptions, and transaction assumptions."
            ));
        }
        if (statement.objectType() == ObjectType.PACKAGE || statement.objectType() == ObjectType.PACKAGE_BODY) {
            risks.add(new DetectedRisk("PACKAGE", RiskLevel.BLOCKER, "Oracle package cannot be automatically migrated as a single PostgreSQL object.", "Split package routines and review global state."));
            var routines = plsqlRewriteAdvisor.packageRoutines(sql);
            if (!routines.isEmpty()) {
                risks.add(new DetectedRisk(
                        "PACKAGE_ROUTINE_DECOMPOSITION",
                        RiskLevel.HIGH,
                        "Oracle package routines must be decomposed: " + String.join(", ", routines),
                        "Convert stateless routines to standalone PostgreSQL functions/procedures and handle shared state explicitly."
                ));
            }
            if (PACKAGE_GLOBAL.matcher(sql).find()) {
                risks.add(new DetectedRisk(
                        "PACKAGE_GLOBAL_STATE",
                        RiskLevel.HIGH,
                        "Oracle package-level variables can hold session state that PostgreSQL routines do not model directly.",
                        "Move state into tables, settings, or explicit parameters before migration."
                ));
            }
        }

        return List.copyOf(risks);
    }

    private boolean contains(String sql, String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql).find();
    }
}
