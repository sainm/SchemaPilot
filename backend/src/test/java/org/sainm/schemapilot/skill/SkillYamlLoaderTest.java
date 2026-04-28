package org.sainm.schemapilot.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillYamlLoaderTest {
    private final SkillYamlLoader loader = new SkillYamlLoader();

    @Test
    void loadsSkillYamlAndValidatesOutputSchema() {
        var spec = loader.load("skills/oracle-trigger-to-postgres.skill.yaml");

        assertThat(spec.id()).isEqualTo("oracle-trigger-to-postgres");
        assertThat(spec.version()).isEqualTo("0.1.0");
        assertThat(spec.allowedTools()).containsExactly("sql.analyze", "knowledge.search");
        assertThat(spec.requiresReview()).isTrue();
        assertThat(spec.outputSchema()).containsKeys("objectType", "objectName", "targetSql", "risks", "reviewRequired");
    }
}
