package org.sainm.schemapilot.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sainm.schemapilot.common.sql.OracleSqlText;

public class SqlRiskDetector {

    private static final Pattern NUMBER_INTEGER_LIKE = Pattern.compile("number\\s*\\(\\s*(10|19)\\s*,\\s*0\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_WITHOUT_PRECISION = Pattern.compile("\\bnumber\\b(?!\\s*\\()", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORACLE_DATE = Pattern.compile("\\bdate\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SYSDATE = Pattern.compile("\\bsysdate\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROWNUM = Pattern.compile("\\brownum\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONNECT_BY = Pattern.compile("\\bconnect\\s+by\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECODE_CALL = Pattern.compile("\\bdecode\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern NVL_CALL = Pattern.compile("\\bnvl\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXECUTE_IMMEDIATE = Pattern.compile("\\bexecute\\s+immediate\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTONOMOUS_TRANSACTION = Pattern.compile("\\bpragma\\s+autonomous_transaction\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("\"([^\"]|\"\")+\"");
    private static final Pattern ORACLE_HINT = Pattern.compile("/\\*\\+.*?\\*/", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PACKAGE_GLOBAL = Pattern.compile(
            "^\\s*[a-zA-Z_][\\w$#]*\\s+(?:constant\\s+)?(?:number|varchar2|date|timestamp|boolean|clob|blob)\\b.*;",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile(
            "\\bcreate\\s+(?:or\\s+replace\\s+)?(?:editionable\\s+|noneditionable\\s+)?package\\b",
            Pattern.CASE_INSENSITIVE);

    public List<RiskIssue> detect(String sql) {
        List<RiskIssue> risks = new ArrayList<>();
        String searchableSql = stripStringLiteralsAndComments(sql);
        Matcher numberMatcher = NUMBER_INTEGER_LIKE.matcher(searchableSql);
        if (numberMatcher.find()) {
            risks.add(new RiskIssue(
                    "NUMBER_INTEGER_NARROWING_REVIEW",
                    RiskLevel.HIGH,
                    "NUMBER(10,0) and NUMBER(19,0) must not be narrowed to integer or bigint without value profile evidence.",
                    numberMatcher.group()));
        }
        if (SYSDATE.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "SYSDATE_TIME_SEMANTICS",
                    RiskLevel.HIGH,
                    "SYSDATE needs explicit review: PostgreSQL transaction time, statement time, and real current time are different choices.",
                    "SYSDATE"));
        }
        if (NUMBER_WITHOUT_PRECISION.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "NUMBER_WITHOUT_PRECISION",
                    RiskLevel.MEDIUM,
                    "NUMBER without precision should map to numeric and remain review-required.",
                    "NUMBER"));
        }
        if (ORACLE_DATE.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "ORACLE_DATE_TIME_SEMANTICS",
                    RiskLevel.MEDIUM,
                    "Oracle DATE stores date and time; PostgreSQL target type needs explicit semantic review.",
                    "DATE"));
        }
        if (containsEmptyStringLiteral(sql)) {
            risks.add(new RiskIssue(
                    "ORACLE_EMPTY_STRING_AS_NULL",
                    RiskLevel.HIGH,
                    "Oracle treats empty string as NULL, while PostgreSQL distinguishes empty string and NULL.",
                    "''"));
        }
        if (QUOTED_IDENTIFIER.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "QUOTED_IDENTIFIER_CASE_SENSITIVE",
                    RiskLevel.MEDIUM,
                    "Quoted identifiers become case-sensitive and should be reviewed for application SQL compatibility.",
                    "\"identifier\""));
        }
        if (ROWNUM.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "ROWNUM_REWRITE_REQUIRED",
                    RiskLevel.MEDIUM,
                    "ROWNUM needs PostgreSQL rewrite with LIMIT or window functions depending on ordering semantics.",
                    "ROWNUM"));
        }
        if (CONNECT_BY.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "CONNECT_BY_REWRITE_REQUIRED",
                    RiskLevel.HIGH,
                    "CONNECT BY hierarchical queries need recursive CTE rewrite.",
                    "CONNECT BY"));
        }
        if (DECODE_CALL.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "DECODE_REWRITE_REQUIRED",
                    RiskLevel.MEDIUM,
                    "DECODE should be rewritten to CASE with NULL comparison semantics reviewed.",
                    "DECODE"));
        }
        if (NVL_CALL.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "NVL_REWRITE_REQUIRED",
                    RiskLevel.MEDIUM,
                    "NVL should be rewritten to COALESCE after checking datatype coercion behavior.",
                    "NVL"));
        }
        if (ORACLE_HINT.matcher(sql).find()) {
            risks.add(new RiskIssue(
                    "ORACLE_HINT_IGNORED",
                    RiskLevel.LOW,
                    "Oracle optimizer hints are not portable and should be removed or replaced with PostgreSQL tuning evidence.",
                    "/*+ hint */"));
        }
        if (EXECUTE_IMMEDIATE.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "DYNAMIC_SQL_REVIEW_REQUIRED",
                    RiskLevel.HIGH,
                    "Dynamic SQL needs manual review because dependencies and generated syntax cannot be fully inferred statically.",
                    "EXECUTE IMMEDIATE"));
        }
        if (AUTONOMOUS_TRANSACTION.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "AUTONOMOUS_TRANSACTION_UNSUPPORTED",
                    RiskLevel.BLOCKER,
                    "Oracle autonomous transactions have no direct PostgreSQL equivalent and require application-level redesign.",
                    "PRAGMA AUTONOMOUS_TRANSACTION"));
        }
        if (PACKAGE_DECLARATION.matcher(searchableSql).find() && PACKAGE_GLOBAL.matcher(searchableSql).find()) {
            risks.add(new RiskIssue(
                    "PACKAGE_GLOBAL_STATE_REVIEW_REQUIRED",
                    RiskLevel.HIGH,
                    "Package global variables carry session state and need explicit redesign in PostgreSQL.",
                    "package global variable"));
        }
        return List.copyOf(risks);
    }

    private boolean containsEmptyStringLiteral(String sql) {
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (!inLineComment && !inBlockComment && OracleSqlText.startsOracleQQuote(sql, i)) {
                i = OracleSqlText.skipQQuotedLiteral(sql, i);
                continue;
            }
            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (ch == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (ch == '\'' && isEmptyStringLiteral(sql, i)) {
                return true;
            }
            if (ch == '\'') {
                i = OracleSqlText.skipStringLiteral(sql, i);
            }
        }
        return false;
    }

    private boolean isEmptyStringLiteral(String sql, int start) {
        int contentLength = 0;
        for (int i = start + 1; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                contentLength++;
                i++;
            } else if (ch == '\'') {
                return contentLength == 0;
            } else {
                contentLength++;
            }
        }
        return false;
    }

    private String stripStringLiteralsAndComments(String sql) {
        return OracleSqlText.stripStringLiteralsAndComments(sql);
    }
}
