package io.ably.lib.types;

import java.io.UnsupportedEncodingException;

import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.util.Serialisation;

/**
 * StatsReader: internal
 * Utility class to convert response bodies in different formats to Stats data.
 */
public class StatsReader  {

	public static Stats[] readJSON(byte[] jsonBytes) throws AblyException {
		try {
			return readJSON(new String(jsonBytes, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	public static Stats[] readJSON(String packed) throws AblyException {
		return Serialisation.gson.fromJson(packed, Stats[].class);
	}

	public static BodyHandler<Stats> statsResponseHandler = new BodyHandler<Stats>() {
		@Override
		public Stats[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			if("application/json".equals(contentType))
				return readJSON(body);
			return null;
		}
	};
}
