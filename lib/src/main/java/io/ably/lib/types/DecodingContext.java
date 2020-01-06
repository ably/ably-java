package io.ably.lib.types;

import java.util.Map;
import java.nio.charset.StandardCharsets;

public class DecodingContext {

	private String lastMessageString;
	private byte[] lastMessageBinary;

	public DecodingContext()
	{
		lastMessageBinary = null;
		lastMessageString = null;
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
