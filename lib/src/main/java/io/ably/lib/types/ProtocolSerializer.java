package io.ably.lib.types;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.ably.lib.types.ProtocolMessage.Action;
import io.ably.lib.util.Serialisation;

public class ProtocolSerializer {

	public static ProtocolMessage fromJSON(String packed) throws AblyException {
		try {
			return Serialisation.jsonObjectMapper.readValue(packed, ProtocolMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static ProtocolMessage fromMsgpack(byte[] packed) throws AblyException {
		try {
			return Serialisation.msgpackObjectMapper.readValue(packed, ProtocolMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static byte[] asJSON(ProtocolMessage message) throws AblyException {
		try {
			return Serialisation.jsonObjectMapper.writeValueAsBytes(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static byte[] asMsgpack(ProtocolMessage message) throws AblyException {
		try {
			return Serialisation.msgpackObjectMapper.writeValueAsBytes(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	/********************************************************
	 * Serializer and Deserializer for ProtocolMessage.Action
	 ********************************************************/

	private static class Serializer extends JsonSerializer<Action> {
		@Override
		public void serialize(Action action, JsonGenerator generator, SerializerProvider arg2)
				throws IOException, JsonProcessingException {
	
			generator.writeNumber(action.getValue());
		}
	}

	private static class Deserializer extends JsonDeserializer<Action> {
		@Override
		public Action deserialize(JsonParser parser, DeserializationContext deserContext)
				throws IOException, JsonProcessingException {
	
			return Action.findByValue(parser.getIntValue());
		}
	}

	static {
		SimpleModule protocolModule = new SimpleModule("ProtocolMessage", new Version(1, 0, 0, null, null, null));
		protocolModule.addSerializer(Action.class, new Serializer());
		protocolModule.addDeserializer(Action.class, new Deserializer());
		Serialisation.jsonObjectMapper.registerModule(protocolModule);
		Serialisation.msgpackObjectMapper.registerModule(protocolModule);
	}
}
