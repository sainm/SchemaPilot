package org.sainm.schemapilot.convert;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OracleTypeMapper {

    private static final Pattern PARAMETERIZED_TYPE = Pattern.compile("^(\\w+)\\s*\\(([^)]*)\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARACTER_LENGTH = Pattern.compile("^(\\d+)\\s*(CHAR|BYTE)?$", Pattern.CASE_INSENSITIVE);

    public TypeMappingResult map(String sourceType) {
        String normalized = sourceType.trim();
        Matcher matcher = PARAMETERIZED_TYPE.matcher(normalized);
        if (matcher.matches()) {
            return mapParameterized(normalized, matcher.group(1).toUpperCase(Locale.ROOT), matcher.group(2).trim());
        }
        return mapSimple(normalized, normalized.toUpperCase(Locale.ROOT));
    }

    private TypeMappingResult mapParameterized(String sourceType, String baseType, String parameters) {
        return switch (baseType) {
            case "VARCHAR2", "NVARCHAR2" -> mapCharacter(sourceType, parameters);
            case "NUMBER" -> new TypeMappingResult(
                    sourceType,
                    "numeric(" + parameters.replaceAll("\\s+", "") + ")",
                    false,
                    List.of("Do not narrow NUMBER(p,0) to integer or bigint without value profile evidence."));
            case "RAW" -> new TypeMappingResult(sourceType, "bytea", false, List.of("Verify RAW length constraints separately."));
            default -> new TypeMappingResult(sourceType, sourceType, true, List.of("No automatic mapping is defined."));
        };
    }

    private TypeMappingResult mapCharacter(String sourceType, String parameters) {
        Matcher matcher = CHARACTER_LENGTH.matcher(parameters.replaceAll("\\s+", " "));
        if (!matcher.matches()) {
            return new TypeMappingResult(sourceType, sourceType, true, List.of("Character length could not be mapped automatically."));
        }
        String length = matcher.group(1);
        String semantics = matcher.group(2);
        if (semantics == null) {
            return new TypeMappingResult(sourceType, "varchar(" + length + ")", false, List.of());
        }
        return new TypeMappingResult(
                sourceType,
                "varchar(" + length + ")",
                true,
                List.of("Review Oracle " + semantics.toUpperCase(Locale.ROOT) + " length semantics against PostgreSQL character length semantics."));
    }

    private TypeMappingResult mapSimple(String sourceType, String normalized) {
        return switch (normalized) {
            case "NUMBER" -> new TypeMappingResult(sourceType, "numeric", true, List.of("NUMBER without precision needs review."));
            case "DATE" -> new TypeMappingResult(sourceType, "timestamp", true, List.of("Oracle DATE includes time; confirm timezone and application semantics."));
            case "TIMESTAMP" -> new TypeMappingResult(sourceType, "timestamp", false, List.of());
            case "CLOB", "NCLOB" -> new TypeMappingResult(sourceType, "text", false, List.of());
            case "BLOB" -> new TypeMappingResult(sourceType, "bytea", false, List.of());
            default -> new TypeMappingResult(sourceType, sourceType, true, List.of("No automatic mapping is defined."));
        };
    }
}
