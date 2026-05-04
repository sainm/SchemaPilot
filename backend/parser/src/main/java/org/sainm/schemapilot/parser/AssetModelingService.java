package org.sainm.schemapilot.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.sainm.schemapilot.common.sql.OracleSqlText;
import org.sainm.schemapilot.model.AssetRepository;
import org.sainm.schemapilot.model.DbColumn;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.DbObjectType;
import org.sainm.schemapilot.model.ObjectStatus;
import org.sainm.schemapilot.model.ParseIssue;
import org.sainm.schemapilot.model.SourceLocation;

public class AssetModelingService {

    private final SqlStatementSplitter splitter;
    private final SqlObjectClassifier classifier;
    private final AssetRepository assetRepository;

    public AssetModelingService(
            SqlStatementSplitter splitter,
            SqlObjectClassifier classifier,
            AssetRepository assetRepository) {
        this.splitter = splitter;
        this.classifier = classifier;
        this.assetRepository = assetRepository;
    }

    public AssetModelingResult modelInputSource(AssetModelingCommand command) {
        List<DbObject> objects = new ArrayList<>();
        List<ParseIssue> parseIssues = new ArrayList<>();
        for (ParsedStatement statement : splitter.split(command.sqlText())) {
            if (OracleSqlText.stripComments(statement.text()).isBlank()) {
                continue;
            }
            SourceLocation location = new SourceLocation(
                    command.sourcePath(),
                    statement.startLine(),
                    statement.endLine(),
                    statement.startOffset(),
                    statement.endOffset());
            ClassifiedSqlObject classified = classifier.classify(statement.text());
            if (classified.objectType() == DbObjectType.UNKNOWN || classified.name() == null || classified.name().isBlank()) {
                parseIssues.add(new ParseIssue(
                        UUID.randomUUID(),
                        command.projectId(),
                        command.sourceProjectId(),
                        command.inputSourceId(),
                        "UNSUPPORTED_STATEMENT",
                        "Statement is not recognized as a supported Oracle schema object",
                        location,
                        statement.text()));
                continue;
            }
            objects.add(new DbObject(
                    UUID.randomUUID(),
                    command.projectId(),
                    command.sourceProjectId(),
                    command.inputSourceId(),
                    classified.objectType(),
                    classified.schema(),
                    classified.name(),
                    ObjectStatus.PARSED,
                    location,
                    statement.text(),
                    extractColumns(classified.objectType(), statement.text())));
        }

        assetRepository.replaceInputSourceAssets(command.inputSourceId(), objects, parseIssues);
        return new AssetModelingResult(objects, parseIssues);
    }

    private List<DbColumn> extractColumns(DbObjectType type, String sql) {
        if (type != DbObjectType.TABLE) {
            return List.of();
        }
        String body = extractFirstParenthesizedBlock(sql);
        if (body.isBlank()) {
            return List.of();
        }
        List<DbColumn> columns = new ArrayList<>();
        int ordinal = 1;
        for (String part : splitTopLevelCommas(body)) {
            String definition = part.strip();
            if (definition.isBlank() || isTableConstraint(definition)) {
                continue;
            }
            ColumnParts columnParts = readColumnParts(definition);
            columns.add(new DbColumn(
                    columnParts.name(),
                    columnParts.type(),
                    null,
                    !definition.toLowerCase(Locale.ROOT).contains(" not null"),
                    ordinal++));
        }
        return columns;
    }

    private String extractFirstParenthesizedBlock(String sql) {
        String sanitizedSql = OracleSqlText.stripComments(stripLeadingComments(sql));
        int start = sanitizedSql.indexOf('(');
        if (start < 0) {
            return "";
        }
        int depth = 0;
        for (int i = start; i < sanitizedSql.length(); i++) {
            char ch = sanitizedSql.charAt(i);
            if (OracleSqlText.startsOracleQQuote(sanitizedSql, i)) {
                i = OracleSqlText.skipQQuotedLiteral(sanitizedSql, i);
                continue;
            }
            if (ch == '\'') {
                i = OracleSqlText.skipStringLiteral(sanitizedSql, i);
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return sanitizedSql.substring(start + 1, i);
                }
            }
        }
        return "";
    }

    private List<String> splitTopLevelCommas(String body) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (OracleSqlText.startsOracleQQuote(body, i)) {
                i = OracleSqlText.skipQQuotedLiteral(body, i);
                continue;
            }
            if (ch == '\'') {
                i = OracleSqlText.skipStringLiteral(body, i);
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    private boolean isTableConstraint(String definition) {
        String lower = definition.toLowerCase(Locale.ROOT);
        return lower.startsWith("constraint ")
                || lower.startsWith("primary key")
                || lower.startsWith("unique ")
                || lower.startsWith("check ")
                || lower.startsWith("foreign key");
    }

    private ColumnParts readColumnParts(String definition) {
        String trimmed = definition.stripLeading();
        String name;
        String rest;
        if (trimmed.startsWith("\"")) {
            int end = findQuotedIdentifierEnd(trimmed);
            name = trimmed.substring(1, end).replace("\"\"", "\"");
            rest = trimmed.substring(end + 1).stripLeading();
        } else {
            int end = 0;
            while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
                end++;
            }
            name = trimmed.substring(0, end);
            rest = trimmed.substring(end).stripLeading();
        }
        return new ColumnParts(name, trimType(rest));
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

    private int findQuotedIdentifierEnd(String text) {
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) == '"' && (i + 1 >= text.length() || text.charAt(i + 1) != '"')) {
                return i;
            }
        }
        return text.length() - 1;
    }

    private String trimType(String rest) {
        String lower = rest.toLowerCase(Locale.ROOT);
        int cut = rest.length();
        for (String marker : List.of(" default ", " not null", " null", " constraint ", " primary key", " unique", " check ", " references ", " generated ")) {
            int index = lower.indexOf(marker);
            if (index >= 0) {
                cut = Math.min(cut, index);
            }
        }
        return rest.substring(0, cut).strip();
    }

    private record ColumnParts(String name, String type) {
    }
}
