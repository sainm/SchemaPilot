package org.sainm.schemapilot.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.sainm.schemapilot.common.sql.OracleSqlText;

public class SqlStatementSplitter {

    private static final Pattern PLSQL_CREATE_PATTERN = Pattern.compile(
            "^create\\s+(?:or\\s+replace\\s+)?(?:editionable\\s+|noneditionable\\s+)?"
                    + "(?:trigger|function|procedure|package)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public List<ParsedStatement> split(String sql) {
        List<ParsedStatement> statements = new ArrayList<>();
        String[] lines = sql.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder current = new StringBuilder();
        int currentStartLine = 1;
        int offset = 0;
        int currentStartOffset = 0;
        boolean inPlsqlBlock = false;
        boolean inBlockComment = false;
        Character qQuoteClosingDelimiter = null;
        boolean inStringLiteral = false;
        boolean inPlsqlStringLiteral = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (inPlsqlBlock) {
                if (current.isEmpty()) {
                    currentStartLine = i + 1;
                    currentStartOffset = offset;
                }
                appendLine(current, line);
                PlsqlLiteralScan literalScan = scanPlsqlLiteralState(line, qQuoteClosingDelimiter, inPlsqlStringLiteral, inBlockComment);
                qQuoteClosingDelimiter = literalScan.qQuoteClosingDelimiter();
                inPlsqlStringLiteral = literalScan.inStringLiteral();
                inBlockComment = literalScan.inBlockComment();
                if (qQuoteClosingDelimiter == null && !inPlsqlStringLiteral && !inBlockComment && "/".equals(trimmed)) {
                    addStatement(statements, current, currentStartLine, i + 1, currentStartOffset, offset + line.length());
                    current.setLength(0);
                    inPlsqlBlock = false;
                    qQuoteClosingDelimiter = null;
                    inBlockComment = false;
                    inPlsqlStringLiteral = false;
                }
            } else {
                int segmentStart = 0;
                CommentScan commentScan = statementEndIndexes(line, inBlockComment, qQuoteClosingDelimiter, inStringLiteral);
                inBlockComment = commentScan.inBlockComment();
                qQuoteClosingDelimiter = commentScan.qQuoteClosingDelimiter();
                inStringLiteral = commentScan.inStringLiteral();
                for (int end : commentScan.statementEndIndexes()) {
                    String segment = line.substring(segmentStart, end + 1);
                    if (current.isEmpty()) {
                        currentStartLine = i + 1;
                        currentStartOffset = offset + segmentStart;
                    }
                    appendLine(current, segment);
                    if (startsPlsqlBlock(current.toString())) {
                        String tail = line.substring(end + 1);
                        if (!tail.isBlank()) {
                            current.append(tail);
                        }
                        inPlsqlBlock = true;
                        inPlsqlStringLiteral = inStringLiteral;
                        inStringLiteral = false;
                        segmentStart = line.length();
                        break;
                    }
                    addStatement(statements, current, currentStartLine, i + 1, currentStartOffset, offset + end + 1);
                    current.setLength(0);
                    segmentStart = end + 1;
                }
                if (!inPlsqlBlock && segmentStart < line.length()) {
                    String tail = line.substring(segmentStart);
                    if (!tail.isBlank() || !current.isEmpty()) {
                        if (current.isEmpty()) {
                            currentStartLine = i + 1;
                            currentStartOffset = offset + segmentStart;
                        }
                        appendLine(current, tail);
                        if (startsPlsqlBlock(current.toString())) {
                            inPlsqlBlock = true;
                            inPlsqlStringLiteral = inStringLiteral;
                            inStringLiteral = false;
                        }
                    }
                }
            }
            offset += line.length() + 1;
        }

        if (!current.toString().isBlank()) {
            addStatement(statements, current, currentStartLine, lines.length, currentStartOffset, sql.length());
        }
        return statements;
    }

    private void appendLine(StringBuilder current, String line) {
        if (!current.isEmpty()) {
            current.append('\n');
        }
        current.append(line);
    }

    private boolean startsPlsqlBlock(String text) {
        String lower = stripLeadingComments(text).stripLeading().toLowerCase(Locale.ROOT);
        return PLSQL_CREATE_PATTERN.matcher(lower).find()
                || lower.startsWith("declare")
                || lower.startsWith("begin");
    }

    private String stripLeadingComments(String text) {
        String remaining = text.stripLeading();
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

    private CommentScan statementEndIndexes(
            String line,
            boolean inBlockComment,
            Character qQuoteClosingDelimiter,
            boolean inStringLiteral) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (qQuoteClosingDelimiter != null) {
                if (ch == qQuoteClosingDelimiter && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                    qQuoteClosingDelimiter = null;
                    i++;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inStringLiteral) {
                if (ch == '\'' && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                    i++;
                } else if (ch == '\'') {
                    inStringLiteral = false;
                }
                continue;
            }
            if (OracleSqlText.startsOracleQQuote(line, i)) {
                int end = OracleSqlText.findQQuoteEnd(line, i);
                if (end >= 0) {
                    i = end;
                } else {
                    qQuoteClosingDelimiter = OracleSqlText.qQuoteClosingDelimiter(line.charAt(i + 2));
                    i = line.length();
                }
            } else if (ch == '\'') {
                inStringLiteral = true;
            } else if (ch == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-') {
                break;
            } else if (ch == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++;
            } else if (ch == ';') {
                indexes.add(i);
            }
        }
        return new CommentScan(indexes, inBlockComment, qQuoteClosingDelimiter, inStringLiteral);
    }

    private PlsqlLiteralScan scanPlsqlLiteralState(
            String line,
            Character qQuoteClosingDelimiter,
            boolean inStringLiteral,
            boolean inBlockComment) {
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inBlockComment) {
                if (ch == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (qQuoteClosingDelimiter != null) {
                if (ch == qQuoteClosingDelimiter && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                    qQuoteClosingDelimiter = null;
                    i++;
                }
                continue;
            }
            if (inStringLiteral) {
                if (ch == '\'' && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                    i++;
                } else if (ch == '\'') {
                    inStringLiteral = false;
                }
                continue;
            }
            if (ch == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-') {
                break;
            }
            if (ch == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (OracleSqlText.startsOracleQQuote(line, i)) {
                int end = OracleSqlText.findQQuoteEnd(line, i);
                if (end >= 0) {
                    i = end;
                } else {
                    qQuoteClosingDelimiter = OracleSqlText.qQuoteClosingDelimiter(line.charAt(i + 2));
                    i = line.length();
                }
            } else if (ch == '\'') {
                inStringLiteral = true;
            }
        }
        return new PlsqlLiteralScan(qQuoteClosingDelimiter, inStringLiteral, inBlockComment);
    }

    private void addStatement(
            List<ParsedStatement> statements,
            StringBuilder current,
            int startLine,
            int endLine,
            int startOffset,
            int endOffset) {
        String text = current.toString().strip();
        if (!text.isBlank()) {
            statements.add(new ParsedStatement(text, startLine, endLine, startOffset, endOffset));
        }
    }

    private record CommentScan(
            List<Integer> statementEndIndexes,
            boolean inBlockComment,
            Character qQuoteClosingDelimiter,
            boolean inStringLiteral) {
    }

    private record PlsqlLiteralScan(Character qQuoteClosingDelimiter, boolean inStringLiteral, boolean inBlockComment) {
    }
}
