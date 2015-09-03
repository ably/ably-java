package io.ably.types;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import io.ably.http.Http;
import io.ably.http.Http.BodyHandler;
import io.ably.http.Http.RequestBody;
import io.ably.util.Serialisation;

/**
 * MessageReader: internal
 * Utility class to convert response bodies in different formats to Message
 * and Message arrays.
 */
public class MessageSerializer {

	public static Message[] readJSON(String packed) throws AblyException {
		try {
			List<Message> messages = Serialisation.jsonObjectMapper.readValue(packed, messageTypeReference);
			return messages.toArray(new Message[messages.size()]);
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static Message[] readMsgpack(byte[] packed) throws AblyException {
		try {
			List<Message> messages = Serialisation.msgpackObjectMapper.readValue(packed, messageTypeReference);
			return messages.toArray(new Message[messages.size()]);
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static Message[] readJSON(byte[] jsonBytes) throws AblyException {
		return readJSON(new String(jsonBytes));
	}

	public static byte[] asMsgpack(Message message) throws AblyException {
		try {
			return Serialisation.msgpackObjectMapper.writeValueAsBytes(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static String asJSON(Message message) throws AblyException {
		try {
			return Serialisation.jsonObjectMapper.writeValueAsString(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static RequestBody asJSONRequest(Message message) throws AblyException {
		return asJSONRequest(new Message[] { message });
	}

	public static RequestBody asMsgpackRequest(Message message) throws AblyException {
		return asMsgpackRequest(new Message[] { message });
	}

	public static RequestBody asJSONRequest(Message[] messages) throws AblyException {
		try {
			return new Http.ByteArrayRequestBody(Serialisation.jsonObjectMapper.writeValueAsBytes(messages));
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static RequestBody asMsgpackRequest(Message[] messages) throws AblyException {
		try {
			return new Http.ByteArrayRequestBody(Serialisation.msgpackObjectMapper.writeValueAsBytes(messages));
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static BodyHandler<Message> getMessageResponseHandler(ChannelOptions opts) {
		return opts == null ? messageResponseHandler : new MessageBodyHandler(opts);
	}

	private static class MessageBodyHandler implements BodyHandler<Message> {

		public MessageBodyHandler(ChannelOptions opts) { this.opts = opts; }

		@Override
		public Message[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			Message[] messages = null;
			if("application/json".equals(contentType))
				messages = readJSON(body);
			else if("application/x-msgpack".equals(contentType))
				messages = readMsgpack(body);
			if(messages != null)
				for(Message message : messages)
					message.decode(opts);
			return messages;
		}

		private ChannelOptions opts;
	}

	private static BodyHandler<Message> messageResponseHandler = new MessageBodyHandler(null);
	private static final TypeReference<List<Message>> messageTypeReference = new TypeReference<List<Message>>(){};

}
