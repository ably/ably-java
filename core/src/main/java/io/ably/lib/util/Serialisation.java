package io.ably.lib.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import io.ably.lib.types.Message;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.ProtocolMessage;

public class Serialisation {
	public static final JsonParser gsonParser;
	public static final GsonBuilder gsonBuilder;
	public static final Gson gson;

	static {
		gsonParser = new JsonParser();
		gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(Message.class, new Message.Serializer());
		gsonBuilder.registerTypeAdapter(PresenceMessage.class, new PresenceMessage.Serializer());
		gsonBuilder.registerTypeAdapter(PresenceMessage.Action.class, new PresenceMessage.ActionSerializer());
		gsonBuilder.registerTypeAdapter(ProtocolMessage.Action.class, new ProtocolMessage.ActionSerializer());
		gson = gsonBuilder.create();
	}
}
