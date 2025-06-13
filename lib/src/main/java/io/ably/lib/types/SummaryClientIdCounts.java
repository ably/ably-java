package io.ably.lib.types;

import java.util.Map;

public class SummaryClientIdCounts {
    public final int total; // TM7d1a
    public final Map<String, Integer> clientIds; // TM7d1b

    public SummaryClientIdCounts(int total, Map<String, Integer> clientIds) {
        this.total = total;
        this.clientIds = clientIds;
    }
}
