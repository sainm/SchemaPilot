package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ConversionLevel;
import org.sainm.schemapilot.model.ObjectType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class OracleToPostgresConverter implements ObjectConverter {
    private static final Pattern NUMBER_WITH_SCALE = Pattern.compile("(?i)\\bNUMBER\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
    private static final Pattern NUMBER_WITH_PRECISION = Pattern.compile("(?i)\\bNUMBER\\s*\\(\\s*(\\d+)\\s*\\)");

    @Override
    public boolean supports(ObjectType objectType) {
        return true;
    }

    @Override
    public ConversionDraft convert(ConversionContext context) {
        var sql = context.originalSql();
        if (context.objectType() == ObjectType.TRIGGER) {
            return new ConversionDraft(triggerDraft(context.objectName(), sql), ConversionLevel.DRAFT);
        }
        if (context.objectType() == ObjectType.FUNCTION || context.objectType() == ObjectType.PROCEDURE) {
            return new ConversionDraft(routineDraft(context.objectName(), sql), ConversionLevel.DRAFT);
        }
        if (context.objectType() == ObjectType.PACKAGE || context.objectType() == ObjectType.PACKAGE_BODY) {
            return new ConversionDraft(packageDraft(context.objectName(), sql), ConversionLevel.MANUAL_REQUIRED);
        }

        var converted = stripSqlPlusTerminator(sql);
        converted = replaceNumberTypes(converted);
        converted = converted
                .replaceAll("(?i)\\bVARCHAR2\\s*\\(", "varchar(")
                .replaceAll("(?i)\\bNVARCHAR2\\s*\\(", "varchar(")
                .replaceAll("(?i)\\bCLOB\\b", "text")
                .replaceAll("(?i)\\bBLOB\\b", "bytea")
                .replaceAll("(?i)\\bRAW\\s*\\(([^)]*)\\)", "bytea")
                .replaceAll("(?i)\\bDATE\\b", "timestamp")
                .replaceAll("(?i)\\bSYSDATE\\b", "CURRENT_TIMESTAMP")
                .replaceAll("(?i)\\bSYSTIMESTAMP\\b", "CURRENT_TIMESTAMP")
                .replaceAll("(?i)\\bNVL\\s*\\(", "COALESCE(");

        return new ConversionDraft(converted, context.objectType() == ObjectType.UNKNOWN
                ? ConversionLevel.REVIEW_REQUIRED
                : ConversionLevel.AUTO);
    }

    private String replaceNumberTypes(String sql) {
        var withScale = NUMBER_WITH_SCALE.matcher(sql).replaceAll(match -> {
            int precision = Integer.parseInt(match.group(1));
            int scale = Integer.parseInt(match.group(2));
            if (scale == 0 && precision <= 9) {
                return "integer";
            }
            if (scale == 0 && precision <= 18) {
                return "bigint";
            }
            return "numeric(" + precision + "," + scale + ")";
        });

        return NUMBER_WITH_PRECISION.matcher(withScale).replaceAll(match -> {
            int precision = Integer.parseInt(match.group(1));
            if (precision <= 9) {
                return "integer";
            }
            if (precision <= 18) {
                return "bigint";
            }
            return "numeric(" + precision + ")";
        }).replaceAll("(?i)\\bNUMBER\\b", "numeric");
    }

    private String triggerDraft(String objectName, String sql) {
        return """
                -- DRAFT: Oracle trigger %s requires a PostgreSQL trigger function plus trigger binding.
                -- Original Oracle trigger:
                /*
                %s
                */
                """.formatted(objectName, stripSqlPlusTerminator(sql));
    }

    private String routineDraft(String objectName, String sql) {
        return """
                -- DRAFT: Oracle routine %s requires PL/pgSQL review.
                -- Original Oracle routine:
                /*
                %s
                */
                """.formatted(objectName, stripSqlPlusTerminator(sql));
    }

    private String packageDraft(String objectName, String sql) {
        return """
                -- MANUAL_REQUIRED: Oracle package %s should be split into PostgreSQL functions/procedures and reviewed.
                -- Original package source:
                /*
                %s
                */
                """.formatted(objectName, stripSqlPlusTerminator(sql));
    }

    private String stripSqlPlusTerminator(String sql) {
        return sql.strip().replaceAll("(?m)^\\s*/\\s*$", "").strip();
    }
}
