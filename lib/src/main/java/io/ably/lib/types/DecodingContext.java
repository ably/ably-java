package io.ably.lib.types;

import java.util.Map;
import java.nio.charset.StandardCharsets;

public class DecodingContext {

	private String lastMessageString;
	private byte[] lastMessageBinary;

	public DecodingContext(Map<PluginType, Plugin> plugins)
	{
		lastMessageBinary = null;
		lastMessageString = null;
		this.plugins = plugins;
	}

	private final Map<PluginType, Plugin> plugins;

	public Plugin getDecoderPlugin(PluginType type)
	{
		return plugins.get(type);
	}

	public byte[] getLastMessageData() {
		if(lastMessageBinary != null)
			return lastMessageBinary;
		else if(lastMessageString != null) {
			return lastMessageString.getBytes(StandardCharsets.UTF_8);
		}
		else
			return null;
	}

	public void setLastMessageData(String message) {
		lastMessageString = message;
		lastMessageBinary = null;
	}

	public void setLastMessageData(byte[] message) {
		lastMessageBinary = message;
		lastMessageString = null;
	}
}
