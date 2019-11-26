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

	public byte[] getLastMessage() {
		if(lastMessageBinary != null)
			return lastMessageBinary;
		else if(lastMessageString != null) {
			return lastMessageString.getBytes(StandardCharsets.UTF_8);
		}
		else
			return null;
	}

	public void setLastMessage(String message) {
		lastMessageString = message;
		lastMessageBinary = null;
	}

	public void setLastMessage(byte[] message) {
		lastMessageBinary = message;
		lastMessageString = null;
	}
}
