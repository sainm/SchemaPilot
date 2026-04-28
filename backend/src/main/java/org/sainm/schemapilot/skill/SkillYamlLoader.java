package org.sainm.schemapilot.skill;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
public class SkillYamlLoader {
    public SkillYamlSpec load(String classpathLocation) {
        var factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(classpathLocation));
        var properties = factory.getObject();
        if (properties == null) {
            throw new BadRequestException("Cannot load skill yaml: " + classpathLocation);
        }
        var schema = outputSchema(properties);
        var spec = new SkillYamlSpec(
                required(properties, "id"),
                required(properties, "version"),
                required(properties, "status"),
                required(properties, "description"),
                indexedList(properties, "allowedTools"),
                Boolean.parseBoolean(required(properties, "requiresReview")),
                schema
        );
        validate(spec);
        return spec;
    }

    public void validate(SkillYamlSpec spec) {
        if (spec.id().isBlank() || spec.version().isBlank() || spec.allowedTools().isEmpty() || spec.outputSchema().isEmpty()) {
            throw new BadRequestException("Skill yaml is missing required governance fields.");
        }
        for (var field : List.of("objectType", "objectName", "targetSql", "risks")) {
            if (!spec.outputSchema().containsKey(field)) {
                throw new BadRequestException("Skill output schema is missing required field: " + field);
            }
        }
    }

    private String required(Properties properties, String key) {
        var value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Skill yaml missing field: " + key);
        }
        return value;
    }

    private List<String> indexedList(Properties properties, String prefix) {
        return properties.stringPropertyNames().stream()
                .filter(name -> name.matches(prefix + "\\[\\d+]"))
                .sorted()
                .map(properties::getProperty)
                .toList();
    }

    private Map<String, String> outputSchema(Properties properties) {
        var schema = new LinkedHashMap<String, String>();
        properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith("outputSchema."))
                .sorted()
                .forEach(name -> schema.put(name.substring("outputSchema.".length()), properties.getProperty(name)));
        return Map.copyOf(schema);
    }
}
