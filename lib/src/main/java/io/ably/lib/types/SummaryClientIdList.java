package io.ably.lib.types;

import java.util.List;

public class SummaryClientIdList {
    public final int total; // TM7c1a
    public final List<String> clientIds; // TM7c1b

    public SummaryClientIdList(int total, List<String> clientIds) {
        this.total = total;
        this.clientIds = clientIds;
    }
}
