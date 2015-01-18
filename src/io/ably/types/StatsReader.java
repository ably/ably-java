package io.ably.types;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.msgpack.MessagePack;
import org.msgpack.template.ListTemplate;
import org.msgpack.template.Template;
import org.msgpack.unpacker.Unpacker;

import io.ably.http.Http.BodyHandler;

/**
 * StatsReader: internal
 * Utility class to convert response bodies in different formats to Stats data.
 */
public class StatsReader  {

	public static Stats[] readJSON(String jsonText) throws AblyException {
		try {
			JSONArray json = new JSONArray(jsonText);
			int count = json.length();
			Stats[] result = new Stats[count];
			for(int i = 0; i < count; i++)
				result[i] = Stats.fromJSON(json.optJSONObject(i));

			return result;
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}
	public static Stats[] readJSON(byte[] jsonBytes) throws AblyException {
		try {
			return readJSON(new String(jsonBytes, "UTF-8"));
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		} catch (UnsupportedEncodingException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	public static Stats[] readMsgpack(byte[] packed) throws AblyException {
		try {
			Unpacker unpacker = msgpack.createUnpacker(new ByteArrayInputStream(packed));
			List<Stats> stats = unpacker.read(listTmpl);
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

	private static final MessagePack msgpack = new MessagePack();
	private static final Template<List<Stats>> listTmpl = new ListTemplate<Stats>(msgpack.lookup(Stats.class));
}
