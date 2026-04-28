package org.sainm.schemapilot.sql;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SqlStatementSplitter {
    List<String> split(String sql) {
        var statements = new ArrayList<String>();
        var current = new StringBuilder();

        for (String line : sql.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (isSlashTerminator(line) && isPlsqlBlock(current)) {
                addStatement(statements, current.toString());
                current.setLength(0);
                continue;
            }

            if (isPlsqlBlock(current) || startsPlsqlBlock(current, line)) {
                current.append(line).append('\n');
                continue;
            }

            appendRegularLine(statements, current, line);
        }

        addStatement(statements, current.toString());
        return statements;
    }

    private void appendRegularLine(List<String> statements, StringBuilder current, String line) {
        var segment = new StringBuilder();
        var inSingleQuote = false;
        var inDoubleQuote = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            segment.append(ch);

            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == ';' && !inSingleQuote && !inDoubleQuote) {
                current.append(segment);
                addStatement(statements, current.toString());
                current.setLength(0);
                segment.setLength(0);
            }
        }

        if (!segment.isEmpty()) {
            current.append(segment).append('\n');
        }
    }

    private boolean startsPlsqlBlock(StringBuilder current, String line) {
        if (!current.toString().isBlank()) {
            return false;
        }
        return isPlsqlStart(line);
    }

    private boolean isPlsqlBlock(StringBuilder current) {
        return isPlsqlStart(current.toString());
    }

    private boolean isPlsqlStart(String value) {
        var normalized = value.stripLeading().toLowerCase(Locale.ROOT);
        return normalized.startsWith("create or replace trigger")
                || normalized.startsWith("create trigger")
                || normalized.startsWith("create or replace function")
                || normalized.startsWith("create function")
                || normalized.startsWith("create or replace procedure")
                || normalized.startsWith("create procedure")
                || normalized.startsWith("create or replace package")
                || normalized.startsWith("create package")
                || normalized.startsWith("declare")
                || normalized.startsWith("begin");
    }

    private boolean isSlashTerminator(String line) {
        return "/".equals(line.strip());
    }

    private void addStatement(List<String> statements, String sql) {
        var normalized = sql.strip();
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1).strip();
        }
        if (!normalized.isEmpty()) {
            statements.add(normalized);
        }
    }
}
