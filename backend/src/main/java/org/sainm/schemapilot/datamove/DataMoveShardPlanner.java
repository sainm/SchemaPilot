package org.sainm.schemapilot.datamove;

import org.sainm.schemapilot.sql.SqlIdentifierValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataMoveShardPlanner {
    private final SqlIdentifierValidator identifierValidator;

    public DataMoveShardPlanner() {
        this(new SqlIdentifierValidator());
    }

    public DataMoveShardPlanner(SqlIdentifierValidator identifierValidator) {
        this.identifierValidator = identifierValidator;
    }

    public List<DataMoveShard> plan(DataMoveRequest request, DataMoveTableProfile profile) {
        if (!profile.largeTable()) {
            return List.of(DataMoveShard.single());
        }
        var shardSize = Math.max(1, request.shardSize());
        var shardCount = Math.max(1, (int) Math.ceil(profile.estimatedRows() / (double) shardSize));
        if (profile.numericBounds().known()) {
            return rangeShards(profile.shardColumn(), profile.numericBounds(), shardCount);
        }
        return hashShards(profile.shardColumn(), shardCount);
    }

    private List<DataMoveShard> rangeShards(String shardColumn, NumericBounds bounds, int shardCount) {
        var shards = new ArrayList<DataMoveShard>();
        var min = bounds.minValue();
        var max = bounds.maxValue();
        var width = Math.max(1, (long) Math.ceil((max - min + 1) / (double) shardCount));
        for (int i = 0; i < shardCount; i++) {
            var lower = min + width * i;
            var upper = i == shardCount - 1 ? max + 1 : Math.min(max + 1, lower + width);
            var predicate = identifierValidator.quote(shardColumn) + " >= " + lower + " and " + identifierValidator.quote(shardColumn) + " < " + upper;
            shards.add(new DataMoveShard(i, DataMoveShardStrategy.RANGE, predicate, lower, upper, 0, 0, DataMoveShardStatus.PENDING, "RANGE:" + i + ":0"));
        }
        return shards;
    }

    private List<DataMoveShard> hashShards(String shardColumn, int shardCount) {
        var shards = new ArrayList<DataMoveShard>();
        var buckets = Math.max(1, shardCount);
        for (int i = 0; i < buckets; i++) {
            var predicate = "ORA_HASH(" + identifierValidator.quote(shardColumn) + ", " + (buckets - 1) + ") = " + i;
            shards.add(new DataMoveShard(i, DataMoveShardStrategy.HASH, predicate, null, null, 0, 0, DataMoveShardStatus.PENDING, "HASH:" + i + ":0"));
        }
        return shards;
    }

}
