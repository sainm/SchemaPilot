package org.sainm.schemapilot.skill;

import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.model.SkillStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SkillRegistry {
    private final List<SkillDefinition> definitions = List.of(
            new SkillDefinition(
                    "oracle-table-ddl",
                    "1.0.0",
                    SkillStatus.ENABLED,
                    "Convert a single Oracle CREATE TABLE statement into PostgreSQL DDL draft.",
                    List.of("sql.analyze"),
                    false,
                    Map.of("objectType", "string", "objectName", "string", "targetSql", "string", "risks", "array")
            ),
            new SkillDefinition(
                    "oracle-view-sql",
                    "1.0.0",
                    SkillStatus.ENABLED,
                    "Convert a single Oracle CREATE VIEW statement into PostgreSQL SQL draft.",
                    List.of("sql.analyze", "knowledge.search"),
                    false,
                    Map.of("objectType", "string", "objectName", "string", "targetSql", "string", "risks", "array")
            ),
            new SkillDefinition(
                    "oracle-trigger-to-postgres",
                    "0.1.0",
                    SkillStatus.ENABLED,
                    "Generate a reviewed PostgreSQL trigger conversion draft from an Oracle trigger.",
                    List.of("sql.analyze", "knowledge.search"),
                    true,
                    Map.of("objectType", "string", "objectName", "string", "targetSql", "string", "risks", "array", "reviewRequired", "boolean")
            )
    );

    public List<SkillDefinition> definitions() {
        return definitions;
    }

    public SkillDefinition get(String skillId) {
        return definitions.stream()
                .filter(definition -> definition.id().equals(skillId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Skill not found: " + skillId));
    }
}
