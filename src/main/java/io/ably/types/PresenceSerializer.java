package io.ably.types;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import io.ably.http.Http.BodyHandler;
import io.ably.util.Serialisation;

/**
 * PresenceReader: internal
 * Utility class to convert response bodies in different formats to PresenceMessage
 * and PresenceMessage arrays.
 */
public class PresenceSerializer {

	public static PresenceMessage[] readJSON(String packed) throws AblyException {
		try {
			List<PresenceMessage> messages = Serialisation.jsonObjectMapper.readValue(packed, presenceTypeReference);
			return messages.toArray(new PresenceMessage[messages.size()]);
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static PresenceMessage[] readMsgpack(byte[] packed) throws AblyException {
		try {
			List<PresenceMessage> messages = Serialisation.msgpackObjectMapper.readValue(packed, presenceTypeReference);
			return messages.toArray(new PresenceMessage[messages.size()]);
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static PresenceMessage[] readJSON(byte[] jsonBytes) throws AblyException {
		return readJSON(new String(jsonBytes));
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
	private static final TypeReference<List<PresenceMessage>> presenceTypeReference = new TypeReference<List<PresenceMessage>>(){};
}
