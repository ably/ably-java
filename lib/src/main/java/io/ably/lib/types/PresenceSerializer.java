package io.ably.lib.types;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.types.PresenceMessage.Action;
import io.ably.lib.util.Serialisation;

/**
 * PresenceSerializer: internal
 * Utility class to convert response bodies in different formats to PresenceMessage
 * and PresenceMessage arrays.
 */
public class PresenceSerializer extends JsonSerializer<PresenceMessage> {

	public static PresenceMessage[] readJSON(byte[] packed) throws AblyException {
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

	/*******************************
	 * Serializer and Deserializer
	 *******************************/

	private static class ActionDeserializer extends JsonDeserializer<Action> {
		@Override
		public Action deserialize(JsonParser parser, DeserializationContext ctx) throws IOException, JsonProcessingException {
			return Action.findByValue(parser.getIntValue());
		}
	}

	@Override
	public void serialize(PresenceMessage msg, JsonGenerator generator, SerializerProvider provider) throws IOException, JsonProcessingException {
		generator.writeStartObject();
		msg.serializeFields(generator);
		generator.writeEndObject();
	}

	static {
		SimpleModule presenceModule = new SimpleModule("PresenceMessage", new Version(1, 0, 0, null, null, null));
		presenceModule.addSerializer(PresenceMessage.class, new PresenceSerializer());
		presenceModule.addDeserializer(Action.class, new ActionDeserializer());
		Serialisation.jsonObjectMapper.registerModule(presenceModule);
		Serialisation.msgpackObjectMapper.registerModule(presenceModule);
	}

}
