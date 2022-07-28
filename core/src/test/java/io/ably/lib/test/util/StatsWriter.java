package io.ably.lib.test.util;

import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Stats;
import io.ably.lib.util.Serialisation;

public class StatsWriter {
    public static HttpCore.RequestBody asJsonRequest(Stats[] stats) throws AblyException {
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(stats));
    }
}
