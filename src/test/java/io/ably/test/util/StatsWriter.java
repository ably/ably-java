package io.ably.test.util;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.ably.http.Http;
import io.ably.http.Http.RequestBody;
import io.ably.types.AblyException;
import io.ably.types.Stats;
import io.ably.util.Serialisation;

public class StatsWriter {
	public static RequestBody asJSONRequest(Stats[] stats) throws AblyException {
		try {
			return new Http.JSONRequestBody(Serialisation.jsonObjectMapper.writeValueAsString(stats));
		} catch (JsonProcessingException e) {
			throw AblyException.fromThrowable(e);
		}
	}
}
