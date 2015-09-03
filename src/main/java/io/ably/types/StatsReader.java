package io.ably.types;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.ably.http.Http.BodyHandler;

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
		try {
			List<Stats> stats = jsonObjectMapper.readValue(packed, typeReference);
			return stats.toArray(new Stats[stats.size()]);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static Stats[] readMsgpack(byte[] packed) throws AblyException {
		try {
			List<Stats> stats = msgpackObjectMapper.readValue(packed, typeReference);
			return stats.toArray(new Stats[stats.size()]);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static BodyHandler<Stats> statsResponseHandler = new BodyHandler<Stats>() {
		@Override
		public Stats[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			if("application/json".equals(contentType))
				return readJSON(body);
			if("application/x-msgpack".equals(contentType))
				return readMsgpack(body);
			return null;
		}
	};

	private static final TypeReference<List<Stats>> typeReference = new TypeReference<List<Stats>>(){};
	private static final ObjectMapper msgpackObjectMapper = new ObjectMapper(new MessagePackFactory());
	private static final ObjectMapper jsonObjectMapper = new ObjectMapper(new MessagePackFactory());
}
