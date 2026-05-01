package org.sainm.schemapilot.sql;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SqlIdentifierValidator {
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_$#]*");
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1F\\x7F]");

    public String quote(String identifier) {
        var segment = unquote(identifier);
        validateSegment(segment, identifier);
        return "\"" + segment.replace("\"", "\"\"") + "\"";
    }

    public String quoteQualified(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("SQL identifier must not be blank.");
        }
        var parts = identifier.strip().split("\\.");
        if (parts.length > 3) {
            throw new IllegalArgumentException("SQL identifier has too many qualified parts: " + identifier);
        }
        return Arrays.stream(parts)
                .map(this::quote)
                .collect(Collectors.joining("."));
    }

    private String unquote(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("SQL identifier must not be null.");
        }
        var value = identifier.strip();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }

    private void validateSegment(String segment, String original) {
        if (segment.isBlank()) {
            throw new IllegalArgumentException("SQL identifier segment must not be blank.");
        }
        if (CONTROL.matcher(segment).find() || segment.contains(";") || segment.contains("--") || segment.contains("/*") || segment.contains("*/")) {
            throw new IllegalArgumentException("SQL identifier contains unsafe characters: " + original);
        }
        if (!SAFE_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException("SQL identifier is not allowlisted: " + original);
        }
    }
}
