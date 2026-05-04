package org.sainm.schemapilot.dependency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sainm.schemapilot.common.sql.OracleSqlText;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.DbObjectType;

public class DependencyGraphService {

    private static final String SQL_IDENTIFIER = "(?:\"(?:\"\"|[^\"])+\"|[\\w$#]+)";
    private static final Pattern TRIGGER_TABLE = Pattern.compile(
            "\\bon\\s+(" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCES_TABLE = Pattern.compile(
            "\\breferences\\s+(" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SEQUENCE_NEXTVAL = Pattern.compile(
            "(?<![\\w$#])(" + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*)\\s*\\.\\s*nextval\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX_TABLE = Pattern.compile(
            "\\bcreate\\s+(?:unique\\s+|bitmap\\s+)?index\\s+"
                    + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")?\\s+on\\s+("
                    + SQL_IDENTIFIER + "(?:\\s*\\.\\s*" + SQL_IDENTIFIER + ")*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern REFERENCING_OBJECT_DECLARATION = Pattern.compile(
            "^\\s*create\\s+(?:or\\s+replace\\s+)?(?:no\\s+force\\s+|force\\s+)?"
                    + "(?:editionable\\s+|noneditionable\\s+)?(?:view|function|procedure)\\s+",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final DependencyRepository dependencyRepository;

    public DependencyGraphService(DependencyRepository dependencyRepository) {
        this.dependencyRepository = dependencyRepository;
    }

    public List<ObjectDependency> rebuildProjectDependencies(UUID projectId, List<DbObject> objects) {
        Map<String, List<DbObject>> byName = new LinkedHashMap<>();
        for (DbObject object : objects) {
            putName(byName, object.name(), object);
            if (object.schema() != null && !object.schema().isBlank()) {
                putName(byName, object.schema() + "." + object.name(), object);
            }
        }

        List<ObjectDependency> dependencies = new ArrayList<>();
        for (DbObject source : objects) {
            switch (source.type()) {
                case TABLE -> addTableDependencies(source, byName, dependencies);
                case INDEX -> addIndexDependency(source, byName, dependencies);
                case VIEW -> addNameReferences(source, byName, DependencyType.VIEW_REFERENCES_TABLE, dependencies);
                case FUNCTION, PROCEDURE -> addNameReferences(source, byName, DependencyType.ROUTINE_REFERENCES_OBJECT, dependencies);
                case TRIGGER -> addTriggerDependency(source, byName, dependencies);
                case PACKAGE_BODY -> addPackageBodyDependency(source, byName, dependencies);
                default -> {
                }
            }
        }
        dependencyRepository.replaceProjectDependencies(projectId, dependencies);
        return List.copyOf(dependencies);
    }

    private void addTableDependencies(
            DbObject source,
            Map<String, List<DbObject>> byName,
            List<ObjectDependency> dependencies) {
        addTableForeignKeyDependencies(source, byName, dependencies);
        addTableSequenceDependencies(source, byName, dependencies);
    }

    private void addTableForeignKeyDependencies(
            DbObject source,
            Map<String, List<DbObject>> byName,
            List<ObjectDependency> dependencies) {
        Matcher matcher = REFERENCES_TABLE.matcher(stripStringLiteralsAndComments(source.originalSql()));
        Set<UUID> emittedTargets = new LinkedHashSet<>();
        while (matcher.find()) {
            DbObject target = resolveReference(source, byName, unquote(matcher.group(1)), DbObjectType.TABLE);
            if (target != null && !source.id().equals(target.id()) && emittedTargets.add(target.id())) {
                dependencies.add(createDependency(source, target, effectiveType(source, target, DependencyType.TABLE_REFERENCES_TABLE), matcher.group()));
            }
        }
    }

    private void addTableSequenceDependencies(
            DbObject source,
            Map<String, List<DbObject>> byName,
            List<ObjectDependency> dependencies) {
        Matcher matcher = SEQUENCE_NEXTVAL.matcher(stripStringLiteralsAndComments(source.originalSql()));
        Set<UUID> emittedTargets = new LinkedHashSet<>();
        while (matcher.find()) {
            DbObject target = resolveReference(source, byName, unquote(matcher.group(1)), DbObjectType.SEQUENCE);
            if (target != null && !source.id().equals(target.id()) && emittedTargets.add(target.id())) {
                dependencies.add(createDependency(source, target, effectiveType(source, target, DependencyType.TABLE_USES_SEQUENCE), matcher.group()));
            }
        }
    }

    private void addIndexDependency(DbObject source, Map<String, List<DbObject>> byName, List<ObjectDependency> dependencies) {
        Matcher matcher = INDEX_TABLE.matcher(stripStringLiteralsAndComments(source.originalSql()));
        if (!matcher.find()) {
            return;
        }
        DbObject target = resolveReference(source, byName, unquote(matcher.group(1)), DbObjectType.TABLE);
        if (target != null) {
            dependencies.add(createDependency(source, target, effectiveType(source, target, DependencyType.INDEX_ON_TABLE), matcher.group()));
        }
    }

    private void addNameReferences(
            DbObject source,
            Map<String, List<DbObject>> byName,
            DependencyType type,
            List<ObjectDependency> dependencies) {
        String searchableSql = stripReferencingObjectDeclaration(source, stripStringLiteralsAndComments(source.originalSql()))
                .toLowerCase(Locale.ROOT);
        List<DbObject> matchingTargets = uniqueObjects(byName).stream()
                .filter(target -> !source.id().equals(target.id()))
                .filter(target -> containsObjectReference(searchableSql, target))
                .toList();
        for (DbObject target : preferSameSourceTargets(source, matchingTargets)) {
            dependencies.add(createDependency(source, target, effectiveType(source, target, type), target.name()));
        }
    }

    private void addTriggerDependency(DbObject source, Map<String, List<DbObject>> byName, List<ObjectDependency> dependencies) {
        Matcher matcher = TRIGGER_TABLE.matcher(stripStringLiteralsAndComments(source.originalSql()));
        if (!matcher.find()) {
            return;
        }
        DbObject target = resolveReference(source, byName, unquote(matcher.group(1)));
        if (target != null) {
            dependencies.add(createDependency(source, target, effectiveType(source, target, DependencyType.TRIGGER_ON_TABLE), matcher.group()));
        }
    }

    private void addPackageBodyDependency(DbObject source, Map<String, List<DbObject>> byName, List<ObjectDependency> dependencies) {
        String packageName = source.schema() == null || source.schema().isBlank()
                ? source.name()
                : source.schema() + "." + source.name();
        DbObject packageSpec = resolveReference(source, byName, packageName);
        if (packageSpec != null && packageSpec.type() == DbObjectType.PACKAGE && !source.id().equals(packageSpec.id())) {
            dependencies.add(createDependency(source, packageSpec, effectiveType(source, packageSpec, DependencyType.PACKAGE_BODY_FOR_SPEC), source.name()));
        }
    }

    private void putName(Map<String, List<DbObject>> byName, String name, DbObject object) {
        byName.computeIfAbsent(normalizeName(name), ignored -> new ArrayList<>()).add(object);
    }

    private Set<DbObject> uniqueObjects(Map<String, List<DbObject>> byName) {
        Set<DbObject> objects = new LinkedHashSet<>();
        byName.values().forEach(objects::addAll);
        return objects;
    }

    private List<DbObject> preferSameSourceTargets(DbObject source, List<DbObject> matchingTargets) {
        Map<String, List<DbObject>> byReferenceName = new LinkedHashMap<>();
        for (DbObject target : matchingTargets) {
            byReferenceName.computeIfAbsent(normalizeName(target.name()), ignored -> new ArrayList<>()).add(target);
        }
        List<DbObject> scopedTargets = new ArrayList<>();
        for (List<DbObject> candidates : byReferenceName.values()) {
            List<DbObject> sameSource = candidates.stream()
                    .filter(candidate -> candidate.sourceProjectId().equals(source.sourceProjectId()))
                    .toList();
            scopedTargets.addAll(sameSource.isEmpty() ? candidates : sameSource);
        }
        return scopedTargets;
    }

    private DbObject resolveReference(DbObject source, Map<String, List<DbObject>> byName, String name) {
        return resolveReference(source, byName, name, null);
    }

    private DbObject resolveReference(DbObject source, Map<String, List<DbObject>> byName, String name, DbObjectType expectedType) {
        List<DbObject> candidates = byName.getOrDefault(normalizeName(name), List.of());
        return candidates.stream()
                .filter(candidate -> expectedType == null || candidate.type() == expectedType)
                .filter(candidate -> candidate.sourceProjectId().equals(source.sourceProjectId()))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(candidate -> expectedType == null || candidate.type() == expectedType)
                        .findFirst())
                .orElse(null);
    }

    private DependencyType effectiveType(DbObject source, DbObject target, DependencyType type) {
        if (!source.sourceProjectId().equals(target.sourceProjectId())) {
            return DependencyType.CROSS_SOURCE_DEPENDENCY;
        }
        return type;
    }

    private ObjectDependency createDependency(DbObject source, DbObject target, DependencyType type, String evidence) {
        return new ObjectDependency(
                UUID.randomUUID(),
                source.projectId(),
                source.sourceProjectId(),
                source.id(),
                target.id(),
                type,
                evidence);
    }

    private String normalizeName(String name) {
        return unquoteQualifiedIdentifier(name).toLowerCase(Locale.ROOT);
    }

    private boolean containsObjectReference(String searchableSql, DbObject target) {
        String targetName = normalizeName(target.name());
        boolean qualifiedReference = target.schema() != null
                && !target.schema().isBlank()
                && matchesIdentifier(searchableSql, normalizeName(target.schema() + "." + target.name()));
        if (qualifiedReference) {
            return true;
        }
        return matchesUnqualifiedIdentifier(searchableSql, targetName);
    }

    private boolean matchesIdentifier(String searchableSql, String normalizedName) {
        String pattern = "(?<![\\w$#])" + identifierReferencePattern(normalizedName) + "(?![\\w$#])";
        return Pattern.compile(pattern).matcher(searchableSql).find();
    }

    private boolean matchesUnqualifiedIdentifier(String searchableSql, String targetName) {
        String pattern = "(?<![\\w$#\\.\"])" + identifierPartPattern(targetName) + "(?![\\w$#\"])";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(searchableSql).find();
    }

    private String identifierReferencePattern(String normalizedName) {
        String[] parts = normalizedName.split("\\.", -1);
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                pattern.append("\\s*\\.\\s*");
            }
            pattern.append(identifierPartPattern(parts[i]));
        }
        return pattern.toString();
    }

    private String identifierPartPattern(String part) {
        String quotedPart = part.replace("\"", "\"\"");
        return "(?:" + Pattern.quote(part) + "|\"" + Pattern.quote(quotedPart) + "\")";
    }

    private String stripStringLiteralsAndComments(String sql) {
        return OracleSqlText.stripStringLiteralsAndComments(sql);
    }

    private String stripReferencingObjectDeclaration(DbObject source, String sql) {
        if (source.type() != DbObjectType.VIEW && source.type() != DbObjectType.FUNCTION && source.type() != DbObjectType.PROCEDURE) {
            return sql;
        }
        Matcher matcher = REFERENCING_OBJECT_DECLARATION.matcher(sql);
        if (!matcher.find()) {
            return sql;
        }
        int identifierStart = matcher.end();
        int identifierEnd = readQualifiedIdentifierEnd(sql, identifierStart);
        if (identifierEnd <= identifierStart) {
            return sql;
        }
        return sql.substring(0, identifierStart)
                + " ".repeat(identifierEnd - identifierStart)
                + sql.substring(identifierEnd);
    }

    private int readQualifiedIdentifierEnd(String sql, int start) {
        boolean quoted = false;
        for (int i = start; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (!quoted && (Character.isWhitespace(ch) || ch == '(' || ch == ';')) {
                return i;
            }
        }
        return sql.length();
    }

    private String unquote(String name) {
        return unquoteQualifiedIdentifier(name);
    }

    private String unquoteQualifiedIdentifier(String name) {
        String trimmed = name.trim();
        List<String> parts = splitQualifiedIdentifier(trimmed);
        if (parts.size() == 1) {
            return unquoteIdentifier(parts.getFirst());
        }
        return parts.stream()
                .map(this::unquoteIdentifier)
                .reduce((left, right) -> left + "." + right)
                .orElse("");
    }

    private List<String> splitQualifiedIdentifier(String name) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '"') {
                current.append(ch);
                if (quoted && i + 1 < name.length() && name.charAt(i + 1) == '"') {
                    current.append(name.charAt(i + 1));
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

    private String unquoteIdentifier(String name) {
        String trimmed = name.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
        }
        return trimmed;
    }
}
