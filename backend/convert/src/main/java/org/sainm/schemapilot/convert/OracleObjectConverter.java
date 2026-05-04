package org.sainm.schemapilot.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sainm.schemapilot.common.sql.OracleSqlText;
import org.sainm.schemapilot.model.DbColumn;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.DbObjectType;
import org.sainm.schemapilot.rule.RuleHit;

public class OracleObjectConverter implements ObjectConverter {

    private static final Pattern DEFAULT_SEQUENCE_NEXTVAL = Pattern.compile(
            "\\bdefault\\s+((?:\"(?:[^\"]|\"\")+\"|[\\w$#]+)(?:\\.(?:\"(?:[^\"]|\"\")+\"|[\\w$#]+))*)\\.nextval\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_TABLE_IDENTIFIER = Pattern.compile(
            "^\\s*create\\s+(?:global\\s+temporary\\s+|private\\s+temporary\\s+)?table\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ORACLE_TEMPORARY_TABLE = Pattern.compile(
            "^\\s*create\\s+(?:global|private)\\s+temporary\\s+table\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TEMPORARY_TABLE_ON_COMMIT = Pattern.compile(
            "\\bon\\s+commit\\s+(delete|preserve)\\s+rows\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SYSDATE_TOKEN = Pattern.compile("\\bsysdate\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_COLUMN_USING_INDEX = Pattern.compile(
            "\\s+\\busing\\s+index\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INLINE_COLUMN_TRAILING_STATE = Pattern.compile(
            "\\s+\\b(?:enable|disable|validate|novalidate|rely|norely)\\b"
                    + "(?:\\s+\\b(?:enable|disable|validate|novalidate|rely|norely)\\b)*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_CONSTRAINT_USING_INDEX = Pattern.compile(
            "\\s+\\busing\\s+index\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TABLE_CONSTRAINT_TRAILING_STATE = Pattern.compile(
            "\\s+\\b(?:enable|disable|validate|novalidate|rely|norely)\\b"
                    + "(?:\\s+\\b(?:enable|disable|validate|novalidate|rely|norely)\\b)*\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final OracleTypeMapper typeMapper;

    public OracleObjectConverter(OracleTypeMapper typeMapper) {
        this.typeMapper = typeMapper;
    }

    @Override
    public ConversionResult convert(DbObject object, ConversionContext context) {
        if (object.type() != DbObjectType.TABLE) {
            return switch (object.type()) {
                case INDEX, SEQUENCE, VIEW -> compatibleDraft(object, context);
                default -> manualRequired(object, context, "Only conservative DDL conversion is available in the P0 automatic path.");
            };
        }
        List<RuleHit> ruleHits = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> columnSql = new ArrayList<>();
        Map<String, ColumnDefinition> originalColumns = originalColumnDefinitions(object.originalSql());
        boolean temporaryTable = isOracleTemporaryTable(object.originalSql());
        boolean reviewRequired = temporaryTable;
        if (temporaryTable) {
            ruleHits.add(new RuleHit(
                    "ORACLE_TEMPORARY_TABLE_REVIEW",
                    "Review Oracle temporary table semantics",
                    "temporary table",
                    "Oracle temporary table lifetime and ON COMMIT behavior need explicit PostgreSQL review before export."));
            notes.add("Review Oracle temporary table lifetime and ON COMMIT behavior before freezing the SQL baseline.");
        }
        for (DbColumn column : object.columns()) {
            TypeMappingResult mapping = typeMapper.map(column.sourceType());
            if (mapping.reviewRequired()) {
                reviewRequired = true;
            }
            notes.addAll(mapping.notes());
            ruleHits.add(new RuleHit(
                    "TYPE_MAPPING_" + column.sourceType().toUpperCase().replaceAll("[^A-Z0-9]+", "_"),
                    "Map Oracle type " + column.sourceType(),
                    column.sourceType(),
                    "Mapped to PostgreSQL " + mapping.targetType()));
            ColumnDefinition originalColumn = originalColumns.get(normalizeIdentifier(column.name()));
            String preservedSuffix = originalColumn == null ? "" : originalColumn.suffix();
            ColumnSuffixRewrite suffixRewrite = rewriteColumnSuffix(preservedSuffix);
            if (suffixRewrite.reviewRequired()) {
                reviewRequired = true;
            }
            ruleHits.addAll(suffixRewrite.ruleHits());
            notes.addAll(suffixRewrite.notes());
            columnSql.add("    %s %s%s".formatted(
                    quoteIdentifier(column.name(), originalColumn != null && originalColumn.quoted()),
                    mapping.targetType(),
                    suffixRewrite.suffix().isBlank() ? column.nullable() ? "" : " not null" : " " + suffixRewrite.suffix()));
        }
        for (String constraint : extractTableConstraints(object.originalSql())) {
            ColumnSuffixRewrite constraintRewrite = rewriteTableConstraint(constraint);
            if (constraintRewrite.reviewRequired()) {
                reviewRequired = true;
            }
            ruleHits.addAll(constraintRewrite.ruleHits());
            notes.addAll(constraintRewrite.notes());
            columnSql.add("    " + constraintRewrite.suffix());
        }
        String targetSql = "%s %s (\n%s\n)%s;".formatted(
                temporaryTable ? "create temporary table" : "create table",
                qualifiedName(object),
                String.join(",\n", columnSql),
                temporaryTable ? temporaryOnCommitClause(object.originalSql()) : "");
        return new ConversionResult(
                UUID.randomUUID(),
                context.projectId(),
                context.sourceProjectId(),
                context.inputSourceId(),
                object.id(),
                object.originalSql(),
                targetSql,
                reviewRequired ? ConversionLevel.REVIEW_REQUIRED : ConversionLevel.AUTO,
                ruleHits,
                notes.stream().distinct().toList());
    }

    private ConversionResult compatibleDraft(DbObject object, ConversionContext context) {
        String note = switch (object.type()) {
            case INDEX -> "Review index method, expression syntax, and tablespace/storage clauses before execution.";
            case SEQUENCE -> "Review sequence min/max/cache/cycle semantics and reset position after data load.";
            case VIEW -> "Simple view SQL is carried forward as a draft; Oracle functions and outer joins still need precheck.";
            default -> "Review generated SQL before execution.";
        };
        return new ConversionResult(
                UUID.randomUUID(),
                context.projectId(),
                context.sourceProjectId(),
                context.inputSourceId(),
                object.id(),
                object.originalSql(),
                "-- DRAFT: " + note + "\n" + stripTerminator(object.originalSql()),
                ConversionLevel.DRAFT,
                List.of(new RuleHit("CONSERVATIVE_" + object.type().name() + "_DRAFT", "Conservative DDL draft", object.type().name(), note)),
                List.of(note));
    }

    private ConversionResult manualRequired(DbObject object, ConversionContext context, String note) {
        return new ConversionResult(
                UUID.randomUUID(),
                context.projectId(),
                context.sourceProjectId(),
                context.inputSourceId(),
                object.id(),
                object.originalSql(),
                "-- MANUAL_REQUIRED: " + note + "\n" + object.originalSql(),
                ConversionLevel.MANUAL_REQUIRED,
                List.of(new RuleHit("MANUAL_REQUIRED_OBJECT", "Manual conversion required", object.type().name(), note)),
                List.of(note));
    }

    private List<String> extractTableConstraints(String sql) {
        String body = extractFirstParenthesizedBlock(sql);
        if (body.isBlank()) {
            return List.of();
        }
        return splitTopLevelCommas(body).stream()
                .map(String::strip)
                .filter(this::isTableConstraint)
                .toList();
    }

    private Map<String, ColumnDefinition> originalColumnDefinitions(String sql) {
        String body = extractFirstParenthesizedBlock(sql);
        if (body.isBlank()) {
            return Map.of();
        }
        Map<String, ColumnDefinition> columns = new LinkedHashMap<>();
        for (String part : splitTopLevelCommas(body)) {
            String definition = part.strip();
            if (definition.isBlank() || isTableConstraint(definition)) {
                continue;
            }
            ColumnDefinition column = readColumnDefinition(definition);
            columns.put(normalizeIdentifier(column.name()), column);
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

    private ColumnDefinition readColumnDefinition(String definition) {
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
        String sourceType = trimType(rest);
        String suffix = rest.substring(sourceType.length()).stripLeading();
        return new ColumnDefinition(name, sourceType, suffix, trimmed.startsWith("\""));
    }

    private ColumnSuffixRewrite rewriteColumnSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return new ColumnSuffixRewrite("", false, List.of(), List.of());
        }
        String rewritten = rewriteSequenceDefaults(suffix);
        boolean reviewRequired = !rewritten.equals(suffix);
        String sysdateRewritten = replaceOutsideLiterals(rewritten, SYSDATE_TOKEN, ignored -> "clock_timestamp()");
        if (!sysdateRewritten.equals(rewritten)) {
            rewritten = sysdateRewritten;
            reviewRequired = true;
        }
        String strippedUsingIndex = replaceOutsideLiterals(rewritten, INLINE_COLUMN_USING_INDEX, ignored -> "");
        if (!strippedUsingIndex.equals(rewritten)) {
            rewritten = strippedUsingIndex;
            reviewRequired = true;
        }
        String strippedEnableDisable = replaceOutsideLiterals(rewritten, INLINE_COLUMN_TRAILING_STATE, ignored -> "");
        if (!strippedEnableDisable.equals(rewritten)) {
            rewritten = strippedEnableDisable;
            reviewRequired = true;
        }
        if (!reviewRequired) {
            return new ColumnSuffixRewrite(rewritten, false, List.of(), List.of());
        }
        RuleHit ruleHit = new RuleHit(
                "ORACLE_INLINE_COLUMN_CLAUSE_REVIEW",
                "Review Oracle inline column clause",
                suffix,
                "Oracle-specific inline defaults or constraint modifiers were rewritten conservatively; review the generated PostgreSQL clause before export.");
        return new ColumnSuffixRewrite(
                rewritten.strip(),
                true,
                List.of(ruleHit),
                List.of("Review rewritten Oracle inline column clauses before freezing the SQL baseline."));
    }

    private ColumnSuffixRewrite rewriteTableConstraint(String constraint) {
        String rewritten = replaceOutsideLiterals(constraint, TABLE_CONSTRAINT_USING_INDEX, ignored -> "");
        boolean reviewRequired = !rewritten.equals(constraint);
        String strippedTrailingState = replaceOutsideLiterals(rewritten, TABLE_CONSTRAINT_TRAILING_STATE, ignored -> "");
        if (!strippedTrailingState.equals(rewritten)) {
            rewritten = strippedTrailingState;
            reviewRequired = true;
        }
        if (!reviewRequired) {
            return new ColumnSuffixRewrite(rewritten, false, List.of(), List.of());
        }
        RuleHit ruleHit = new RuleHit(
                "ORACLE_TABLE_CONSTRAINT_REVIEW",
                "Review Oracle table constraint clause",
                constraint,
                "Oracle-specific table constraint clauses were rewritten conservatively; review the generated PostgreSQL constraint before export.");
        return new ColumnSuffixRewrite(
                rewritten.strip(),
                true,
                List.of(ruleHit),
                List.of("Review rewritten Oracle table constraints before freezing the SQL baseline."));
    }

    private boolean isOracleTemporaryTable(String sql) {
        return ORACLE_TEMPORARY_TABLE.matcher(stripLeadingComments(sql)).find();
    }

    private String temporaryOnCommitClause(String sql) {
        Matcher matcher = TEMPORARY_TABLE_ON_COMMIT.matcher(OracleSqlText.stripStringLiteralsAndComments(sql));
        if (!matcher.find()) {
            return " on commit delete rows";
        }
        return " on commit " + matcher.group(1).toLowerCase(Locale.ROOT) + " rows";
    }

    private String rewriteSequenceDefaults(String suffix) {
        return replaceOutsideLiterals(suffix, DEFAULT_SEQUENCE_NEXTVAL, matcher -> {
            String sequenceName = toRegclassName(suffix.substring(matcher.start(1), matcher.end(1)));
            return "default nextval('" + sequenceName + "'::regclass)";
        });
    }

    private String replaceOutsideLiterals(String sql, Pattern pattern, Function<Matcher, String> replacementFactory) {
        String searchable = OracleSqlText.stripStringLiteralsAndComments(sql);
        Matcher matcher = pattern.matcher(searchable);
        StringBuilder rewritten = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            rewritten.append(sql, last, matcher.start());
            rewritten.append(replacementFactory.apply(matcher));
            last = matcher.end();
        }
        rewritten.append(sql, last, sql.length());
        return rewritten.toString();
    }

    private String toRegclassName(String sequenceName) {
        List<String> parts = splitQualifiedIdentifier(sequenceName);
        List<String> regclassParts = new ArrayList<>();
        for (String part : parts) {
            regclassParts.add(toRegclassPart(part));
        }
        return String.join(".", regclassParts).replace("'", "''");
    }

    private List<String> splitQualifiedIdentifier(String identifier) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < identifier.length(); i++) {
            char ch = identifier.charAt(i);
            if (ch == '"') {
                current.append(ch);
                if (quoted && i + 1 < identifier.length() && identifier.charAt(i + 1) == '"') {
                    current.append(identifier.charAt(i + 1));
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == '.' && !quoted) {
                parts.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString().strip());
        return parts;
    }

    private String toRegclassPart(String part) {
        if (part.length() >= 2 && part.startsWith("\"") && part.endsWith("\"")) {
            return part;
        }
        return part.toLowerCase(Locale.ROOT);
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

    private String qualifiedName(DbObject object) {
        QualifiedIdentifier originalIdentifier = originalObjectIdentifier(object.originalSql());
        boolean schemaQuoted = originalIdentifier.schema() != null && originalIdentifier.schema().quoted();
        boolean nameQuoted = originalIdentifier.name() != null && originalIdentifier.name().quoted();
        if (object.schema() == null || object.schema().isBlank()) {
            return quoteIdentifier(object.name(), nameQuoted);
        }
        return quoteIdentifier(object.schema(), schemaQuoted) + "." + quoteIdentifier(object.name(), nameQuoted);
    }

    private QualifiedIdentifier originalObjectIdentifier(String sql) {
        Matcher matcher = CREATE_TABLE_IDENTIFIER.matcher(stripLeadingComments(sql));
        if (!matcher.find()) {
            return new QualifiedIdentifier(null, null);
        }
        List<IdentifierPart> parts = readQualifiedIdentifierParts(matcher.group(1));
        if (parts.isEmpty()) {
            return new QualifiedIdentifier(null, null);
        }
        if (parts.size() == 1) {
            return new QualifiedIdentifier(null, parts.getFirst());
        }
        return new QualifiedIdentifier(parts.get(parts.size() - 2), parts.getLast());
    }

    private List<IdentifierPart> readQualifiedIdentifierParts(String text) {
        List<IdentifierPart> parts = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= text.length()) {
                break;
            }
            IdentifierRead identifier = readIdentifier(text, index);
            if (identifier == null) {
                break;
            }
            parts.add(identifier.part());
            index = identifier.nextIndex();
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= text.length() || text.charAt(index) != '.') {
                break;
            }
            index++;
        }
        return parts;
    }

    private IdentifierRead readIdentifier(String text, int start) {
        if (text.charAt(start) == '"') {
            int end = findQuotedIdentifierEnd(text.substring(start)) + start;
            String value = text.substring(start + 1, end).replace("\"\"", "\"");
            return new IdentifierRead(new IdentifierPart(value, true), end + 1);
        }
        int end = start;
        while (end < text.length()) {
            char ch = text.charAt(end);
            if (Character.isWhitespace(ch) || ch == '(' || ch == '.' || ch == ';') {
                break;
            }
            end++;
        }
        if (end == start) {
            return null;
        }
        return new IdentifierRead(new IdentifierPart(text.substring(start, end), false), end);
    }

    private String stripTerminator(String sql) {
        String stripped = sql.strip();
        return stripped.endsWith(";") ? stripped : stripped + ";";
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

    private String quoteIdentifier(String identifier) {
        return quoteIdentifier(identifier, false);
    }

    private String quoteIdentifier(String identifier, boolean preserveCase) {
        String canonical = preserveCase ? identifier : canonicalIdentifier(identifier);
        return "\"" + canonical.replace("\"", "\"\"") + "\"";
    }

    private String canonicalIdentifier(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }

    private String normalizeIdentifier(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }

    private record ColumnDefinition(String name, String sourceType, String suffix, boolean quoted) {
    }

    private record IdentifierPart(String value, boolean quoted) {
    }

    private record IdentifierRead(IdentifierPart part, int nextIndex) {
    }

    private record QualifiedIdentifier(IdentifierPart schema, IdentifierPart name) {
    }

    private record ColumnSuffixRewrite(String suffix, boolean reviewRequired, List<RuleHit> ruleHits, List<String> notes) {
    }
}
