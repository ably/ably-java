package io.ably.lib.types;

import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

public class DecodingContext {

	private String lastMessageString;
	private byte[] lastMessageBinary;

	public DecodingContext(Map<String, PluggableCodec> codecs)
	{
		lastMessageBinary = null;
		lastMessageString = null;
		this.codecs = codecs;
	}

	public Map<String, PluggableCodec> codecs;

	public byte[] get_lastMessage() {
		if(lastMessageBinary != null)
			return lastMessageBinary;
		else if(lastMessageString != null) {
			return lastMessageString.getBytes(StandardCharsets.UTF_8);
		}
		else
			return null;
	}

	public void set_lastMessage(String message) {
		lastMessageString = message;
	}

	public void set_lastMessage(byte[] message) {
		lastMessageBinary = message;
	}
}
