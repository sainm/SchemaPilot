package org.sainm.schemapilot.ai;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.convert.ConversionResult;
import org.sainm.schemapilot.dependency.ObjectDependency;
import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.risk.ObjectRiskIssue;

public class AiContextBuilder {

    public AiContext build(
            UUID projectId,
            List<DbObject> objects,
            List<ObjectDependency> dependencies,
            List<ObjectRiskIssue> risks,
            List<ConversionResult> conversions) {
        return new AiContext(projectId, objects, dependencies, risks, conversions);
    }
}
