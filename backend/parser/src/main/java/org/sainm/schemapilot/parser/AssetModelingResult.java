package org.sainm.schemapilot.parser;

import java.util.List;

import org.sainm.schemapilot.model.DbObject;
import org.sainm.schemapilot.model.ParseIssue;

public record AssetModelingResult(
        List<DbObject> objects,
        List<ParseIssue> parseIssues) {

    public AssetModelingResult {
        objects = List.copyOf(objects);
        parseIssues = List.copyOf(parseIssues);
    }
}
