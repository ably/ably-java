package io.ably.lib.test.util;

import io.ably.lib.http.Http;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Stats;
import io.ably.lib.util.Serialisation;

public class StatsWriter {
	public static RequestBody asJSONRequest(Stats[] stats) throws AblyException {
		return new Http.JSONRequestBody(Serialisation.gson.toJson(stats));
	}
}
