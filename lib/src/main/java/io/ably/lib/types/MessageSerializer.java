package io.ably.lib.types;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.ably.lib.http.Http;
import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.http.Http.RequestBody;
import io.ably.lib.util.Serialisation;

/**
 * MessageReader: internal
 * Utility class to convert response bodies in different formats to Message
 * and Message arrays.
 */
public class MessageSerializer extends JsonSerializer<Message> {

	public static Message[] readJSON(byte[] packed) throws AblyException {
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

	public static RequestBody asJSONRequest(Message message) throws AblyException {
		return asJSONRequest(new Message[] { message });
	}

	public static RequestBody asMsgpackRequest(Message message) throws AblyException {
		return asMsgpackRequest(new Message[] { message });
	}

	public static RequestBody asJSONRequest(Message[] messages) throws AblyException {
		try {
			return new Http.ByteArrayRequestBody(Serialisation.jsonObjectMapper.writeValueAsBytes(messages), "application/json");
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static RequestBody asMsgpackRequest(Message[] messages) throws AblyException {
		try {
			return new Http.ByteArrayRequestBody(Serialisation.msgpackObjectMapper.writeValueAsBytes(messages), "application/x-msgpack");
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

	@Override
	public void serialize(Message msg, JsonGenerator generator, SerializerProvider provider)
			throws IOException, JsonProcessingException {

		generator.writeStartObject();
		msg.serializeFields(generator);
		generator.writeEndObject();
	}

	static {
		SimpleModule messageModule = new SimpleModule("Message", new Version(1, 0, 0, null, null, null));
		messageModule.addSerializer(Message.class, new MessageSerializer());
		Serialisation.jsonObjectMapper.registerModule(messageModule);
		Serialisation.msgpackObjectMapper.registerModule(messageModule);
	}
}
