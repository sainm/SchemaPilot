package org.sainm.schemapilot.model;

public enum DbObjectType {
    TABLE,
    COLUMN,
    PRIMARY_KEY,
    UNIQUE_CONSTRAINT,
    CHECK_CONSTRAINT,
    FOREIGN_KEY,
    INDEX,
    SEQUENCE,
    VIEW,
    TRIGGER,
    FUNCTION,
    PROCEDURE,
    PACKAGE,
    PACKAGE_BODY,
    SYNONYM,
    GRANT,
    COMMENT,
    PARTITION,
    UNKNOWN
}
