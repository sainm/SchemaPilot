package org.sainm.schemapilot.ai;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.convert.ConversionResult;
import org.sainm.schemapilot.dependency.ObjectDependency;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.risk.ObjectRiskIssue;

public record AiContext(
        UUID projectId,
        List<DbObject> objects,
        List<ObjectDependency> dependencies,
        List<ObjectRiskIssue> risks,
        List<ConversionResult> conversions) {

    public AiContext {
        objects = List.copyOf(objects);
        dependencies = List.copyOf(dependencies);
        risks = List.copyOf(risks);
        conversions = List.copyOf(conversions);
    }
}
