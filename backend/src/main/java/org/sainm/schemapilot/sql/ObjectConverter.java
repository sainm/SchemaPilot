package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.ObjectType;

interface ObjectConverter {
    boolean supports(ObjectType objectType);

    ConversionDraft convert(ConversionContext context);
}
