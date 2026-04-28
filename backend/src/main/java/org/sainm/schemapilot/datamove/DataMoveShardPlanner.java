package org.sainm.schemapilot.datamove;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataMoveShardPlanner {
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
            var predicate = quote(shardColumn) + " >= " + lower + " and " + quote(shardColumn) + " < " + upper;
            shards.add(new DataMoveShard(i, DataMoveShardStrategy.RANGE, predicate, lower, upper, 0, 0, DataMoveShardStatus.PENDING, "RANGE:" + i + ":0"));
        }
        return shards;
    }

    private List<DataMoveShard> hashShards(String shardColumn, int shardCount) {
        var shards = new ArrayList<DataMoveShard>();
        var buckets = Math.max(1, shardCount);
        for (int i = 0; i < buckets; i++) {
            var predicate = "ORA_HASH(" + quote(shardColumn) + ", " + (buckets - 1) + ") = " + i;
            shards.add(new DataMoveShard(i, DataMoveShardStrategy.HASH, predicate, null, null, 0, 0, DataMoveShardStatus.PENDING, "HASH:" + i + ":0"));
        }
        return shards;
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
