package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ObjectType;

record ClassifiedStatement(
        ObjectType objectType,
        String objectName
) {
}
