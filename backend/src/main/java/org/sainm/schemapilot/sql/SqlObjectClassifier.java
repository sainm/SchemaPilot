package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ObjectType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class SqlObjectClassifier {
    private static final Pattern CREATE_PATTERN = Pattern.compile(
            "(?is)^\\s*create\\s+(?:or\\s+replace\\s+)?(table|view|index|sequence|trigger|function|procedure|package(?:\\s+body)?)\\s+([^\\s(]+)"
    );

    ClassifiedStatement classify(String sql) {
        var matcher = CREATE_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return new ClassifiedStatement(ObjectType.UNKNOWN, "anonymous_sql");
        }

        var typeToken = matcher.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        var objectType = switch (typeToken) {
            case "table" -> ObjectType.TABLE;
            case "view" -> ObjectType.VIEW;
            case "index" -> ObjectType.INDEX;
            case "sequence" -> ObjectType.SEQUENCE;
            case "trigger" -> ObjectType.TRIGGER;
            case "function" -> ObjectType.FUNCTION;
            case "procedure" -> ObjectType.PROCEDURE;
            case "package" -> ObjectType.PACKAGE;
            case "package_body" -> ObjectType.PACKAGE_BODY;
            default -> ObjectType.UNKNOWN;
        };

        return new ClassifiedStatement(objectType, cleanName(matcher.group(2)));
    }

    private String cleanName(String rawName) {
        return rawName.replace("\"", "").replaceAll("[;,]", "");
    }
}
