package org.sainm.schemapilot.common.sql;

public final class OracleSqlText {

    private OracleSqlText() {
    }

    public static boolean startsOracleQQuote(CharSequence text, int index) {
        return index + 2 < text.length()
                && (text.charAt(index) == 'q' || text.charAt(index) == 'Q')
                && text.charAt(index + 1) == '\'';
    }

    public static char qQuoteClosingDelimiter(char delimiter) {
        return switch (delimiter) {
            case '[' -> ']';
            case '(' -> ')';
            case '{' -> '}';
            case '<' -> '>';
            default -> delimiter;
        };
    }

    public static int findQQuoteEnd(CharSequence text, int start) {
        char closingDelimiter = qQuoteClosingDelimiter(text.charAt(start + 2));
        for (int i = start + 3; i + 1 < text.length(); i++) {
            if (text.charAt(i) == closingDelimiter && text.charAt(i + 1) == '\'') {
                return i + 1;
            }
        }
        return -1;
    }

    public static int skipQQuotedLiteral(CharSequence text, int start) {
        int end = findQQuoteEnd(text, start);
        return end >= 0 ? end : text.length() - 1;
    }

    public static int skipStringLiteral(CharSequence text, int start) {
        for (int i = start + 1; i < text.length(); i++) {
            if (text.charAt(i) == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                i++;
            } else if (text.charAt(i) == '\'') {
                return i;
            }
        }
        return text.length() - 1;
    }

    public static String stripStringLiteralsAndComments(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (startsOracleQQuote(sql, i)) {
                int end = skipQQuotedLiteral(sql, i);
                appendBlanked(result, sql, i, end);
                i = end;
            } else if (ch == '\'') {
                int end = skipStringLiteral(sql, i);
                appendBlanked(result, sql, i, end);
                i = end;
            } else if (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int end = skipLineComment(sql, i + 2);
                appendBlanked(result, sql, i, end);
                i = end;
            } else if (ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = skipBlockComment(sql, i + 2);
                appendBlanked(result, sql, i, end);
                i = end;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    public static String stripComments(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (startsOracleQQuote(sql, i)) {
                int end = skipQQuotedLiteral(sql, i);
                result.append(sql, i, end + 1);
                i = end;
            } else if (ch == '\'') {
                int end = skipStringLiteral(sql, i);
                result.append(sql, i, end + 1);
                i = end;
            } else if (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int end = skipLineComment(sql, i + 2);
                appendBlanked(result, sql, i, end);
                i = end;
            } else if (ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = skipBlockComment(sql, i + 2);
                appendBlanked(result, sql, i, end);
                i = end;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static int skipLineComment(CharSequence text, int start) {
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                return i;
            }
        }
        return text.length() - 1;
    }

    private static int skipBlockComment(CharSequence text, int start) {
        for (int i = start; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                return i + 1;
            }
        }
        return text.length() - 1;
    }

    private static void appendBlanked(StringBuilder result, CharSequence text, int start, int endInclusive) {
        for (int i = start; i <= endInclusive; i++) {
            char ch = text.charAt(i);
            result.append(Character.isWhitespace(ch) ? ch : ' ');
        }
    }
}
