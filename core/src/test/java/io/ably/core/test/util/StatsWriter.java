package io.ably.core.test.util;

import io.ably.core.http.HttpCore;
import io.ably.core.http.HttpUtils;
import io.ably.core.types.AblyException;
import io.ably.core.types.Stats;
import io.ably.core.util.Serialisation;

public class StatsWriter {
    public static HttpCore.RequestBody asJsonRequest(Stats[] stats) throws AblyException {
        return new HttpUtils.JsonRequestBody(Serialisation.gson.toJson(stats));
    }
}
