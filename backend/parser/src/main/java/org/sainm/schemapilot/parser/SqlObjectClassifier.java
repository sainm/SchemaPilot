package org.sainm.schemapilot.parser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sainm.schemapilot.model.DbObjectType;

public class SqlObjectClassifier {

    private static final Pattern CREATE_PATTERN = Pattern.compile(
            "^\\s*create\\s+(?:or\\s+replace\\s+)?(?:no\\s+force\\s+|force\\s+)?"
                    + "(?:editionable\\s+|noneditionable\\s+)?(?:(?:unique|bitmap)\\s+)?"
                    + "(package\\s+body|package|(?:global\\s+temporary\\s+|private\\s+temporary\\s+)?table|index|view|sequence|trigger|function|procedure)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public ClassifiedSqlObject classify(String sql) {
        String sqlWithoutLeadingComments = stripLeadingComments(sql.strip());
        Matcher matcher = CREATE_PATTERN.matcher(sqlWithoutLeadingComments);
        if (!matcher.find()) {
            return new ClassifiedSqlObject(DbObjectType.UNKNOWN, null, null, sql);
        }
        DbObjectType type = toObjectType(matcher.group(1));
        Identifier identifier = parseIdentifier(matcher.group(2));
        return new ClassifiedSqlObject(type, identifier.schema(), identifier.name(), sql);
    }

    private DbObjectType toObjectType(String token) {
        return switch (token.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")) {
            case "table", "global temporary table", "private temporary table" -> DbObjectType.TABLE;
            case "index" -> DbObjectType.INDEX;
            case "view" -> DbObjectType.VIEW;
            case "sequence" -> DbObjectType.SEQUENCE;
            case "trigger" -> DbObjectType.TRIGGER;
            case "function" -> DbObjectType.FUNCTION;
            case "procedure" -> DbObjectType.PROCEDURE;
            case "package" -> DbObjectType.PACKAGE;
            case "package body" -> DbObjectType.PACKAGE_BODY;
            default -> DbObjectType.UNKNOWN;
        };
    }

    private Identifier parseIdentifier(String tail) {
        String rawIdentifier = readIdentifier(tail.stripLeading());
        int dot = findUnquotedDot(rawIdentifier);
        if (dot < 0) {
            return new Identifier(null, unquote(rawIdentifier));
        }
        return new Identifier(unquote(rawIdentifier.substring(0, dot)), unquote(rawIdentifier.substring(dot + 1)));
    }

    private String readIdentifier(String text) {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
                result.append(ch);
                continue;
            }
            if (!quoted && (Character.isWhitespace(ch) || ch == '(')) {
                break;
            }
            result.append(ch);
        }
        return result.toString();
    }

    private String stripLeadingComments(String sql) {
        String remaining = sql.stripLeading();
        while (true) {
            if (remaining.startsWith("--")) {
                int newline = remaining.indexOf('\n');
                if (newline < 0) {
                    return "";
                }
                remaining = remaining.substring(newline + 1).stripLeading();
            } else if (remaining.startsWith("/*")) {
                int end = remaining.indexOf("*/");
                if (end < 0) {
                    return "";
                }
                remaining = remaining.substring(end + 2).stripLeading();
            } else {
                return remaining;
            }
        }
    }

    private int findUnquotedDot(String text) {
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (!quoted && ch == '.') {
                return i;
            }
        }
        return -1;
    }

    private String unquote(String text) {
        String trimmed = text.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
        }
        return trimmed;
    }

    private record Identifier(String schema, String name) {
    }
}
