package io.ably.types;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import io.ably.http.Http.BodyHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.msgpack.MessagePack;
import org.msgpack.template.ListTemplate;
import org.msgpack.template.Template;
import org.msgpack.unpacker.Unpacker;

/**
 * PresenceReader: internal
 * Utility class to convert response bodies in different formats to PresenceMessage
 * and PresenceMessage arrays.
 */
public class PresenceSerializer {

	static PresenceMessage[] readMsgpack(Unpacker unpacker) throws IOException {
		List<PresenceMessage> msgList = unpacker.read(listTmpl);
		return msgList.toArray(new PresenceMessage[msgList.size()]);
	}

	public static PresenceMessage[] readMsgpack(byte[] packed) throws AblyException {
		try {
			Unpacker unpacker = msgpack.createUnpacker(new ByteArrayInputStream(packed));
			return readMsgpack(unpacker);
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static PresenceMessage[] readJSON(JSONArray json) {
		int count = json.length();
		PresenceMessage[] result = new PresenceMessage[count];
			for(int i = 0; i < count; i++)
				result[i] = PresenceMessage.fromJSON(json.optJSONObject(i));
			return result;
	}

	public static PresenceMessage[] readJSON(String jsonText) throws AblyException {
		try {
			return readJSON(new JSONArray(jsonText));
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	public static PresenceMessage[] readJSON(byte[] jsonBytes) throws AblyException {
		return readJSON(new String(jsonBytes));
	}

	public static JSONArray writeJSON(PresenceMessage[] messages) throws AblyException {
		JSONArray json;
		try {
			json = new JSONArray();
			for(int i = 0; i < messages.length; i++)
				json.put(i, messages[i].toJSON());

			return json;
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	public static BodyHandler<PresenceMessage> getPresenceResponseHandler(ChannelOptions opts) {
		return opts == null ? presenceResponseHandler : new PresenceBodyHandler(opts);
	}

	public static class PresenceBodyHandler implements BodyHandler<PresenceMessage> {
		public PresenceBodyHandler(ChannelOptions opts) { this.opts = opts; }

		@Override
		public PresenceMessage[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			PresenceMessage[] messages = null;
			if("application/json".equals(contentType))
				messages = readJSON(body);
			else if("application/x-msgpack".equals(contentType))
				messages = readMsgpack(body);
			if(messages != null)
				for(PresenceMessage message : messages)
					message.decode(opts);
			return messages;
		}

		private ChannelOptions opts;
	};

	private static PresenceBodyHandler presenceResponseHandler = new PresenceBodyHandler(null);
	private static final MessagePack msgpack = new MessagePack();
	private static final Template<List<PresenceMessage>> listTmpl = new ListTemplate<PresenceMessage>(msgpack.lookup(PresenceMessage.class));
}
