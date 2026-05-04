package org.sainm.schemapilot.convert;

import org.sainm.schemapilot.model.DbObject;

public interface ObjectConverter {

    ConversionResult convert(DbObject object, ConversionContext context);
}
