package org.sainm.schemapilot.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PlsqlRewriteAdvisor {
    private static final Pattern TRIGGER_HEADER = Pattern.compile("(?is)create\\s+(?:or\\s+replace\\s+)?trigger\\s+(\\S+)\\s+(before|after|instead\\s+of)\\s+(.+?)\\s+on\\s+(\\S+)");
    private static final Pattern ROUTINE_HEADER = Pattern.compile("(?is)create\\s+(?:or\\s+replace\\s+)?(function|procedure)\\s+([\\w$#\".]+)\\s*(\\([^)]*\\))?(.*?)(?:is|as)\\b");
    private static final Pattern PACKAGE_ROUTINE = Pattern.compile("(?im)^\\s*(procedure|function)\\s+([a-z][\\w$#]*)\\s*(\\([^;]*\\))?");

    public String triggerDraft(String objectName, String sql) {
        var matcher = TRIGGER_HEADER.matcher(sql);
        var triggerName = objectName;
        var timing = "BEFORE";
        var events = "INSERT OR UPDATE";
        var tableName = "target_table";
        if (matcher.find()) {
            triggerName = cleanName(matcher.group(1));
            timing = matcher.group(2).replaceAll("\\s+", " ").toUpperCase();
            events = matcher.group(3).replaceAll("(?is)\\s+for\\s+each\\s+row.*", "").strip().toUpperCase();
            tableName = cleanName(matcher.group(4));
        }
        return """
                -- DRAFT: Oracle trigger %s converted into PostgreSQL trigger function skeleton.
                -- Review :NEW/:OLD mappings, statement-level behavior, mutating-table assumptions, and transaction side effects.
                CREATE OR REPLACE FUNCTION %s_fn()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                  -- TODO: move Oracle trigger body here.
                  -- Oracle :NEW maps to NEW; :OLD maps to OLD.
                  RETURN NEW;
                END;
                $$;

                CREATE TRIGGER %s
                %s %s ON %s
                FOR EACH ROW
                EXECUTE FUNCTION %s_fn();

                -- Original Oracle trigger:
                /*
                %s
                */
                """.formatted(triggerName, normalizePgName(triggerName), normalizePgName(triggerName), timing, events, tableName, normalizePgName(triggerName), stripSqlPlusTerminator(sql));
    }

    public String routineDraft(String objectName, String sql) {
        var matcher = ROUTINE_HEADER.matcher(sql);
        var kind = "routine";
        var routineName = objectName;
        var parameters = "";
        if (matcher.find()) {
            kind = matcher.group(1).toLowerCase();
            routineName = cleanName(matcher.group(2));
            parameters = normalizeParameters(matcher.group(3));
        }
        var returns = "function".equals(kind) ? "\nRETURNS text" : "";
        return """
                -- DRAFT: Oracle %s %s converted into PL/pgSQL review skeleton.
                -- Review parameter modes, OUT params, exception behavior, transaction control, and dynamic SQL.
                CREATE OR REPLACE %s %s(%s)%s
                LANGUAGE plpgsql
                AS $$
                BEGIN
                  -- TODO: port Oracle PL/SQL body.
                  -- EXECUTE IMMEDIATE maps to PostgreSQL EXECUTE format(...) USING ...
                  NULL;
                EXCEPTION
                  WHEN others THEN
                    -- TODO: review Oracle SQLCODE/SQLERRM handling and rethrow policy.
                    RAISE;
                END;
                $$;

                -- Original Oracle routine:
                /*
                %s
                */
                """.formatted(kind, routineName, kind, normalizePgName(routineName), parameters, returns, stripSqlPlusTerminator(sql));
    }

    public String packageDraft(String objectName, String sql) {
        var routines = packageRoutines(sql);
        var builtIns = builtinPackageSuggestions(sql);
        var globals = Pattern.compile("(?im)^\\s*(g_[a-z][\\w$#]*|[a-z][\\w$#]*)\\s+(constant\\s+)?(number|varchar2|nvarchar2|date|boolean|clob|blob|raw)\\b").matcher(sql).results()
                .map(match -> "- " + match.group(1))
                .distinct()
                .toList();
        return """
                -- MANUAL_REQUIRED: Oracle package %s must be decomposed for PostgreSQL.
                -- Package routines discovered:
                %s
                -- Package global state candidates:
                %s
                -- Oracle built-in package replacements:
                %s
                -- Suggested target shape:
                -- 1. Convert stateless package routines to standalone schema functions/procedures.
                -- 2. Move package state into tables, explicit parameters, or session settings.
                -- 3. Replace package initialization logic with migration/setup scripts.

                -- Original package source:
                /*
                %s
                */
                """.formatted(
                objectName,
                bulletList(routines),
                bulletList(globals),
                bulletList(builtIns),
                stripSqlPlusTerminator(sql)
        );
    }

    public List<String> packageRoutines(String sql) {
        var matcher = PACKAGE_ROUTINE.matcher(sql);
        var routines = new ArrayList<String>();
        while (matcher.find()) {
            var signature = matcher.group(1).toLowerCase() + " " + matcher.group(2) + (matcher.group(3) == null ? "()" : matcher.group(3).replaceAll("\\s+", " "));
            routines.add(signature);
        }
        return routines.stream().distinct().toList();
    }

    public List<String> builtinPackageSuggestions(String sql) {
        var suggestions = new ArrayList<String>();
        addIfPresent(sql, suggestions, "DBMS_OUTPUT", "DBMS_OUTPUT.PUT_LINE -> RAISE NOTICE or application logging");
        addIfPresent(sql, suggestions, "DBMS_LOB", "DBMS_LOB -> PostgreSQL text/bytea functions or large object API");
        addIfPresent(sql, suggestions, "DBMS_RANDOM", "DBMS_RANDOM -> pgcrypto gen_random_* functions");
        addIfPresent(sql, suggestions, "UTL_FILE", "UTL_FILE -> external file service or controlled server-side COPY workflow");
        addIfPresent(sql, suggestions, "DBMS_SCHEDULER", "DBMS_SCHEDULER -> pg_cron, OS scheduler, or orchestration platform");
        addIfPresent(sql, suggestions, "DBMS_SQL", "DBMS_SQL -> PL/pgSQL EXECUTE with explicit bind handling");
        return suggestions;
    }

    private void addIfPresent(String sql, List<String> suggestions, String token, String suggestion) {
        if (Pattern.compile("\\b" + token + "\\b", Pattern.CASE_INSENSITIVE).matcher(sql).find()) {
            suggestions.add(suggestion);
        }
    }

    private String normalizeParameters(String raw) {
        var parameters = raw == null ? "" : raw.strip();
        if (parameters.startsWith("(") && parameters.endsWith(")")) {
            parameters = parameters.substring(1, parameters.length() - 1);
        }
        return parameters
                .replaceAll("(?i)\\bIN\\s+OUT\\b", "INOUT")
                .replaceAll("(?i)\\bVARCHAR2\\b", "text")
                .replaceAll("(?i)\\bNUMBER\\b", "numeric")
                .replaceAll("(?i)\\bDATE\\b", "timestamp")
                .strip();
    }

    private String bulletList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-- - none detected";
        }
        return values.stream().map(value -> "-- - " + value).reduce((left, right) -> left + "\n" + right).orElse("-- - none detected");
    }

    private String cleanName(String name) {
        return name.replace("\"", "").replaceAll("[;()]", "").strip();
    }

    private String normalizePgName(String name) {
        return cleanName(name).replaceAll("[^A-Za-z0-9_]", "_").toLowerCase();
    }

    private String stripSqlPlusTerminator(String sql) {
        return sql.strip().replaceAll("(?m)^\\s*/\\s*$", "").strip();
    }
}
